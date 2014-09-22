package com.amp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import com.amp.AudioService.LocalBinder;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class AudioService extends Service {

	// private MediaPlayer player = new MediaPlayer();
	private final IBinder binder = new LocalBinder();
	static private boolean serviceStarted = false;
	static private boolean playbackStopped = false;
	private Uri currentSongUri;
	private ServerAsyncTask server = null;
    private ClientAsyncTask client = null;
	private static final String TAG = "AudioService";
    private URIManager uriManager;
    
    
    private long nanoToMilli(long nanos) {
    	return nanos/1000000;
    }

	static public boolean isServiceStarted() {
		return serviceStarted;
	}

	public IBinder onBind(Intent arg0) {
		serviceStarted = true;
		this.uriManager = new URIManager();
		return binder;
	}	

	public class LocalBinder extends Binder {
		public AudioService getService() {
			// Return this instance of LocalService so clients can call public methods
			return AudioService.this;
		}
	}

	public int decodeMP3Header(Uri songUri)
	{
		//http://www.mp3-tech.org/programmer/frame_header.html
		
		Bitstream fileInfoBitStream;
		File file;
		Header infoFrameHeader;
		int sampleRate,sampleSizeInBytes, numChannels;
		
		try {
			file = new File(uriManager.getPath(getApplicationContext(), songUri));
			fileInfoBitStream = new Bitstream(new FileInputStream(file));
			infoFrameHeader = fileInfoBitStream.readFrame();
			//infoFrameHeader.version: 0 - MPEG Version 2.5 ; 2 - MPEG Version 2.0 ; 3 - MPEG Version 1
			Log.d("newdebug",Integer.toString(infoFrameHeader.sample_frequency()));
			sampleRate = sampleFrequencyIndex(infoFrameHeader.sample_frequency(),infoFrameHeader.version());
			//Might need to change this in future. Currently assuming everything is of
			//16-bit depth.
			sampleSizeInBytes = 2;
			if(infoFrameHeader.mode()==3)
			{
				numChannels=1;
			}
			else
			{
				numChannels=2;
			}
			Log.d("newdebug",Integer.toString(sampleRate));
			
			
			
			int sizeModifier = sampleRate*sampleSizeInBytes*numChannels;
			fileInfoBitStream.close();
			return sizeModifier;
		}
		catch (Exception e)
		{
			Log.d("newdebug","we in dis weird place");
			return 0;
		}
	}
	
	public int sampleFrequencyIndex(int index, int mpegversionindex)
	{
		//infoFrameHeader.version: 0 - MPEG Version 2.5 ; 2 - MPEG Version 2.0 ; 3 - MPEG Version 1
		//http://www.mp3-tech.org/programmer/frame_header.html
		switch (mpegversionindex)
		{
			case 0:
				//infoFrameHeader.version: 0 - MPEG Version 2.5 
				switch(index)
				{
					case 0:
						return 11025;
					case 1:
						return 12000;
					case 2:
						return 8000;
				}
				break;
			case 2:
				//infoFrameHeader.version: 2 - MPEG Version 2.0 
				switch(index)
				{
					case 0:
						return 22050;
					case 1:
						return 24000;
					case 2:
						return 16000;
				}
				break;
			case 3:
				//infoFrameHeader.version: 3 - MPEG Version 1
				switch(index)
				{
					case 0:
						return 44100;
					case 1:
						return 48000;
					case 2:
						return 32000;
				}
				break;
		}
		return 0;
	}
	public byte[] decode(Uri songUri, int startMs, int maxMs) 
	  throws IOException {
		//MP3 header is 4 bytes.
		int fileHeaderSizeModifier=decodeMP3Header(songUri);
		if(fileHeaderSizeModifier>0)
		{
			int size = ((maxMs-startMs)/1000)*fileHeaderSizeModifier;
			ByteArrayOutputStream outStream = new ByteArrayOutputStream(size);
			File file = new File(uriManager.getPath(getApplicationContext(), songUri));
			InputStream inputStream = new BufferedInputStream(new FileInputStream(file),size);
			try {	

			   
				float totalMs = 0;
				boolean seeking = true;
				
			    Bitstream bitstream = new Bitstream(inputStream);
			    Decoder decoder = new Decoder();
			     
			    boolean done = false;
			    while (! done) {
			      Header frameHeader = bitstream.readFrame();
			      if (frameHeader == null) {
			        done = true;
			      } else {
			        totalMs += frameHeader.ms_per_frame();
			 
			        if (totalMs >= startMs) {
			          seeking = false;
			        }
			         
			        if (! seeking) {
			          SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
			           
	//		          if (output.getSampleFrequency() != 44100
	//		              || output.getChannelCount() != 2) {
	//		            throw new Exception("mono or non-44100 MP3 not supported");
	//		          }
			           
			          short[] pcm = output.getBuffer();
			          for (short s : pcm) {
			            outStream.write(s & 0xff);
			            outStream.write((s >> 8 ) & 0xff);
			          }
			        }
			         
			        if (totalMs >= (startMs + maxMs)) {
			          done = true;
			        }
			      }
			      bitstream.closeFrame();
			    }
			     
			    return outStream.toByteArray();
			}
			catch (Exception e) {
			    throw new IOException("Error:" + e);
			} 			
			finally {
			    inputStream.close();
			}	
		}
		return null;
	}
	
	public void initializeSong(Uri songUri) {
		currentSongUri = songUri;

		AudioTrack audioTrack = new AudioTrack (3,44100, 12, 2, 100000,0);
    	try {
    		//Must fix this!
    		byte[] songByteArray = decode(songUri,0,100000);
    		Log.d("new debugger","Length of songByteArray: "+Integer.toString(songByteArray.length));
    		//Must dynamically figure out how long to decode for! Placeholder for now to ensure that AudioTrack implementation is viable!
    		audioTrack.write(songByteArray, 0, songByteArray.length);
    	} catch (Exception e) {
    		Log.d("server log", e.toString());
    		e.printStackTrace();  
    	}

//		player.release();
		try {
			audioTrack.play();
		}
		catch(Exception e) {
			Log.d("audio log", e.toString());
		}
	}

	public void startServer(MainActivity activity) {
		this.server = (ServerAsyncTask) new ServerAsyncTask(getApplicationContext(), this, activity);
		this.server.start();
	}

	public void serverBroadcastPause() {
		if (server != null) {
			this.server.broadcastPause(-1);
		}
	}

	public void serverBroadcastSong() {
		if (server != null) {
			this.server.broadcastSong();
		}
	}

	public void serverBroadcastPlay()  {
		if (server != null) {
			this.server.broadcastPlay(-1);
		}
	}

	
	//Deprecated method. Only works with corresponding deprecated method in server.
	/*public void serverBroadcastSeekTo() {
		if (server != null) {
			this.server.broadcastSeekTo(-1);
		}
	}*/
	
	public void serverBroadcastSeekToNotification(){
		if (server != null) {
			this.server.broadcastSeekToNotification(-1);
		}
	}

	public void startClient(String address, MainActivity activity) {
		this.client = new ClientAsyncTask(getApplicationContext(), this, address, activity);
		this.client.start();
	}

	public void clientSendPause() {
		if (client != null) {
			this.client.sendPause();
		}
	}

	public void clientSendPlay() {
		if (client != null) {
			this.client.sendPlay();
		}
	}

	public void clientSendSong() {
		if (client != null) {
			this.client.sendSong();
		}
	}

	public void clientSendSeekTo() {
		if (client != null) {
			this.client.sendSeekTo();
		}
	}
	
	
	public void clientSendSeekToNotification() {
		if (client != null) {
			this.client.sendSeekToNotification();
		}
	}
	

	public void disconnectClient() {
		if (this.client != null) {
			this.client.cancelTask();
		}
	}

	public void cancelServerAndClientTasks() {
		if (this.client != null) {
			this.client.sendDisconnect();
			this.client.cancelTask();
			client = null;
		}

		if (this.server != null) {
			this.server.broadcastDisconnect();
			this.server.cancelTask();
			server = null;
		}
	}

	public void initializeSongAndPause(Uri uri) {
		currentSongUri = uri;

		File songfile = new File(uriManager.getPath(getApplicationContext(), uri));
		AudioTrack audioTrack = new AudioTrack (3,44100, 12, 2, 100000,0);
    	try {
    		//Must fix this!
    		byte[] songByteArray = decode(uri,0,100000);
    		Log.d("new debugger","Length of songByteArray: "+Integer.toString(songByteArray.length));
    		//Must dynamically figure out how long to decode for! Placeholder for now to ensure that AudioTrack implementation is viable!
    		audioTrack.write(songByteArray, 0, songByteArray.length);
    	} catch (Exception e) {
    		Log.d("server log", e.toString());
    		e.printStackTrace();  
    	}

//		player.release();
		try {
			audioTrack.play();
		}
		catch(Exception e) {
			Log.d("audio log", e.toString());
		}
	}

	public void stopPlayback() {
		// if (player.isPlaying()) {
		// 	player.pause();
		// }
		// playbackStopped = true;
	}

	public void allowPlayback() {
		playbackStopped = false;
	}

	public void pause() {
		// if (player.isPlaying() && !playbackStopped) {
		// 	long timeBeforePause = nanoToMilli(System.nanoTime());
		// 	player.pause();
		// 	long timeAfterPause = nanoToMilli(System.nanoTime());
		// 	Log.d("audio log", "Pause delay: " + Long.valueOf(timeAfterPause-timeBeforePause).toString());
		// }
	}

	public void play() {
		// if (!player.isPlaying() && !playbackStopped) {
		// 	long timeBeforePlay = nanoToMilli(System.nanoTime());
		// 	player.start();
		// 	long timeAfterPlay = nanoToMilli(System.nanoTime());
		// 	long delay = timeAfterPlay-timeBeforePlay;
		// 	Log.d("audio log", "Play delay: " + Long.valueOf(delay).toString());
		// 	if ((delay) > 1) {
		// 		seekTo(player.getCurrentPosition()+(int)(delay), 1);
		// 	}
		// }
	}
	
	public void seekTo(int milliseconds, int iteration) {
		if (iteration >= 5) {
			return;
		}
		
		if (!playbackStopped) {
			// long timeBeforeSeekTo = nanoToMilli(System.nanoTime());
			// player.seekTo(milliseconds);
			// long timeAfterSeekTo = nanoToMilli(System.nanoTime());
			// long delay = timeAfterSeekTo-timeBeforeSeekTo;
			// Log.d("audio log", "SeekToNew delay: " + Long.valueOf(delay).toString());
			// if (delay > 2) {
			// 	seekTo(player.getCurrentPosition()+(int)(delay), ++iteration);
			// }
			// else {
			// 	return;
			// }
		}
	}

	public boolean isPlaying() {
		// if (player != null) {
		// 	return player.isPlaying();
		// }
		// else {
		// 	return false;
		// }
		return true;
		
	}

	public int getPosition() {
		//return player.getCurrentPosition()/1000;
		return 0;
	}

	public int getCurrentPosition() {
		//return player.getCurrentPosition();
		return 0;
	}

	public int getDuration() {
		//return player.getDuration()/1000;
		return 0;
	}

	Uri getCurrentTrackUri() {
		return currentSongUri;
	}

	void releasePlayer() {
		//player.release();
	}

}