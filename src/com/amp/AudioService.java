package com.amp;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

public class AudioService extends Service {
	
	private MediaPlayer player = new MediaPlayer();
	private final IBinder binder = new LocalBinder();
	static private boolean serviceStarted = false;
	private Uri currentTrackUri;
	
	static public boolean isServiceStarted() {
		return serviceStarted;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		serviceStarted = true;
		return binder;
	}
	
	public class LocalBinder extends Binder {
		public AudioService getService() {
			// Return this instance of LocalService so clients can call public methods
			return AudioService.this;
		}
	}
	
	public void initializeSong(Uri uri) {
		currentTrackUri = uri;
		
		player.release();
		player = new MediaPlayer();
		try {
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setDataSource(getApplicationContext(), currentTrackUri);
			player.prepare();
			player.start();
		}
		catch(Exception e) {}
	}
	
	public void pause() {
		if (player.isPlaying()) {
			player.pause();
		}
	}
	
	public void play() {
		if (!player.isPlaying()) {
			player.start();
		}
	}

	public boolean isPlaying() {
		return player.isPlaying();
	}
	
	public int getPosition() {
		return player.getCurrentPosition()/1000;
	}
	
	public int getDuration() {
		return player.getDuration()/1000;
	}
	
	public void seekTo(int milliseconds) {
		player.seekTo(milliseconds);
	}

}
