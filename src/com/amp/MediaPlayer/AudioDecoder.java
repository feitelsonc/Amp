package com.amp.MediaPlayer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.amp.AudioService;
import com.amp.URIManager;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import android.content.Context;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.net.Uri;
import android.util.Log;

public class AudioDecoder {
	
	AmpPlayer player;
	Context context;
	private URIManager uriManager;
	private boolean firstDip;				//need a class-wide variable to make the first dip as accurate as possible.
	private Uri currentSongUri;
    private AudioTrack currentAudioTrack;
	
	private Dipper dipper;				
	private boolean songDone;				//songDone is true after a song has finished playback, or when the song is switching.
	
	public AudioDecoder(AmpPlayer player, Context context) {
        this.context = context;
        this.player = player;
        this.uriManager = new URIManager();
		
	}
	
	public void run()
	{
		//this is repeated infinitely by AmpPlayer.run() method.
		playbackEnabledLoop();
		try {
			decodeAndPlay(currentSongUri, 600000);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public float initializeSong(Uri songUri) throws IOException {
		songDone = true;
		currentAudioTrack = decodeMP3Header(songUri);
		currentSongUri = songUri;
		firstDip=true;
		return determineSongLength(songUri);
		
	}
	
	private AudioTrack decodeMP3Header(Uri songUri)
	{
		//http://www.mp3-tech.org/programmer/frame_header.html
		
		Bitstream fileInfoBitStream;
		File file;
		Header infoFrameHeader;
		int sampleRate,sampleSizeInBytes, numChannels,audioTrackChannelConfig;
		
		try {
			file = new File(uriManager.getPath(context, songUri));
			fileInfoBitStream = new Bitstream(new FileInputStream(file));
			infoFrameHeader = fileInfoBitStream.readFrame();
			if(infoFrameHeader==null)
			{
				//throw error because of incorrect file format!;
				
			}
			else
			{
				Log.d("newdebug01",infoFrameHeader.toString());
			}
			Log.d("newdebug01","whoaaaa");
			//infoFrameHeader.version: 0 - MPEG Version 2.5 ; 2 - MPEG Version 2.0 ; 3 - MPEG Version 1
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
			if (numChannels==2)
			{
				audioTrackChannelConfig = 12;
			}
			else if (numChannels==1)
			{
				audioTrackChannelConfig = 4;
			}
			else
			{
				audioTrackChannelConfig = -1;
				//This should cause an error.
			}
			
			Log.d("innerestin","sampleRate: "+Integer.toString(sampleRate));
			
			Log.d("innerestin","audioTrackChannelConfig: "+Integer.toString(audioTrackChannelConfig));
			
			Log.d("innerestin","AudioTrack.getMinBufferSize(sampleRate,audioTrackChannelConfig,2)"+Integer.toString(AudioTrack.getMinBufferSize(sampleRate,audioTrackChannelConfig, 2)));
			
			fileInfoBitStream.close();
			
			return new AudioTrack (3,sampleRate, audioTrackChannelConfig, 2, AudioTrack.getMinBufferSize(sampleRate,audioTrackChannelConfig, 2),1);
			
		}
		catch (Exception e)
		{
			Log.e("Test Beta", e.toString()); 
			Log.d("newdebug","we in dis weird place");
			return null;
		}
	}
	
	private int sampleFrequencyIndex(int index, int mpegversionindex)
	{
		//infoFrameHeader.version: 0 - MPEG Version 2.5 ; 2 - MPEG Version 2.0 ; 3 - MPEG Version 1
		//http://www.mp3-tech.org/programmer/frame_header.html
		switch (mpegversionindex)
		{
			case 2:
				//infoFrameHeader.version: 0 - MPEG Version 2.5
				//MP3 frame header two bit representation is 00.
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
			case 0:
				//infoFrameHeader.version: 0 - MPEG Version 2.0 
				//MP3 frame header two bit representation is 10.
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
			case 1:
				//infoFrameHeader.version: 1 - MPEG Version 1
				//MP3 frame header two bit representation is 11.
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
	
	private float determineSongLength(Uri songUri)
			throws IOException {
		float startMs = 0;
		
		boolean songSeekDone= false;
		
		File file = new File(uriManager.getPath(context, songUri));
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
		try {	
			float totalMs = 0;
		    Bitstream bitstream = new Bitstream(inputStream);
		    Decoder decoder = new Decoder();

			Log.d("newDebug","songUri: "+songUri.toString());
		     
		    songSeekDone = false;
		    while (! songSeekDone) {
		    	Header frameHeader = bitstream.readFrame();
		    	if (frameHeader == null) {

		        	songSeekDone = true;
		        	bitstream.close();
					return totalMs;
			        //If frameHeader is null, we have reached end of File,
			    	//or there is an erroneous gap in the file. Either way
			    	//stop writing to byteArray.
		    	} else {
			        totalMs += frameHeader.ms_per_frame();
			        //Log.d("newDebug","frameHeader.ms_per_frame():"+Float.toString(frameHeader.ms_per_frame()));
			        //For test file this is 26.12245
		    	}
		      	bitstream.closeFrame();	
		    }		    
		}
		
		catch (Exception e) {
		    throw new IOException("Error:" + e);
		} 			
		finally {
		    inputStream.close();
		}	
			return -1;
	}
	
	private void decodeAndPlay(Uri songUri, float maxMs) 
			  throws IOException {
				//MP3 header is 4 bytes.
				//Not sure if the following line is exactly what we want, may cause errors in weird
				//multithreading cases in which we seekto at the same time as playing - which is exactly
				//what a seek to is - changing the buffer time nad hten playing...
				player.startMs = 0;
				float currentHeaderLength = 0;
				
				File file = new File(uriManager.getPath(context, songUri));
				InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
				try {
						
					player.totalMs = 0;
					player.seeking = false;
					ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				    Bitstream bitstream = new Bitstream(inputStream);
				    Decoder decoder = new Decoder();
				    currentAudioTrack.play();
				    
					Log.d("newDebug","songUri: "+songUri.toString());
				     
				    songDone = false;
				    while (! songDone) {
				    	//should acquire lock on player values at beginning of every loop.
				    	pauseWaitLoop();
				    	Header frameHeader = bitstream.readFrame();
				    	
				    	if (frameHeader == null) {
				    		songDone = true;
							player.pauseTrack();
					        //If frameHeader is null, we have reached end of File,
					    	//or there is an erroneous gap in the file. Either way
					    	//stop writing to byteArray.
				    	} else {
				    		currentHeaderLength = frameHeader.ms_per_frame();
					        player.totalMs += currentHeaderLength;
					        Log.d("newDebug","totalMs: "+Float.toString(player.totalMs));
							
					        if (player.seeking==true) {
					        	if(player.seekForward)
					        	{
						        	if(player.totalMs>=player.startMs)
						        	{
						        		firstDip = true;
						        		player.seeking = false;
						        	}
					        	}
					        	if(player.seekBack)
					        	{
					        		if(player.totalMs<=player.startMs)
					        		{
					        			firstDip=true;
					        			player.seeking=false;
					        			
					        			//NEW SEEK BACKWARDS SHOULD GO HEREE
					        			//-Perhaps create new fileinputstream and bitstream?
					        			//-Perhaps create mark at beginning of bit stream and then reset to that point in the bitstream?
					        			//	-Maybe not! This will require the system to remember every bit since the beginning of the song, possibly too much.
					        		}
					        	}
					        }
				         
					        if (!player.seeking) {
					          SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
					          short[] pcm = output.getBuffer();
					          for (short s : pcm) {
					        	  outStream.write(s & 0xff);
					        	  outStream.write((s >> 8 ) & 0xff);
					          }
					        }
					         
					        if (player.totalMs >= (player.startMs + maxMs)) {
					        	songDone = true;
					        	player.pauseTrack();
					          //If done with track, pause the player.
					        }
				    	}
				      	bitstream.closeFrame();	
					    byte[] songByteArray = outStream.toByteArray();
					    currentAudioTrack.write(songByteArray, 0, songByteArray.length);
					    if(firstDip)
					    {
					    	dipper = new Dipper((long)(player.totalMs-currentHeaderLength),System.nanoTime(),false);
					    	firstDip=false;
					    }
					    outStream = new ByteArrayOutputStream();
				    }
				    outStream.close();
					return;		    
				}
				catch (Exception e) {
				    throw new IOException("Error:" + e);
				} 			
				finally {
					
				    inputStream.close();
				}	
			}
	
	public long getTime(){
		if(dipper==null)
		{
			return 0;
		}
		if(dipper.paused)
		{
			return dipper.songTime;
		}
		return dipper.songTime+((System.nanoTime()-dipper.systemTime)/1000000);
	}
	
	private synchronized void pauseWaitLoop()	{
		
		if(player.isPaused())	
		{
			currentAudioTrack.pause();
			dipper = new Dipper(dipper.songTime+((System.nanoTime()-dipper.systemTime)/1000000),0,true);
			while(player.isPaused())
			{
				try {
					this.wait(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			currentAudioTrack.play();
			dipper = new Dipper(dipper.songTime,System.nanoTime(),false);
			firstDip = true;
		}	
	}
	
	private synchronized void playbackEnabledLoop()	{
		while(!player.isPlaybackEnabled())
		{
			try {
				this.wait(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private class Dipper{
		/*Dipper is supposed to give us a reliable timestamp from which to determine where in the song we are.
		We should create a new dipper every time playback is changed in any way (new song, stop, pause);
		Inneresting Use Case:
			-When we pause we should refresh the dipper.
			-When we start playback again, we should copy the pause dipper EXCEPT-
				- we need to update the systemTime to be as soon as playback is started.
			
		*/
		public long songTime;		//milliseconds
		public long systemTime;		//nanoseconds
		public boolean paused;
		
		Dipper(long songTime,long systemTime, boolean paused)
		{
			this.songTime=songTime;
			this.systemTime = systemTime;
			this.paused = paused;
		}
	}
}
