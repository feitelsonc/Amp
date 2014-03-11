package com.amp;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

public class ServerAsyncTask extends AsyncTask<Void, Void, Void> {
	
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

	private AudioService musicPlayerService = null;
	private MainActivity activity;
	private int numClients = 0;
	static Map<String, Socket> dictionary = new HashMap<String, Socket>(); // maps uuids to sockets of clients
	
    private Context context;
    private Uri songUri;
    private byte[] songByteArray;
    private int songByteLength;
    private boolean isTaskCancelled = false;
    private Handler handler = new Handler();
    private ServerSocket serverSocket;
    
    public ServerAsyncTask(Context context, AudioService musicPlayerService, MainActivity activity) {
        this.context = context;
        this.musicPlayerService = musicPlayerService;
        this.songUri = musicPlayerService.getCurrentTrackUri();
        this.activity = activity;
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
            byte[] clientUuid = new byte[1];
            
        	serverSocket = new ServerSocket(8888);
        	Log.d("server log", "waiting for client to connect");
        	Socket client = serverSocket.accept();
//        	activity.toastClientConnected();

        	Log.d("server log", "client connected");
        	DataInputStream inputstream = new DataInputStream(client.getInputStream());
        	OutputStream outputStream = client.getOutputStream();
            
            while (true) {
            	
            	if (isTaskCancelled) {
            		Log.d("server log", "isTaskCancelled is true");
            		broadcastStopPlayback();
            		for (int i=0; i<numClients; i++) {
            			dictionary.get(Integer.valueOf(i)).close();
            			
            		}
                    return null;
                }

            	// Reads the first byte of the packet to determine packet type
            	byte[] packetType = new byte[1];
            	inputstream.readFully(packetType,0,1);
            	
            	if (packetType[0] == CONNECT) {
            		Log.d("server log", "received connect packet from client");
            		messageType[0] = WELCOME;
            	//  clientUuid[0] = Integer.valueOf(numClients).byteValue();
            		outputStream.write(messageType);
            //		outputStream.write(clientUuid);
            		dictionary.put(Integer.valueOf(numClients).toString(), client);
            		
            		numClients++;
            	}
            	
            	else if (packetType[0] == DISCONNECT) {
//            		activity.toastClientDisconnected();
            		/*int uuidToRemove = inputstream.readFully();
            		dictionary.remove(Integer.valueOf(uuidToRemove).toString());*/
            		Log.d("server log", "received disconnect packet from client");
            	}
            	
            	else if (packetType[0] == FILE_REQUEST) {
            		Log.d("server log", "received file requested packet from client");
            		
            		// get current track file from musicPlayerService
            		songUri = musicPlayerService.getCurrentTrackUri();
            		
            		FileInputStream songFileinputstream;
            		File songfile = new File(getPath(songUri));
            		Log.d("server log", "created file object");
                	try {
                		songFileinputstream = new FileInputStream(songfile);
                		songByteLength = (int) songfile.length();
                		songByteArray = new byte[songByteLength];
                		songFileinputstream.read(songByteArray, 0, songByteLength);
                		Log.d("server log", "copied file bytes to byte array");
                		songFileinputstream.close();
                	} catch (Exception e) {
                		Log.d("server log", e.toString());
                		e.printStackTrace();  
                	}

                	byte type_packet = FILE;
                	outputStream.write(type_packet);
                	byte[] length = intToByteArray(songByteLength);
                	byte[] fileExtension = (songfile.getAbsolutePath().substring(songfile.getAbsolutePath().length()-3)).getBytes();

                	outputStream.write(length,0,length.length);
                	
                	outputStream.write(fileExtension,0,fileExtension.length);
                	

                	outputStream.write(length,0,length.length);
                	outputStream.write(fileExtension,0,fileExtension.length);
                	outputStream.write(songByteArray,0,songByteArray.length);
                	Log.d("server log", "sent file packet to client");
                	
            	}

            	else if (packetType[0] == FILE) {
            		Log.d("server log", "received song from client");

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
            		inputstream.readFully(songByteArray, 0, fileLength);
            		FileOutputStream fileoutputstream = new FileOutputStream(file);
            		fileoutputstream.write(songByteArray);
            		fileoutputstream.close();
            		
            		musicPlayerService.initializeSongAndPause(uri);
            		songUri = musicPlayerService.getCurrentTrackUri();
            		// update activity UI
            		activity.setupWidgets(songUri.toString());
            		
            		broadcastStopPlayback();
            		broadcastSong();   
            		
            		// request playback location of file
            		messageType[0] = REQUEST_SEEK_TO;
            		outputStream.write(messageType);
            	}
            	
            	else if (packetType[0] == PAUSE) {
            		Log.d("server log", "client paused");
            		musicPlayerService.pause();
            		
            		broadcastPause();
            	}
            	
            	else if (packetType[0] == PLAY) {
            		Log.d("server log", "client played");
            		musicPlayerService.play();
            		
            		broadcastPlay();
            	}
            	
            	else if (packetType[0] == REQUEST_SEEK_TO) {
                	Log.d("server log", "client requested seek to");
                	byte[] packet = new byte[5];
                	packet[0] = SEEK_TO;
                	byte[] millisecondsArray = new byte[4];
                	int milliseconds = musicPlayerService.getCurrentPosition();
                	millisecondsArray = intToByteArray(milliseconds);
                	for (int i=1; i<5; i++) {
                		packet[i] = millisecondsArray[i-1];
                	}
                	outputStream.write(packet);
                	Log.d("server log", "sent seek to packet to client");
                	
            	}
            	
            	else if (packetType[0] == SEEK_TO) {
            		Log.d("server log", "client changes seek pos");
            		int milliseconds = 0;
            		byte[] millisecondsArray = new byte [4];
            		inputstream.readFully(millisecondsArray, 0, 4);
            		milliseconds = byteArrayToInt(millisecondsArray);
            		musicPlayerService.play();
            		musicPlayerService.seekTo(milliseconds);
            		
            		broadcastPlay();
            		broadcastSeekTo();
            	}
            	
            	else if (packetType[0] == STOP_PLAYBACK) {
            		Log.d("server log", "client stopped playback");
            		musicPlayerService.stopPlayback();
            	}
            	
            	else
            	{
            		Log.d("server log", "invalid packet type");
            	}

        }
        } catch (Exception e) {
        	Log.d("server log", e.toString());
        }
		return null;
    }
    
    public void broadcastPause() {
    	Log.d("server log", "broadcasted pause");
    	byte[] messageType = new byte[1];
    	messageType[0] = PAUSE;
    	sendToClients(messageType);
    }
    
    public void broadcastPlay() {
    	Log.d("server log", "broadcasted play");
    	byte[] messageType = new byte[1];
    	messageType[0] = PLAY;
    	sendToClients(messageType);
    }
    
    public void broadcastSeekTo() {
    	Log.d("server log", "broadcasted seek to");
    	byte[] packet = new byte[5];
    	packet[0] = SEEK_TO;
    	byte[] millisecondsArray = new byte[4];
    	int milliseconds = musicPlayerService.getCurrentPosition();
    	millisecondsArray = intToByteArray(milliseconds);
    	for (int i=1; i<5; i++) {
    		packet[i] = millisecondsArray[i-1];
    	}
    	sendToClients(packet);
    }
    
    public void broadcastStopPlayback() {
    	Log.d("server log", "broadcasted stop");
    	byte[] messageType = new byte[1];
    	messageType[0] = STOP_PLAYBACK;
    	sendToClients(messageType);
    }
    
    public void broadcastSong() {
    	Log.d("server log", "broadcasted song");
    	
    	// get current track file from musicPlayerService
		songUri = musicPlayerService.getCurrentTrackUri();
		
		FileInputStream songFileinputstream;
    	try {
    		File songfile = new File(getPath(songUri));
    		songFileinputstream = new FileInputStream(songfile);
    		songByteLength = (int) songfile.length();
    		songByteArray = new byte[songByteLength];
    		songFileinputstream.read(songByteArray, 0, songByteLength);
    		songFileinputstream.close();
    	} catch (Exception e) {  
    		e.printStackTrace();  
    	}
		
    	byte[] packet = new byte[songByteLength+8];
    	packet[0] = FILE;
    	byte[] length = intToByteArray(songByteLength);
    	String tempExten = "mp3";
    	byte[] fileExtension = tempExten.getBytes();
    	
    	for (int i=1; i<5; i++) {
    		packet[i] = length[i-1];
    	}
    	for (int i=5; i<8; i++) {
    		packet[i] = fileExtension[i-5];
    	}
    	for (int i=8; i<songByteLength+8; i++) {
    		packet[i] = songByteArray[i-8];
    	}
    	sendToClients(packet);

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
			e.printStackTrace();
		}
		return f;
    }
    
    @Override
    protected void onPreExecute() {
    	Toast.makeText(context, "Server Started", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onPostExecute(Void result) {
    	Toast.makeText(context, "Server Stopped", Toast.LENGTH_SHORT).show();
    }
    
    private void sendToClients(byte[] packet) {
    	OutputStream outputStream;
    	
		for (int i=0; i<numClients; i++) {
			try {
				outputStream = dictionary.get(Integer.valueOf(i)).getOutputStream();
				outputStream.write(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
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
    
    private class ClientAccepter extends Thread {
    	ClientAcceptRunnable runnable = null;
    	
    	public ClientAccepter() {
    		this(new ClientAcceptRunnable());
     	}
    	
    	private ClientAccepter(ClientAcceptRunnable runnable) {
    		super(runnable, "client_acceptor");
    		this.runnable = runnable;
    	}
    	
    	public void stopAccepter() {
    		runnable.stopAcceptor();
    	}
    }
    
    private class ClientAcceptRunnable implements Runnable {
	   	private final int TICKER_TIME = 250;
    	
    	private boolean canceled = false; 
 
    	@Override
    	public void run() {
     		
     		while(!canceled) {
	    		try {
	    			Thread.sleep(TICKER_TIME);
	    		} catch (Exception e) {
	    			return;
	    		}
	
	    		handler.post(new Runnable() {
	    			
	    			@Override
	    			public void run() {
	    				try {
	    					Socket client = serverSocket.accept();
//	    					activity.toastClientConnected();
		    				InputStream inputstream = client.getInputStream();
		    	        	OutputStream outputStream = client.getOutputStream();
		    	        	
//		    	        	int packetType = inputstream.read();
//		                	
//		                	if (packetType == CONNECT) {
//		                		Log.d("server log", "received connect packet from client");
//		                		messageType[0] = Integer.valueOf(WELCOME).byteValue();
//		                		clientUuid[0] = Integer.valueOf(numClients).byteValue();
//		                		outputStream.write(messageType);
//		                		outputStream.write(clientUuid);
//		                		dictionary.put(Integer.valueOf(numClients).toString(), client);
//		                		
//		                		numClients++;
//		                	}
	    				} catch (Exception e) {
	    					
	    				}
	    				
	    			}
	    		});
     		}
    	}
    	
    	public void stopAcceptor() {
    		canceled = true;
    	}
	}
    
}