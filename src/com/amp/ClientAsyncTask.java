package com.amp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;



public class ClientAsyncTask extends AsyncTask<Void, Void, String> {
	
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
	static Map<String, String> dictionary = new HashMap<String, String>(); // maps uuids to IP addresses of clients
	
    private Context context;
    private Uri songUri;
    private byte[] songByteArray;

    public ClientAsyncTask(Context context, Uri songUri) {
        this.context = context;
        this.songUri = songUri;

    

            Socket clientSocket = new Socket();
            byte[] messageType = new byte[1];
            byte[] clientUuid = new byte[1];
            
            
            
            /*FILE PACKETS HAVE THE FOLLOWING FORMAT:
        	 * 4 BYTES - LENGTH OF FILE
        	 * 6 BYTES - FILE NAME EXTENSION (.mp3,.wav, etc.)
        	 * VARIABLE BYTES - FILE ITSELF
        	 */
        	
//        	if (packetType == FILE) {
//        		byte length[] = new byte[4];
//        		inputstream.read(length,0,4);
//        		int file_length = byteArrayToInt(length);
//        		byte name[] = new byte[6];
//        		inputstream.read(name, 0, 6);
//        		String filetype = name.toString();
//        		File file = createFile(filetype);
//        		byte song_byte_array[] = new byte[file_length];
//        		inputstream.read(song_byte_array,0,file_length);
//        		FileOutputStream fileoutputstream = new FileOutputStream(file);
//        		fileoutputstream.write(song_byte_array);
//        		fileoutputstream.close();
//        		//copyFile(inputstream, new FileOutputStream(file));
//        	}
        	
//        	if (packetType == FILE_REQUEST) {
//        		
//        	}
    }
            
        	
            
    @Override
    protected void onPostExecute(String result) {
       // notify of song transfer completion
    }


	@Override
	protected String doInBackground(Void... params) {
		// TODO Auto-generated method stub
		return null;
	}
}