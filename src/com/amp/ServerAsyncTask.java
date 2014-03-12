package com.amp;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
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
    private ServerSocket serverSocket;
    private ClientAccepter clientAcceptor = null;
    
    public ServerAsyncTask(Context context, AudioService musicPlayerService, MainActivity activity) {
        this.context = context;
        this.musicPlayerService = musicPlayerService;
        this.songUri = musicPlayerService.getCurrentTrackUri();
        this.activity = activity;
    }
    
    public void cancelTask() {
        isTaskCancelled = true;
        clientAcceptor.stopAccepter();
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
//          byte[] clientUuid = new byte[1];
            
        	serverSocket = new ServerSocket(8888);
        	
        	clientAcceptor = new ClientAccepter();
        	clientAcceptor.start();
        	while (true) {
        		 
        	DataInputStream inputstream;
        	OutputStream outputStream;
        	for (int i=0; i<numClients; i++) {
        		Socket client = dictionary.get(Integer.valueOf(i).toString());
        		inputstream = new DataInputStream(client.getInputStream());
	        	if (inputstream.available() == 0) {
	        		continue;
	        		
	        	}
	        	else {
	        		outputStream = client.getOutputStream();
	        	}
	        	
            	if (isTaskCancelled) {
            		broadcastStopPlayback(-1);
            		for (int i2=0; i2<numClients; i2++) {
            			dictionary.get(Integer.valueOf(i2)).close();          			
            		}
                    return null;
                }

            	// Reads the first byte of the packet to determine packet type
            	byte[] packetType = new byte[1];
            	inputstream.readFully(packetType,0,1);
            	
            	if (packetType[0] == CONNECT) {
            		Log.d("server log", "received connect packet from client");
            		messageType[0] = WELCOME;
//            	    clientUuid[0] = Integer.valueOf(numClients).byteValue();
            		outputStream.write(messageType);
//            		outputStream.write(clientUuid);
            	}
            	
            	else if (packetType[0] == DISCONNECT) {
//            		activity.toastClientDisconnected();
//            		int uuidToRemove = inputstream.readFully();
//            		dictionary.remove(Integer.valueOf(uuidToRemove).toString());
            		Log.d("server log", "received disconnect packet from client");
            	}
            	
            	else if (packetType[0] == FILE_REQUEST) {
            		Log.d("server log", "received file requested packet from client");
            		
            		// get current track file from musicPlayerService
            		songUri = musicPlayerService.getCurrentTrackUri();
            		
            		FileInputStream songFileinputstream;
            		File songfile;
            		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            			songfile = new File(getPath(songUri));
            		}
            		else {
            			songfile = new File(getPath(context, songUri));
            		}
                	try {
                		songFileinputstream = new FileInputStream(songfile);
                		songByteLength = (int) songfile.length();
                		songByteArray = new byte[songByteLength];
                		songFileinputstream.read(songByteArray, 0, songByteLength);
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
            		activity.reloadUI();
            		
            		broadcastStopPlayback(-1);
            		broadcastSong(i);   
            		
            		// request playback location of file
            		messageType[0] = REQUEST_SEEK_TO;
            		outputStream.write(messageType);
            	}
            	
            	else if (packetType[0] == PAUSE) {
            		Log.d("server log", "client paused");
            		musicPlayerService.pause();
            		
            		broadcastPause(i);
            	}
            	
            	else if (packetType[0] == PLAY) {
            		Log.d("server log", "client played");
            		musicPlayerService.play();
            		
            		broadcastPlay(i);
            		broadcastSeekTo(i);
            	}
            	
            	else if (packetType[0] == REQUEST_SEEK_TO) {
                	Log.d("server log", "client requested seek to");
                	byte[] packet = new byte[5];
                	packet[0] = SEEK_TO;
                	byte[] millisecondsArray = new byte[4];
                	int milliseconds = musicPlayerService.getCurrentPosition();
                	millisecondsArray = intToByteArray(milliseconds);
                	for (int i3=1; i3<5; i3++) {
                		packet[i3] = millisecondsArray[i3-1];
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
            		
            		broadcastPlay(i);
            		broadcastSeekTo(i);
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
        }} catch (Exception e) {
        	Log.d("server log", e.toString());
        }
		return null;
    }
    
    public void broadcastPause(int clientOriginator) {
    	byte[] messageType = new byte[1];
    	messageType[0] = PAUSE;
    	sendToClients(messageType, clientOriginator);
    	Log.d("server log", "broadcasted pause");
    }
    
    public void broadcastPlay(int clientOriginator) {
    	byte[] messageType = new byte[1];
    	messageType[0] = PLAY;
    	sendToClients(messageType, clientOriginator);
    	Log.d("server log", "broadcasted play");
    }
    
    public void broadcastSeekTo(int clientOriginator) {
    	byte[] packet = new byte[5];
    	packet[0] = SEEK_TO;
    	byte[] millisecondsArray = new byte[4];
    	int milliseconds = musicPlayerService.getCurrentPosition();
    	millisecondsArray = intToByteArray(milliseconds);
    	for (int i=1; i<5; i++) {
    		packet[i] = millisecondsArray[i-1];
    	}
    	sendToClients(packet, clientOriginator);
    	Log.d("server log", "broadcasted seek to");
    }
    
    public void broadcastStopPlayback(int clientOriginator) {
    	byte[] messageType = new byte[1];
    	messageType[0] = STOP_PLAYBACK;
    	sendToClients(messageType, clientOriginator);
    	Log.d("server log", "broadcasted stop");
    }
    
    public void broadcastSong(int clientOriginator) {
    	// get current track file from musicPlayerService
		songUri = musicPlayerService.getCurrentTrackUri();
		
		FileInputStream songFileinputstream;
		File songfile;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			songfile = new File(getPath(songUri));
		}
		else {
			songfile = new File(getPath(context, songUri));
		}
    	try {
    		songFileinputstream = new FileInputStream(songfile);
    		songByteLength = (int) songfile.length();
    		songByteArray = new byte[songByteLength];
    		songFileinputstream.read(songByteArray, 0, songByteLength);
    		songFileinputstream.close();
    	} catch (Exception e) {
    		Log.d("server log", e.toString());
    		e.printStackTrace();  
    	}
		
    	byte[] packet = new byte[songByteLength+8];
    	packet[0] = FILE;
    	byte[] length = intToByteArray(songByteLength);
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
    	sendToClients(packet, clientOriginator);
    	Log.d("server log", "broadcasted song");
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
    
    private void sendToClients(byte[] packet, int clientOriginator) {
    	OutputStream outputStream;
    	for (int i=0; i<numClients; i++) {
    		if (i == clientOriginator) {
    			continue;
    		}
    		try {
    			outputStream = dictionary.get(Integer.valueOf(i).toString()).getOutputStream();
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
	    		
	    		try {
					Log.d("server log", "waiting for clients to connect");
					Socket client = serverSocket.accept();
					Log.d("server log", "client connected");
					dictionary.put(Integer.valueOf(numClients).toString(), client);

					numClients++;
				} catch (Exception e) {
					Log.d("server log","This is an error of type: "+e.toString());
				}
     		}
    	}
    	
    	public void stopAcceptor() {
    		canceled = true;
    	}
	}
    
    // The following URI methods are from http://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    @SuppressLint("NewApi")
	public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
            String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    
}