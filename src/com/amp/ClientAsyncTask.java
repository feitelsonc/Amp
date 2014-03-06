package com.amp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Calendar;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

public class ClientAsyncTask extends AsyncTask<Void, Void, Void> {
	
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

    private String server;
    private int uuid;
    OutputStream outputStream;
    
	private AudioService musicPlayerService = null;
	
    private Context context;
    private Uri songUri;
    private byte[] songByteArray;
    private int songByteLength;

    public ClientAsyncTask(Context context, AudioService musicPlayerService) {
        this.context = context;
        this.musicPlayerService = musicPlayerService;
    }
    
    public ClientAsyncTask(Context context, AudioService musicPlayerService, String host) {
        this.context = context;
        this.musicPlayerService = musicPlayerService;
        this.server = host;
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
//        	Toast.makeText(context, "Client Started", Toast.LENGTH_SHORT).show();
        	
            byte[] messageType = new byte[1];
            
            Socket socket = new Socket();
            socket.bind(null);
            socket.connect(new InetSocketAddress(server, 8888));
            Log.d("client log", "connected to server");
            InputStream inputstream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            
            messageType[0]=Integer.valueOf(CONNECT).byteValue();
            outputStream.write(messageType);
            
            while (true) {
            	
            	// Reads the first byte of the packet to determine packet type
            	int packetType = inputstream.read();
            	
            	if (packetType == WELCOME){
            		uuid = inputstream.read();
            		messageType[0]=Integer.valueOf(FILE_REQUEST).byteValue();
            		outputStream.write(messageType);
            	}
            	
            	else if (packetType == FILE_REQUEST) {
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

            	}
            	
            	else if (packetType == FILE) {

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
            		
            		// request playback location of file
            		messageType[0] = Integer.valueOf(REQUEST_SEEK_TO).byteValue();
            		outputStream.write(messageType);
            		Log.d("client log", "here9");
            	}
            	
            	else if (packetType == PAUSE) {
            		musicPlayerService.pause();            		
            	}
            	
            	else if (packetType == PLAY) {
            		musicPlayerService.play();
            	}
            	
            	else if (packetType == SEEK_TO) {
            		int milliseconds = 0;
            		byte[] millisecondsArray = new byte [4];
            		inputstream.read(millisecondsArray, 0, 4);
            		milliseconds = byteArrayToInt(millisecondsArray);
            		musicPlayerService.play();
            		musicPlayerService.seekTo(milliseconds);        		
            	}
            	
            	else if (packetType == STOP_PLAYBACK) {
            		musicPlayerService.stopPlayback();
            	}
            	
            }
           
            
        } catch (Exception e) {
        }
		return null;
    }
    
    public void sendPause() {
    	byte[] messageType = new byte[1];
    	messageType[0] = Integer.valueOf(PAUSE).byteValue();
    	try {
			outputStream.write(messageType);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public void sendPlay() {
    	byte[] messageType = new byte[1];
    	messageType[0] = Integer.valueOf(PLAY).byteValue();
    	try {
			outputStream.write(messageType);
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
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    @Override
    protected void onPreExecute() {
    	Toast.makeText(context, "Client Started", Toast.LENGTH_SHORT).show();
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
			Log.d("client log", "did not create new file");
			e.printStackTrace();
		}
		return f;
    }
    
}