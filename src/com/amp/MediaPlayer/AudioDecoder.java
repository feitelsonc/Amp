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
	private Uri currentSongUri;
    private AudioTrack currentAudioTrack;
	private float currentBufferTime;		//currentTimeOfTheBuffer in the song, very useful! Different from currentTime playing in song.
	
	private int playbackHeadPosition;
	private float currentSongTime;
	private float lastTime;
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
	
	public void initializeSong(Uri songUri) throws IOException {
		songDone = true;
		currentAudioTrack = decodeMP3Header(songUri);
		currentBufferTime = 0;
		currentSongUri = songUri;
		Log.d("Test Alpha","songLength: "+Float.toString(determineSongLength(songUri)));
		
		
		
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
			Log.d("newdebug01",infoFrameHeader.toString());
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
	
	private byte[] decodeAndPlay(Uri songUri, float maxMs) 
			  throws IOException {
				//MP3 header is 4 bytes.
				float startMs = currentBufferTime;
				Log.d("newDebug","currentBufferTime:"+Float.toString(0));
				
				File file = new File(uriManager.getPath(context, songUri));
				InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
				try {
					currentAudioTrack.play();	
					float totalMs = 0;
					boolean seeking = true;
					ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				    Bitstream bitstream = new Bitstream(inputStream);
				    Decoder decoder = new Decoder();
				    

					Log.d("newDebug","songUri: "+songUri.toString());
				     
				    songDone = false;
				    while (! songDone) {
				    	pauseWaitLoop();
				    	Header frameHeader = bitstream.readFrame();
				    	if (frameHeader == null) {
				    		songDone = true;
							player.pauseTrack();
							currentBufferTime = 0;
					        //If frameHeader is null, we have reached end of File,
					    	//or there is an erroneous gap in the file. Either way
					    	//stop writing to byteArray.
				    	} else {
					        totalMs += frameHeader.ms_per_frame();
					        Log.d("newDebug","totalMs: "+Float.toString(totalMs));
					        //Log.d("newDebug","frameHeader.ms_per_frame():"+Float.toString(frameHeader.ms_per_frame()));
					        //For test file this is 26.12245
							
					        if (totalMs >= startMs) {
					          seeking = false;
					        }
				         
					        if (!seeking) {
					          SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
					          short[] pcm = output.getBuffer();
					          for (short s : pcm) {
					        	  outStream.write(s & 0xff);
					        	  outStream.write((s >> 8 ) & 0xff);
					          }
					        }
					         
					        if (totalMs >= (startMs + maxMs)) {
					        	songDone = true;
					        	player.pauseTrack();
					        	currentBufferTime = 0;
					          //If done with track, pause the player.
					        }
				    	}
				      	bitstream.closeFrame();	
					    byte[] songByteArray = outStream.toByteArray();
					    currentAudioTrack.write(songByteArray, 0, songByteArray.length);
					    outStream = new ByteArrayOutputStream();
				    }
				    outStream.close();
					return null;		    
				}
				catch (Exception e) {
				    throw new IOException("Error:" + e);
				} 			
				finally {
					
				    inputStream.close();
				}	
			}
	
	private void updateTime(){
		if(player.isPlaybackEnabled()&&!player.isPaused())
		{
			playbackHeadPosition = currentAudioTrack.getPlaybackHeadPosition();
			AudioTimestamp timestamp = new AudioTimestamp();
			currentAudioTrack.getTimestamp(timestamp);
			Log.d("Timing Test Alpha", "timestamp: "+timestamp.toString());
			Log.d("Timing Test Alpha", "playbackHeadPosition: "+Integer.toString(playbackHeadPosition));
			currentSongTime+=lastTime;
			lastTime = System.nanoTime();
			
		}
	}
	
	private synchronized void pauseWaitLoop()	{
		if(player.isPaused())	
		{
			currentAudioTrack.pause();
		
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
}
