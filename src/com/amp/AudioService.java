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
		currentSongUri = uri;
		
		player.release();
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
		
		player.release();
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
	
//	public void play() {
//		if (!player.isPlaying() && !playbackStopped) {
//			long timeBeforePlay = System.currentTimeMillis();
//			player.start();
//			long timeAfterPlay = System.currentTimeMillis();
//			Log.d("audio log", "Play delay: " + Long.valueOf(timeAfterPlay-timeBeforePlay).toString());
//		}
//	}
	
	public long play() {
		if (!player.isPlaying() && !playbackStopped) {
			long timeBeforePlay = System.currentTimeMillis();
			player.start();
			long timeAfterPlay = System.currentTimeMillis();
			long difference = timeAfterPlay-timeBeforePlay;
			Log.d("audio log", "Play delay: " + Long.valueOf(difference).toString());
			difference = System.currentTimeMillis()-timeBeforePlay;
			return difference;
		}
		else
		{
			return 0;
	
		}
	}
	public void iterativePlay(){
		int milliseconds = getCurrentPosition(); 
		long delay = play();
		if (delay>5){
			//Log.d("audio log", "in iterative seek to loop");
			iterativeSeekTo(milliseconds,delay);			
		}
	}

	public boolean isPlaying() {
		return player.isPlaying();
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
	
	/*
	 * ITERATIVE SEEKTO VERSION 1
	 * 
	 * public void iterativeSeekTo(int milliseconds) {
		long delay = seekTo(milliseconds);
		long newdelay = System.currentTimeMillis();
		if(delay>4)
		{
			int intdelay = (int)delay;
			Log.d("audio log", "in iterative seek to loop:"+Long.valueOf(System.currentTimeMillis()-newdelay).toString());
			
			iterativeSeekTo(milliseconds);			
		}
	}*/
	
	/* ITERATIVE SEEKTO VERSION 2
	 * 
	 
	public void iterativeSeekTo(int milliseconds,int delay) {
		long timeBeforeSeek = System.currentTimeMillis();
		player.seekTo(milliseconds);
		long timeAfterSeek = System.currentTimeMillis();
		long difference = timeAfterSeek-timeBeforeSeek;
		//Log.d("audio log", "seek delay: " + Long.valueOf(timeAfterSeek-timeBeforeSeek).toString());
		//return difference;
		long delay = seekTo(milliseconds);
		/*IN CHECKING THE DELAY OF THE IF LOOP REQUIRED FOR RECURSION, FOUND MOSTLY 0 DELAY EVEN FOR SLOW PHONES, NOT NEGLIGIBLE OVER MANY CALLS, THUS
		 * WE TRY TO MINIMIZE CALLS WITH ERROR PREDICITON.
		 */
		/*long newdelay = System.currentTimeMillis();
		if(delay>4)
		{
			int intdelay = (int)delay;
			//Log.d("audio log", "in iterative seek to loop:"+Long.valueOf(System.currentTimeMillis()-newdelay).toString());
			
			iterativeSeekTo(milliseconds,intdelay);			
		}
	}
*/	
	
	public void iterativeSeekTo(int milliseconds) {
		long difference = System.currentTimeMillis();
		player.seekTo(milliseconds);
		difference = System.currentTimeMillis()-difference;
		/*try {
			Thread.sleep(500-difference);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		player.seekTo(milliseconds+500);*/
		//Log.d("audio log", "seek delay: " + Long.valueOf(timeAfterSeek-timeBeforeSeek).toString());
		//return difference;
		/*IN CHECKING THE DELAY OF THE IF LOOP REQUIRED FOR RECURSION, FOUND MOSTLY 0 DELAY EVEN FOR SLOW PHONES, NOT NEGLIGIBLE OVER MANY CALLS, THUS
		 * WE TRY TO MINIMIZE CALLS WITH ERROR PREDICITON.
		 */
		//long newdelay = System.currentTimeMillis();
		iterativeSeekTo(milliseconds,difference);					
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
