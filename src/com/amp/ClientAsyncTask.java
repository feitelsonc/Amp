package com.amp;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Calendar;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

public class ClientAsyncTask extends AsyncTask<Void, Void, Void> {
	
	private static final byte CONNECT = 0x00;
    private static final byte DISCONNECT = 0x01;
    private static final byte WELCOME = 0x02;
    private static final byte FILE_REQUEST = 0x03;
    private static final byte FILE = 0x04;
    private static final byte PAUSE = 0x05;
    private static final byte PLAY = 0x06;
    private static final byte SEEK_TO = 0x07;
    private static final byte STOP_PLAYBACK = 0x08;
    private static final byte REQUEST_SEEK_TO = 0x09;
    private static final byte DELAY_REQUEST = 0x10;
    private static final byte DELAY_RESPONSE = 0x11; 
    private static final byte REQUEST_REQUEST_SEEK_TO = 0x12;

    private String server;
    private int uuid;
    private OutputStream outputStream;
    
	private AudioService musicPlayerService = null;
	private MainActivity activity;
    private Context context;
    private Uri songUri;
    private byte[] songByteArray;
    private int songByteLength;
    private boolean isTaskCancelled = false;
    private long timeAtSend;
    private long timeDelay;
    private URIManager uriManager;
    private long timeBeforeRequestSeekTo = 0;
	private long seekToDelay = 0;
    
    
    public ClientAsyncTask(Context context, AudioService musicPlayerService, String host, MainActivity activity) {
        this.context = context;
        this.musicPlayerService = musicPlayerService;
        this.server = host;
        this.activity = activity;
        this.uriManager = new URIManager(context);
    }
    
    public void cancelTask() {
        isTaskCancelled = true;
    }
    

    public static int byteArrayToInt(byte[] b) {
        return b[3] & 0xFF |
               (b[2] & 0xFF) << 8 |
               (b[1] & 0xFF) << 16 |
               (b[0] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a) {
        return new byte[] {
            (byte) ((a >> 24) & 0xFF),
            (byte) ((a >> 16) & 0xFF),   
            (byte) ((a >> 8) & 0xFF),   
            (byte) (a & 0xFF)
        };
    }
    
    @Override
    protected Void doInBackground(Void... params) {
        try {
        	
            byte[] messageType = new byte[1];
            byte[] packetType = new byte[1];
            
            Socket socket = new Socket();
            socket.bind(null);
            socket.connect(new InetSocketAddress(server, 8888));
            Log.d("client log", "connected to server");
//            activity.toastConnectedToServer();
            DataInputStream inputstream = new DataInputStream(socket.getInputStream());
            outputStream = socket.getOutputStream();
            
            messageType[0]=CONNECT;
            timeAtSend = System.currentTimeMillis();
            outputStream.write(messageType);
            Log.d("client log", "sent connect message to server");
            
            while (true) {
            	
            	if (isTaskCancelled){
            		messageType[0]=DISCONNECT;
            		outputStream.write(messageType);
//            		outputStream.write(Integer.valueOf(uuid).byteValue());
            		socket.close();
                    return null;
                }
            	
            	// Reads the first byte of the packet to determine packet type
            	inputstream.readFully(packetType, 0, 1);
            	long timeBeginningLoop = System.currentTimeMillis();
            	
            	if (packetType[0] == DELAY_RESPONSE) {
            		Log.d("client log", "received delay response message from server");
            		timeDelay = (System.currentTimeMillis()-timeDelay)/2;
            		Log.d("total delay log", "prop delay: "+ Long.valueOf(timeDelay).toString());
            	}
            	
            	else if (packetType[0] == SEEK_TO) {
            		seekToDelay = System.currentTimeMillis()-timeBeforeRequestSeekTo;
            		Log.d("server log", "received seek to packet. seekToDelay: " + Long.valueOf(seekToDelay).toString());
            		int milliseconds = 0;
            		byte[] millisecondsArray = new byte [4];
            		inputstream.readFully(millisecondsArray, 0, 4);
            		milliseconds = byteArrayToInt(millisecondsArray);
            		musicPlayerService.play();
            		long delay = System.currentTimeMillis()-timeBeginningLoop;
//            		musicPlayerService.iterativeSeekTo(milliseconds+(int)delay);
            		musicPlayerService.seekTo(milliseconds);
            		Log.d("total delay log", "received seek to, delay: "+Long.valueOf(delay).toString());
            	}
            	
            	else if (packetType[0] == REQUEST_SEEK_TO) {
                	Log.d("client log", "client requested seek to");
                	byte[] packet = new byte[5];
                	packet[0] = SEEK_TO;
                	byte[] millisecondsArray = new byte[4];
                	int milliseconds = musicPlayerService.getCurrentPosition();
                	millisecondsArray = intToByteArray(milliseconds);
                	for (int i=1; i<5; i++) {
                		packet[i] = millisecondsArray[i-1];
                	}
                	outputStream.write(packet);
                	Log.d("client log", "sent seek to packet to server");
            	}
            	
               	else if (packetType[0] == REQUEST_REQUEST_SEEK_TO) {
                	Log.d("client log", "server requested request seek to");
                	packetType[0]=REQUEST_SEEK_TO;
                	timeBeforeRequestSeekTo = System.currentTimeMillis();
                	outputStream.write(messageType);
                	continue;
            	}
            	
            	else if (packetType[0] == WELCOME){
            		Log.d("client log", "received welcome message from server");
//            		uuid = inputstream.read();
            		if (musicPlayerService.isPlaying()) {
            			sendSong();
            		}
            		else {
            			messageType[0]=FILE_REQUEST;
                		outputStream.write(messageType);
                		Log.d("client log", "sent file request message to server");
            		}
            	}
            	
            	else if (packetType[0] == FILE_REQUEST) {
            		Log.d("client log", "received file request message from server");
            		songUri = musicPlayerService.getCurrentTrackUri();
            		byte[] packet = new byte[songByteLength+8];
                	packet[0] = Integer.valueOf(FILE).byteValue();
                	
                	byte[] length = intToByteArray(songByteLength);
                	byte[] fileExtension = songUri.toString().substring(songUri.toString().length()-3).getBytes();
                	
                	for (int i=1; i<5; i++) {
                		packet[i] = length[i-1];
                	}
                	for (int i=5; i<8; i++) {
                		packet[i] = fileExtension[i-5];
                	}
                	for (int i=8; i<songByteLength+8; i++) {
                		packet[i] = songByteArray[i-8];
                	}
                	
                	outputStream.write(packet);
                	Log.d("client log", "sent file message to server");
            	}
            	
            	else if (packetType[0] == FILE) {
            		Log.d("client log", "received file message from server");
            		activity.showSpinner();
            		
            		byte[] length = new byte[4];
            		inputstream.readFully(length, 0, 4);
            		int fileLength = byteArrayToInt(length);
            		byte[] fileExtension = new byte[3];
            		inputstream.readFully(fileExtension, 0, 3);
            		
            		String filetype = new String(fileExtension);
            		
            		File file = createFile(filetype);
            		Uri uri = Uri.fromFile(file);
 
            		songByteArray = new byte[fileLength];
            		songByteLength = fileLength;
            		
            		// read file bytes from socket input stream
            		inputstream.readFully(songByteArray, 0, fileLength);
            		
            		FileOutputStream fileoutputstream = new FileOutputStream(file);

            		fileoutputstream.write(songByteArray);
            		fileoutputstream.close();
            		
            		activity.hideSpinner();
            		
            		
            		musicPlayerService.initializeSongAndPause(uri);
            		songUri = musicPlayerService.getCurrentTrackUri();
            		
            		// update activity UI
            		activity.reloadUI();
            		
            		// request playback location of file
            		messageType[0] = REQUEST_SEEK_TO;
            		outputStream.write(messageType);
            		Log.d("client log", "sent request seek position message to server");
            	}
            	
            	else if (packetType[0] == PAUSE) {
            		Log.d("client log", "received pause message from server");
            		musicPlayerService.pause();            		
            	}
            	
            	else if (packetType[0] == PLAY) {
            		Log.d("client log", "received play message from server");
//            		musicPlayerService.iterativePlay();
            		musicPlayerService.play();
            	}
            	
            	
            	else if (packetType[0] == STOP_PLAYBACK) {
            		Log.d("client log", "received stop playback message from server");
            		musicPlayerService.pause();
            	}
            	
            	else {
            		Log.d("client log", "invalid packet type received");
            	}
            	
            }
           
            
        } catch (Exception e) {
        	Log.d("client log", e.toString());
        }
		return null;
    }
    
    public void sendPause() {
    	byte[] messageType = new byte[1];
    	messageType[0] = PAUSE;
    	try {
			outputStream.write(messageType);
			Log.d("client log", "sent pause message to server");
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public void sendPlay() {
    	byte[] messageType = new byte[1];
    	messageType[0] = PLAY;
    	try {
			outputStream.write(messageType);
			Log.d("client log", "sent play message to server");
			sendSeekTo();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public void sendSeekTo() {
        	byte[] packet = new byte[5];
        	packet[0] = Integer.valueOf(SEEK_TO).byteValue();
        	byte[] millisecondsArray = new byte[4];
        	int milliseconds = musicPlayerService.getCurrentPosition();
        	millisecondsArray = intToByteArray(milliseconds);
        	for (int i=1; i<5; i++) {
        		packet[i] = millisecondsArray[i-1];
        	}
        	try {
    			outputStream.write(packet);    			
    			Log.d("client log", "sent seek to message to server");
    			sendDelayRequest();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    }
    
    public void sendDelayRequest() {
    	timeDelay = System.currentTimeMillis();
    	byte[] messageType = new byte[1];
    	messageType[0]=DELAY_REQUEST;
    	try {
			outputStream.write(messageType);
			Log.d("client log", "sent delay request message to server");
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public void sendSong() {
    	// get current track file from musicPlayerService
    	songUri = musicPlayerService.getCurrentTrackUri();
    	
    	FileInputStream songFileinputstream;
    	File songfile;
    	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
    		songfile = new File(getPath(songUri));
    	}
    	else {
    		songfile = new File(uriManager.getPath(context, songUri));
    	}
    	try {
    		songFileinputstream = new FileInputStream(songfile);
    		songByteLength = (int) songfile.length();
    		songByteArray = new byte[songByteLength];
    		songFileinputstream.read(songByteArray, 0, songByteLength);
    		songFileinputstream.close();
    	} catch (Exception e) {
    		Log.d("client log", e.toString());
    		e.printStackTrace();  
    	}
    	
    	byte[] packet = new byte[songByteLength+8];
    	packet[0] = FILE;
    	byte []length = intToByteArray(songByteLength);
    	byte[] fileExtension = (songfile.getAbsolutePath().substring(songfile.getAbsolutePath().length()-3)).getBytes();
    	for (int i=1; i<5; i++) {
    		packet[i] = length[i-1];
    	}
    	for (int i=5; i<8; i++) {
    		packet[i] = fileExtension[i-5];
    	}
    	for (int i=8; i<songByteLength+8; i++) {
    		packet[i] = songByteArray[i-8];
    	}
    	
    	try {
			outputStream.write(packet);
			Log.d("client log", "sent file message to server");
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    private final synchronized String getPath(Uri uri) {
        String res = null;
        String[] proj = { MediaStore.Audio.Media.DATA };
        Cursor cursor = context.getContentResolver().query(uri, proj, null, null, null);
        if (cursor.moveToFirst()) {
            int column_index = cursor .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }
    
    @Override
    protected void onPreExecute() {
//    	Toast.makeText(context, "Client Started", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onPostExecute(Void result) {
    	Toast.makeText(context, "Client Stopped", Toast.LENGTH_SHORT).show();
    }
    
    private File createFile(String FileType){
    	String date = java.text.DateFormat.getDateTimeInstance().format(Calendar.getInstance().getTime());

    	final File f = new File(Environment.getExternalStorageDirectory() + "/"
				+ "Amp" + "/Shared Songs/Song-" + date + "."
				+ FileType);

		File dirs = new File(f.getParent());
		if (!dirs.exists())
			dirs.mkdirs();
		try {
			f.createNewFile();
		} catch (IOException e) {
			Log.d("client log", e.toString());
			Log.d("client log", "error creating new file");
			e.printStackTrace();
		}
		return f;
    }
}