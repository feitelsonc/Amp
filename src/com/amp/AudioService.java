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
import com.amp.MediaPlayer.AmpPlayer;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/*AudioService manages the playback commands on this device, and the broadcasting of these commands from this device to others.
	-It sends commands to AmpPlayer.
	-It can request important song information from AmpPlayer such as songLength, currentSongTime, and errors in streaming the music.
*/

public class AudioService extends Service {

	// private MediaPlayer player = new MediaPlayer();
	private final IBinder binder = new LocalBinder();
	
	static private boolean serviceStarted = false;
	static private boolean playbackStopped = false;
	static private boolean isPlaying = false;
	
	private Uri currentSongUri;
	private ServerAsyncTask server = null;
    private ClientAsyncTask client = null;
	private static final String TAG = "AudioService";
	private AmpPlayer ampPlayer;
    
    
    private long nanoToMilli(long nanos) {
    	return nanos/1000000;
    }

	static public boolean isServiceStarted() {
		return serviceStarted;
	}

	public IBinder onBind(Intent arg0) {
		serviceStarted = true;
		this.ampPlayer = new AmpPlayer(getApplicationContext(),this);
		this.ampPlayer.start();
		return binder;
	}	

	public class LocalBinder extends Binder {
		public AudioService getService() {
			// Return this instance of LocalService so clients can call public methods
			return AudioService.this;
		}
	}

	public void initializeSong(Uri songUri){
		
		try {
			ampPlayer.initializeSong(songUri);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		currentSongUri = songUri;
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

	public void stopPlayback() {
		playbackStopped = true;
	}

	public void allowPlayback() {
		playbackStopped = false;
	}

	public void pause() {
		ampPlayer.pauseTrack();
		isPlaying = false;
	}

	public void play() {
    	ampPlayer.playTrack();
    	isPlaying = true;
	}
	
	public void seekTo(int milliseconds, boolean networked) {
			ampPlayer.seekTo(milliseconds, this);			 
	}
	
	/*public void seekTo(int milliseconds, int iteration) {
		if (iteration >= 10) {
			return;
		}
		
		if (!playbackStopped) {
			 long timeBeforeSeekTo = nanoToMilli(System.nanoTime());
			 ampPlayer.seekTo(milliseconds,this);
			 long timeAfterSeekTo = nanoToMilli(System.nanoTime());
			 long delay = timeAfterSeekTo-timeBeforeSeekTo;
			 Log.d("ITERATION OF SEEK TO", "SeekToNew delay: " + Long.valueOf(delay).toString());
			 if (delay > 10) {
			 	seekTo(ampPlayer.getCurrentPosition()+(int)(delay), ++iteration);
			 }
			 else {
			 	return;
			 }
		}
	}*/
	
//	public void seekTo(int milliseconds, int iteration)
//	{
//		ampPlayer.seekTo(milliseconds,this);
//	}
	public boolean isPlaying() {
		if (isPlaying) {
			return true;
		}
		else{
			return false;
		}
	}

	public int getPosition() {
		return ampPlayer.getCurrentPosition()/1000;
	}

	public int getCurrentPosition() {
		return ampPlayer.getCurrentPosition();
	}

	public int getDuration() {
		return (int)ampPlayer.songDuration/1000;
	}

	Uri getCurrentTrackUri() {
		return currentSongUri;
	}

	void releasePlayer() {
		//player.release();
	}

}