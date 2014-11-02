package com.amp.MediaPlayer;

import java.io.IOException;

import com.amp.AudioService;


import android.content.Context;
import android.net.Uri;
import android.util.Log;

//AmpPlayer is the intermediary between audioService and AudioDecoder.
//	-It is it's own Thread and it runs the audioDecoder.
//	-It maintains states that signal to audioService and AudioDecoder what to do.
//	

public class AmpPlayer  extends Thread implements Runnable {
	

	private boolean paused;					//Music is paused or not.
	private boolean playbackEnabled;		//Determines whether playback actions such as play, pause, seekTo should be enabled.	
	
	public Uri currentSongUri;
	public float songDuration;
	float startMs;
	float totalMs;
	boolean seeking;
	boolean seekBack;
	boolean seekForward;
	
	private AudioDecoder audioDecoder;
	AudioService audioService;
	
	public AmpPlayer(Context context,AudioService audioService) {
	        this.audioService = audioService;
	        this.audioDecoder = new AudioDecoder(this,context);
	        paused = true;
	        playbackEnabled = false;
	    }
	 
	 @Override
    public void run() {
		while (true)
		{
			audioDecoder.run();
		}
	}
	 
		
	public void initializeSong(Uri songUri) throws IOException {
		stopPlayback();
		Log.d("INSURANCE","Ensure that initializeSong called from AudioService is being run.");
		audioService.stopPlayback();
		songDuration = audioDecoder.initializeSong(songUri);
		currentSongUri = songUri;
		audioService.allowPlayback();
		enablePlayback();				//could cause problems whereby playback was stopped, then song is initialized
										//and playback is enabled when it shouldnt be.
		//play();
	}
	
	public int getCurrentPosition()
	{
		return (int)audioDecoder.getTime();
	}
		
	public void seekTo(int milliseconds){
		if(milliseconds<totalMs)
		{
			seekBack = true;
			//Should be separate logic to allow backwards seekto. Not sure for now.
		}
		else
		{
			seekForward = true;
		}
		seeking = true;
		startMs = milliseconds;
	}
	 
	//Play/pause block.
	
	public void playTrack()
	{
		paused = false;
	}
 
	public void pauseTrack()
	{
		paused = true;
	}
 
	public boolean isPaused()
	{
		return paused;
	}
 
 //Playback enabling block.
 
	public boolean isPlaybackEnabled()
	{
		return playbackEnabled; 
	}
 
	public void stopPlayback()
	{
		playbackEnabled = false;
	}
 
	public void enablePlayback()
	{
		playbackEnabled = true;
	}

}
