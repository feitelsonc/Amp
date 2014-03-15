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
			this.server.broadcastSong(-1);
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
			this.client.cancelTask();
		}

		if (this.server != null) {
			this.server.cancelTask();
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
			long timeBeforePause = System.currentTimeMillis();
			player.pause();
			long timeAfterPause = System.currentTimeMillis();
			Log.d("audio log", "Pause delay: " + Long.valueOf(timeAfterPause-timeBeforePause).toString());
		}
	}

	public void play() {
		if (!player.isPlaying() && !playbackStopped) {
			long timeBeforePlay = System.currentTimeMillis();
			player.start();
			long timeAfterPlay = System.currentTimeMillis();
			iterativeSeekTo(player.getCurrentPosition()+(int)(timeAfterPlay-timeBeforePlay));
			Log.d("audio log", "Play delay: " + Long.valueOf(timeAfterPlay-timeBeforePlay).toString());
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

	public long seekTo(int milliseconds) {
		if (!playbackStopped) {

			long timeBeforeSeek = System.currentTimeMillis();
			player.seekTo(milliseconds);
			long timeAfterSeek = System.currentTimeMillis();
			long difference = timeAfterSeek-timeBeforeSeek;
			Log.d("audio log", "seek delay: " + Long.valueOf(timeAfterSeek-timeBeforeSeek).toString());
			return difference;
		}
		else
		{
			return 0;
		}
	}


	public void iterativeSeekTo(int milliseconds) {
		long difference = System.currentTimeMillis();
		player.seekTo(milliseconds);
		difference = System.currentTimeMillis()-difference;
		iterativeSeekTo(milliseconds, difference);					
	}

	public void iterativeSeekTo(int milliseconds,long delay) {
		long differenceOfThisOperations = System.currentTimeMillis();
		if(delay<5 && delay >-5)
		{
			return;
		}
		int nextdelayestimation=0;
		int delayguess=0;
		long differenceOfMediaPlayer;
		if (delay>5)
		{
		nextdelayestimation = (int)delay*(1/5);
		delayguess = nextdelayestimation+(int)delay; 		
		}
		else
		{
		//in this case delay is negative because we OVERSHOT our estimation.
		nextdelayestimation = (int)delay*(-1/5);
		delayguess = nextdelayestimation+(int)delay;
		}


		differenceOfThisOperations = System.currentTimeMillis()-differenceOfThisOperations;
		delayguess +=differenceOfThisOperations;
		//add this version of difference to the guess.

		differenceOfMediaPlayer = System.currentTimeMillis();
		player.seekTo(milliseconds+(delayguess));
		differenceOfMediaPlayer = System.currentTimeMillis()-differenceOfMediaPlayer;
		//Log.d("audio log", "seek delay: " + Long.valueOf(timeAfterSeek-timeBeforeSeek).toString());
		//return difference;
		/*IN CHECKING THE DELAY OF THE IF LOOP REQUIRED FOR ITERATION, FOUND MOSTLY 0 DELAY EVEN FOR SLOW PHONES, NOT NEGLIGIBLE OVER MANY CALLS, THUS
		 * WE TRY TO MINIMIZE CALLS WITH ERROR PREDICITON.
		 */
		//long newdelay = System.currentTimeMillis();
		delay = (int)differenceOfMediaPlayer+differenceOfThisOperations+delay-(delayguess);	
		iterativeSeekTo(milliseconds+(delayguess),delay);			
	}

	Uri getCurrentTrackUri() {
		return currentSongUri;
	}

	void releasePlayer() {
		player.release();
	}

}