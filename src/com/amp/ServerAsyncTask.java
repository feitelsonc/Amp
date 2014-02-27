package com.amp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;



public class ServerAsyncTask extends AsyncTask<Void, Void, String> {

    private Context context;
    private Uri songUri;
    private byte[] songByteArray;

    public ServerAsyncTask(Context context, Uri songUri) {
        this.context = context;
        this.songUri = songUri;
    }

    @Override
    protected String doInBackground(Void... params) {
        try {
        	
        	FileInputStream inputStream;
        	try {
        		File songfile = new File(songUri.getPath());
        		inputStream = new FileInputStream(songfile);
        		int fileLength = (int) songfile.length();
        		songByteArray = new byte[fileLength];  
        		for (int i=0; i<songfile.length(); i++) {
        			songByteArray[i] = (byte) inputStream.read();
        		}
        		inputStream.close();
        		} catch (Exception e) {  
        		e.printStackTrace();  
        	}

            /**
             * Create a server socket and wait for client connections. This
             * call blocks until a connection is accepted from a client
             */
            ServerSocket serverSocket = new ServerSocket(8888);
            
            while (true) {
            	Socket client = serverSocket.accept();

                /**
                 * If this code is reached, a client has connected and transferred data
                 * Save the input stream from the client as a JPEG file
                 */
            	OutputStream outputStream = client.getOutputStream();
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".mp3");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                InputStream inputstream = client.getInputStream();
//                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            }
        } catch (Exception e) {
        }
		return null;
    }
    
    @Override
    protected void onPostExecute(String result) {
       // notify of song transfer completion
    }
}