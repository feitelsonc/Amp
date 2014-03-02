package com.amp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.format.Time;

public class ServerAsyncTask extends AsyncTask<Void, Void, String> {
	
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
	static Map<String, InetAddress> dictionary = new HashMap<String, InetAddress>(); // maps uuids to IP addresses of clients
	static Map<String, Integer> dictionaryPorts = new HashMap<String, Integer>(); // maps uuids to ports of clients
	
    private Context context;
    private Uri songUri;
    private byte[] songByteArray;

    public ServerAsyncTask(Context context, Uri songUri) {
        this.context = context;
        this.songUri = songUri;
    }
    
    public static int byteArrayToInt(byte[] b) 
    {
        return   b[3] & 0xFF |
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
    protected String doInBackground(Void... params) {
        try {
        	
        	FileInputStream fileinputstream;
        	try {
        		File songfile = new File(songUri.getPath());
        		fileinputstream = new FileInputStream(songfile);
        		int fileLength = (int) songfile.length();
        		songByteArray = new byte[fileLength];
        		fileinputstream.read(songByteArray, 0, fileLength);
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
            		
            		dictionary.put(Integer.valueOf(numClients).toString(), client.getInetAddress());
            		dictionaryPorts.put(Integer.valueOf(numClients).toString(), Integer.valueOf(client.getPort()));
            		
            		numClients++;
            	}
            	
            	else if (packetType == DISCONNECT) {
            		int uuidToRemove = inputstream.read();
            		dictionary.remove(Integer.valueOf(uuidToRemove).toString());
            		dictionaryPorts.remove(Integer.valueOf(numClients).toString());
            	}
            	
            	else if (packetType == FILE_REQUEST) {
            		// send file to client
            	}
            	
            	else if (packetType == FILE) {
            		if (packetType == FILE) {
                		byte length[] = new byte[4];
                		inputstream.read(length,0,4);
                		int file_length = byteArrayToInt(length);
                		byte name[] = new byte[6];
                		inputstream.read(name, 0, 6);
                		String filetype = name.toString();
                		File file = createFile(filetype);
                		byte song_byte_array[] = new byte[file_length];
                		inputstream.read(song_byte_array,0,file_length);
                		FileOutputStream fileoutputstream = new FileOutputStream(file);
                		fileoutputstream.write(song_byte_array);
                		fileoutputstream.close();
                		
                		// request playback location of file
                		messageType[0] = Integer.valueOf(REQUEST_SEEK_TO).byteValue();
                		outputStream.write(messageType);
                	}
            	}
            	
            	else if (packetType == PAUSE) {
            		musicPlayerService.pause();
            	}
            	
            	else if (packetType == PLAY) {
            		musicPlayerService.play();
            	}
            	
            	else if (packetType == SEEK_TO) {
            		int milliseconds = 0;
            		byte millisecondsArray[] = new byte [4];
            		inputstream.read(millisecondsArray, 0, 4);
            		milliseconds = byteArrayToInt(millisecondsArray);
            		musicPlayerService.seekTo(milliseconds);
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
    
    
    private File createFile(String FileType){
    	Time time = new Time();
    	time.setToNow();
    	final File f = new File(Environment.getExternalStorageDirectory() + "/"
				+ context.getPackageName() + "/sharedsongs/song-" + time.toString() + System.currentTimeMillis()
				+ FileType);

		File dirs = new File(f.getParent());
		if (!dirs.exists())
			dirs.mkdirs();
		try {
			f.createNewFile();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return f;
    }
    
    
    @Override
    protected void onPostExecute(String result) {
    	
    }
    
    private void sendToClients(byte[] packet) {
    	ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(8888);
	    	for (int i=0; i<numClients; i++) {
	    	}
		}
    	catch (IOException e) {
			e.printStackTrace();
		}
    }
}