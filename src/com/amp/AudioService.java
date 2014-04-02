package com.amp;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class AudioService extends Service {

	private MediaPlayer player = new MediaPlayer();
	private final IBinder binder = new LocalBinder();
	static private boolean serviceStarted = false;
	static private boolean playbackStopped = false;
	private Uri currentSongUri;
	private ServerAsyncTask server = null;
    private ClientAsyncTask client = null;
    
    private long nanoToMilli(long nanos) {
    	return nanos/1000000;
    }

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

	public void serverBroadcastSeekTo() {
		if (server != null) {
			this.server.broadcastSeekTo(-1);
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

	public void initializeSong(Uri uri) {
		currentSongUri = uri;

		player.reset();
//		player.release();
		player = new MediaPlayer();
		try {
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setDataSource(getApplicationContext(), currentSongUri);
			player.prepare();
			player.start();
		}
		catch(Exception e) {
			Log.d("audio log", e.toString());
		}
	}

	public void initializeSongAndPause(Uri uri) {
		currentSongUri = uri;

//		player.release();
		player.reset();
		player = new MediaPlayer();
		try {
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setDataSource(getApplicationContext(), currentSongUri);
			player.prepare();
			player.start();
			player.pause();
		}
		catch(Exception e) {
			Log.d("audio log", e.toString());
		}
	}

	public void stopPlayback() {
		if (player.isPlaying()) {
			player.pause();
		}
		playbackStopped = true;
	}

	public void allowPlayback() {
		playbackStopped = false;
	}

	public void pause() {
		if (player.isPlaying() && !playbackStopped) {
			long timeBeforePause = nanoToMilli(System.nanoTime());
			player.pause();
			long timeAfterPause = nanoToMilli(System.nanoTime());
			Log.d("audio log", "Pause delay: " + Long.valueOf(timeAfterPause-timeBeforePause).toString());
		}
	}

	public void play() {
		if (!player.isPlaying() && !playbackStopped) {
			long timeBeforePlay = nanoToMilli(System.nanoTime());
			player.start();
			long timeAfterPlay = nanoToMilli(System.nanoTime());
			long delay = timeAfterPlay-timeBeforePlay;
			Log.d("audio log", "Play delay: " + Long.valueOf(delay).toString());
			if ((delay) > 1) {
				seekTo(player.getCurrentPosition()+(int)(delay), 1);
			}
		}
	}
	
	public void seekTo(int milliseconds, int iteration) {
		if (iteration >= 5) {
			return;
		}
		
		if (!playbackStopped) {
			long timeBeforeSeekTo = nanoToMilli(System.nanoTime());
			player.seekTo(milliseconds);
			long timeAfterSeekTo = nanoToMilli(System.nanoTime());
			long delay = timeAfterSeekTo-timeBeforeSeekTo;
			Log.d("audio log", "SeekToNew delay: " + Long.valueOf(delay).toString());
			if (delay > 2) {
				seekTo(player.getCurrentPosition()+(int)(delay), ++iteration);
			}
			else {
				return;
			}
		}
	}

	public boolean isPlaying() {
		if (player != null) {
			return player.isPlaying();
		}
		else {
			return false;
		}
		
	}

	public int getPosition() {
		return player.getCurrentPosition()/1000;
	}

	public int getCurrentPosition() {
		return player.getCurrentPosition();
	}

	public int getDuration() {
		return player.getDuration()/1000;
	}

	Uri getCurrentTrackUri() {
		return currentSongUri;
	}

	void releasePlayer() {
		player.release();
	}

}