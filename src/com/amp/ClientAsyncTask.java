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
        	Toast.makeText(context, "Client Started", Toast.LENGTH_SHORT).show();
        	
            byte[] messageType = new byte[1];
            
            Socket socket = new Socket();
            socket.bind(null);
            socket.connect(new InetSocketAddress(server,8888));
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
            		Toast.makeText(context, "Received a WELCOME packet.", Toast.LENGTH_SHORT).show();
            	}
            	
            	else if (packetType == DISCONNECT) {
            		int uuidToRemove = inputstream.read();            		
            		socket.close();
            		
            	}
            	
            	else if (packetType == FILE_REQUEST) {
            		byte[] packet = new byte[songByteLength+1];
                	packet[0] = Integer.valueOf(FILE).byteValue();
                	
                	for (int i=1; i<songByteLength+1; i++) {
                		packet[i] = songByteArray[i-1];
                	}
                	outputStream.write(packet);

//            		Toast.makeText(musicPlayerService, "Received a WELCOME packet.", Toast.LENGTH_SHORT).show();

            	}
            	
            	else if (packetType == FILE) {

            		byte[] length = new byte[4];
            		inputstream.read(length, 0, 4);
            		int fileLength = byteArrayToInt(length);
            		byte[] fileExtention = new byte[6];
            		inputstream.read(fileExtention, 0, 6);
            		String filetype = new String(fileExtention);
            		File file = createFile(filetype);
            		Uri uri = Uri.fromFile(file);
            		songByteArray = new byte[fileLength];
            		songByteLength = fileLength;
            		inputstream.read(songByteArray, 0, fileLength);
            		FileOutputStream fileoutputstream = new FileOutputStream(file);
            		fileoutputstream.write(songByteArray);
            		fileoutputstream.close();
            		
            		musicPlayerService.initializeSongAndPause(uri);
            		songUri = musicPlayerService.getCurrectSongUri();   
            		
            		// request playback location of file
            		messageType[0] = Integer.valueOf(REQUEST_SEEK_TO).byteValue();
            		outputStream.write(messageType);
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
    
}