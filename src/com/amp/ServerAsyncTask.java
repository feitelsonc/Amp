package com.amp;

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
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

public class ServerAsyncTask extends AsyncTask<Void, Void, Void> {
	
	private static final int CONNECT = 0;
    private static final int DISCONNECT = 1;
    private static final int WELCOME = 2;
    private static final int FILE_REQUEST = 3;
    private static final int FILE = 4;
    private static final int PAUSE = 5;
    private static final int PLAY = 6;
    private static final int SEEK_TO = 7;
    private static final int STOP_PLAYBACK = 8;
    private static final int REQUEST_SEEK_TO = 9;

	private AudioService musicPlayerService = null;
	private int numClients = 0;
	static Map<String, Socket> dictionary = new HashMap<String, Socket>(); // maps uuids to sockets of clients
	
    private Context context;
    private Uri songUri;
    private byte[] songByteArray;
    private int songByteLength;
    private boolean isTaskCancelled = false;
    
    public ServerAsyncTask(Context context, AudioService musicPlayerService) {
        this.context = context;
        this.musicPlayerService = musicPlayerService;
        this.songUri = musicPlayerService.getCurrentTrackUri();
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
            
        	ServerSocket serverSocket = new ServerSocket(8888);
        	Log.d("server log", "waiting for client to connect");
        	Socket client = serverSocket.accept();

        	Log.d("server log", "client connected");
        	InputStream inputstream = client.getInputStream();
        	OutputStream outputStream = client.getOutputStream();
            
            while (true) {
            	
            	if (isTaskCancelled) {
            		broadcastStopPlayback();
            		for (int i=0; i<numClients; i++) {
            			dictionary.get(Integer.valueOf(i)).close();
            			
            		}
                    return null;
                }

            	// Reads the first byte of the packet to determine packet type
            	int packetType = inputstream.read();
            	
            	if (packetType == CONNECT) {
            		Log.d("server log", "received connect packet from client");
            		messageType[0] = Integer.valueOf(WELCOME).byteValue();
            		clientUuid[0] = Integer.valueOf(numClients).byteValue();
            		outputStream.write(messageType);
            		outputStream.write(clientUuid);
            		dictionary.put(Integer.valueOf(numClients).toString(), client);
            		
            		numClients++;
            	}
            	
            	else if (packetType == DISCONNECT) {
            		int uuidToRemove = inputstream.read();
            		dictionary.remove(Integer.valueOf(uuidToRemove).toString());
            		Log.d("server log", "received disconnect packet from client");
            	}
            	
            	else if (packetType == FILE_REQUEST) {
            		Log.d("server log", "received file requested packet from client");
            		
            		// get current track file from musicPlayerService
            		songUri = musicPlayerService.getCurrentTrackUri();
            		
            		FileInputStream songFileinputstream;
            		File songfile = new File(getPath(songUri));
                	try {
//                		Log.d("server log", "this is the File songfile.toUri() "+songfile.toURI());
//                		Log.d("server log", "this is the File songfile.exists() "+Boolean.valueOf(songfile.exists()).toString());
//                		Log.d("server log", "this is the File songfile.getName() "+songfile.getName());
//                		Log.d("server log", "this is the entire uri path "+getPath(songUri));
                		songFileinputstream = new FileInputStream(songfile);
                		songByteLength = (int) songfile.length();
//                		Log.d("server log", "this is the file's length "+Integer.valueOf((int)songfile.length()).toString());
                		songByteArray = new byte[songByteLength];
                		songFileinputstream.read(songByteArray, 0, songByteLength);
                		songFileinputstream.close();
                	} catch (Exception e) {  
                		e.printStackTrace();  
                	}
            		
                	byte[] packet = new byte[songByteLength+8];
                	packet[0] = Integer.valueOf(FILE).byteValue();
                	byte[] length = intToByteArray(songByteLength);
                	byte[] fileExtension = (songfile.getAbsolutePath().substring(songfile.getAbsolutePath().length()-3)).getBytes();
//                	
//                	Log.d("server log", "this is the entire file name"+songUri.getPath());
//                	Log.d("server log", "this is the file extension, before byte array conversion."+songUri.toString().substring(songUri.getPath().length()-3));
//                	Log.d("server log", "this is the file extension, after byte array conversion, and then conversion back to string."+new String(fileExtension));
//                	String tempExten = "mp3";
//                	byte[] fileExtension = tempExten.getBytes();
                	
                	for (int i=1; i<5; i++) {
                		packet[i] = length[i-1];
                	}

                	Log.d("server log", "this is the songbyte length"+Integer.valueOf(songByteLength));
                	
                	for (int i=5; i<8; i++) {
                		packet[i] = fileExtension[i-5];
                	}

                	for (int i=8; i<songByteLength+8; i++) {
                		packet[i] = songByteArray[i-8];
                	}
                	
                	outputStream.write(packet);
                	Log.d("server log", "packet away. weeeeeee");
            	}

            	else if (packetType == FILE) {
            		Log.d("server log", "received song from client");

            		byte[] length = new byte[4];
            		inputstream.read(length, 0, 4);
            		int fileLength = byteArrayToInt(length);
            		byte[] fileExtension = new byte[3];
            		inputstream.read(fileExtension, 0, 3);
            		String filetype = new String(fileExtension);
            		File file = createFile(filetype);
            		Uri uri = Uri.fromFile(file);
            		songByteArray = new byte[fileLength];
            		songByteLength = fileLength;
            		inputstream.read(songByteArray, 0, fileLength);
            		FileOutputStream fileoutputstream = new FileOutputStream(file);
            		fileoutputstream.write(songByteArray);
            		fileoutputstream.close();
            		
            		musicPlayerService.initializeSongAndPause(uri);
            		songUri = musicPlayerService.getCurrentTrackUri();
            		
            		broadcastStopPlayback();
            		broadcastSong();   
            		
            		// request playback location of file
            		messageType[0] = Integer.valueOf(REQUEST_SEEK_TO).byteValue();
            		outputStream.write(messageType);
            	}
            	
            	else if (packetType == PAUSE) {
            		Log.d("server log", "client paused");
            		musicPlayerService.pause();
            		
            		broadcastPause();
            	}
            	
            	else if (packetType == PLAY) {
            		Log.d("server log", "client played");
            		musicPlayerService.play();
            		
            		broadcastPlay();
            	}
            	
            	else if (packetType == SEEK_TO) {
            		Log.d("server log", "client changes seek pos");
            		int milliseconds = 0;
            		byte[] millisecondsArray = new byte [4];
            		inputstream.read(millisecondsArray, 0, 4);
            		milliseconds = byteArrayToInt(millisecondsArray);
            		musicPlayerService.play();
            		musicPlayerService.seekTo(milliseconds);
            		
            		broadcastPlay();
            		broadcastSeekTo();
            	}
            	
            	else if (packetType == STOP_PLAYBACK) {
            		Log.d("server log", "client stopped playback");
            		musicPlayerService.stopPlayback();
            	}

        }
        } catch (Exception e) {
        }
		return null;
    }
    
    public void broadcastPause() {
    	Log.d("server log", "broadcasted pause");
    	byte[] messageType = new byte[1];
    	messageType[0] = Integer.valueOf(PAUSE).byteValue();
    	sendToClients(messageType);
    }
    
    public void broadcastPlay() {
    	Log.d("server log", "broadcasted play");
    	byte[] messageType = new byte[1];
    	messageType[0] = Integer.valueOf(PLAY).byteValue();
    	sendToClients(messageType);
    }
    
    public void broadcastSeekTo() {
    	Log.d("server log", "broadcasted seek to");
    	byte[] packet = new byte[5];
    	packet[0] = Integer.valueOf(SEEK_TO).byteValue();
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
    	messageType[0] = Integer.valueOf(STOP_PLAYBACK).byteValue();
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
    	packet[0] = Integer.valueOf(FILE).byteValue();
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
    
}