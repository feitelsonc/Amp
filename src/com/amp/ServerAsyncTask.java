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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
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

    public ServerAsyncTask(Context context, AudioService musicPlayerService) {
        this.context = context;
        this.musicPlayerService = musicPlayerService;
        this.songUri = musicPlayerService.getCurrectSongUri();
    }
    
    public static int byteArrayToInt(byte[] b) 
    {
        return b[3] & 0xFF |
               (b[2] & 0xFF) << 8 |
               (b[1] & 0xFF) << 16 |
               (b[0] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a)
    {
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
        	Toast.makeText(context, "Server Started", Toast.LENGTH_SHORT).show();
        	FileInputStream fileinputstream;
        	try {
        		File songfile = new File(songUri.getPath());
        		fileinputstream = new FileInputStream(songfile);
        		int songByteLength = (int) songfile.length();
        		songByteArray = new byte[songByteLength];
        		fileinputstream.read(songByteArray, 0, songByteLength);
        		/*for (int i=0; i<songfile.length(); i++) {
        			songByteArray[i] = (byte) inputStream.read();
        		}*/
        		fileinputstream.close();
        		} catch (Exception e) {  
        		e.printStackTrace();  
        	}
        	
            byte[] messageType = new byte[1];
            byte[] clientUuid = new byte[1];
            
            while (true) {
            	ServerSocket serverSocket = new ServerSocket(8888);
            	Socket client = serverSocket.accept();
            	InputStream inputstream = client.getInputStream();
            	OutputStream outputStream = client.getOutputStream();
            	
            	//Reads the first byte off the packet, this should always be packettype.
            	int packetType = inputstream.read();
            	
            	if (packetType == CONNECT) {
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
            	}
            	
            	else if (packetType == FILE_REQUEST) {
            		byte[] packet = new byte[songByteLength+1];
                	packet[0] = Integer.valueOf(FILE).byteValue();
                	
                	for (int i=1; i<songByteLength+1; i++) {
                		packet[i] = songByteArray[i-1];
                	}
                	outputStream.write(packet);
            	}
            	
            	else if (packetType == FILE) {

            		byte length[] = new byte[4];
            		inputstream.read(length,0,4);
            		int file_length = byteArrayToInt(length);
            		byte name[] = new byte[6];
            		inputstream.read(name, 0, 6);
            		String filetype = name.toString();
            		File file = createFile(filetype);
            		Uri uri = Uri.fromFile(file);
            		songByteArray = new byte[file_length];
            		inputstream.read(songByteArray,0,file_length);
            		FileOutputStream fileoutputstream = new FileOutputStream(file);
            		fileoutputstream.write(songByteArray);
            		fileoutputstream.close();
            		
            		musicPlayerService.initializeSongAndPause(uri);
            		
            		broadcastStopPlayback();
            		broadcastSong();         		
            		// request playback location of file
            		messageType[0] = Integer.valueOf(REQUEST_SEEK_TO).byteValue();
            		outputStream.write(messageType);
            	}
            	
            	else if (packetType == PAUSE) {
            		musicPlayerService.pause();
            		
            		broadcastPause();
            	}
            	
            	else if (packetType == PLAY) {
            		musicPlayerService.play();
            		
            		broadcastPlay();
            	}
            	
            	else if (packetType == SEEK_TO) {
            		int milliseconds = 0;
            		byte millisecondsArray[] = new byte [4];
            		inputstream.read(millisecondsArray, 0, 4);
            		milliseconds = byteArrayToInt(millisecondsArray);
            		musicPlayerService.play();
            		musicPlayerService.seekTo(milliseconds);
            		
            		broadcastPlay();
            		broadcastSeekTo();
            	}
            	
            	else if (packetType == STOP_PLAYBACK) {
            		musicPlayerService.stopPlayback();
            	}
            	
            	serverSocket.close();
            }
            
        } catch (Exception e) {
        }
		return null;
    }
    
    public void broadcastPause() {
    	byte[] messageType = new byte[1];
    	messageType[0] = Integer.valueOf(PAUSE).byteValue();
    	sendToClients(messageType);
    }
    
    public void broadcastPlay() {
    	byte[] messageType = new byte[1];
    	messageType[0] = Integer.valueOf(PLAY).byteValue();
    	sendToClients(messageType);
    }
    
    public void broadcastSeekTo() {
    	byte[] packet = new byte[5];
    	packet[0] = Integer.valueOf(SEEK_TO).byteValue();
    	int milliseconds = musicPlayerService.getCurrentPosition();
    	byte[] millisecondsArray = new byte[4];
    	millisecondsArray = intToByteArray(milliseconds);
    	for (int i=1; i<5; i++) {
    		packet[i] = millisecondsArray[i-1];
    	}
    	sendToClients(packet);
    }
    
    public void broadcastStopPlayback() {
    	byte[] messageType = new byte[1];
    	messageType[0] = Integer.valueOf(STOP_PLAYBACK).byteValue();
    	sendToClients(messageType);
    }
    
    public void broadcastSong() {
    	byte[] packet = new byte[songByteLength+1];
    	packet[0] = Integer.valueOf(FILE).byteValue();
    	
    	for (int i=1; i<songByteLength+1; i++) {
    		packet[i] = songByteArray[i-1];
    	}
    	
    	sendToClients(packet);
    }
    
    private File createFile(String FileType){
    	String date = java.text.DateFormat.getDateTimeInstance().format(Calendar.getInstance().getTime());

    	final File f = new File(Environment.getExternalStorageDirectory() + "/"
				+ context.getPackageName() + "/Shared Songs/Song-" + date + "."
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
    
}