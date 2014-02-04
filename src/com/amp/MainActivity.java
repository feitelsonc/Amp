package com.amp;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.amp.AudioService.LocalBinder;

public class MainActivity extends Activity {
	
	static final String SELECTED_SONG = "selectedSong";
	private static final int SELECT_SONG = 1;
	boolean masterMode;
	boolean connected;
	ToggleButton playPause;
	ImageView albumArtView;
	TextView songTitleView;
	String intentType;
	Uri selectedSongUri = null;
	String selectedSongUriString = null;
	MediaMetadataRetriever metaRetriver;
    byte[] albumArt = null;
    private AudioService musicPlayerService = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// change color of action bar
		ActionBar bar = getActionBar();
		bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#3f9fe0")));
		
		albumArtView = (ImageView) findViewById(R.id.albumCover);
		songTitleView = (TextView) findViewById(R.id.songTitle);
		playPause = (ToggleButton) findViewById(R.id.playPause);
		playPause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		        if (isChecked) {
		            // Play is set
		        	playPause.setBackgroundResource(R.drawable.btn_play);
		        	if(musicPlayerService.isPlaying()) {
		        		musicPlayerService.pause();
		        	}
		        } else {
		            // Pause is set
		        	playPause.setBackgroundResource(R.drawable.btn_pause);
		        	musicPlayerService.play();
		        }
		    }
		});
		
		intentType = getIntent().getStringExtra(SplashScreenActivity.GROUP_ACTION_EXTRA);
		
		if (savedInstanceState != null) {
	        // Restore value of members from saved state
	    	selectedSongUriString = savedInstanceState.getString(SELECTED_SONG);
	    }
		
		if (intentType.equals(SplashScreenActivity.CREATE_GROUP_EXTRA)) {
			masterMode = true;
			selectedSongUriString = getIntent().getStringExtra(SplashScreenActivity.SELECTED_SONG_URI_EXTRA);
			selectedSongUri = Uri.parse(selectedSongUriString);
		}
		else {
			masterMode = false;
		}
	    
	    if(!AudioService.isServiceStarted()) {
	  		Intent intent = new Intent(this, AudioService.class);
	  		startService(intent);
		}
	    
	    bindToMusicPlayerService();
	    
		setupWidgets(selectedSongUriString);
		
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
	    // Save the user's current state
	    savedInstanceState.putString(SELECTED_SONG, selectedSongUriString);
	    super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_activity_actions, menu);
	    if (connected) {
	    	menu.findItem(R.id.joinGroup).setVisible(false);
	    }
	    else {
	    	menu.findItem(R.id.exitGroup).setVisible(false);
	    }
	    return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	        case R.id.changeSong:
	        	Intent selectSongIntent = new Intent();
            	selectSongIntent.setType("audio/*");
            	selectSongIntent.setAction(Intent.ACTION_GET_CONTENT);
            	startActivityForResult(selectSongIntent, SELECT_SONG);
	            return true;
	        case R.id.exitGroup:
	        	Toast.makeText(this, R.string.toast_exited_group, Toast.LENGTH_SHORT).show();
	        	connected = false;
	        	invalidateOptionsMenu();
	            return true;
	        case R.id.joinGroup:
	        	Toast.makeText(this, R.string.toast_joined_group, Toast.LENGTH_SHORT).show();
	        	connected = true;
	        	invalidateOptionsMenu();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override 
	protected void onActivityResult(int requestCode,int resultCode,Intent data){

	  if(requestCode == 1) {

	    if(resultCode == RESULT_OK){

	        //the selected audio
	    	selectedSongUri = data.getData(); 
	    	selectedSongUriString = selectedSongUri.toString();
	    	musicPlayerService.initializeSong(selectedSongUri);
	    	
	    	setupWidgets(selectedSongUriString);
	    }
	  }
	  super.onActivityResult(requestCode, resultCode, data);
	}
	
	private void setupWidgets(String songUriString) {
		if (songUriString != null && !songUriString.equals("")) {
			selectedSongUri = Uri.parse(songUriString);
			
	        metaRetriver = new MediaMetadataRetriever();
	        try {
	        	 metaRetriver.setDataSource(this, selectedSongUri);
	        }
	        catch (Exception e) {
	        	
	        }
	       
	        //Song info retrieval code
	        try {
	        	albumArt = metaRetriver.getEmbeddedPicture();
	            Bitmap songImage = BitmapFactory.decodeByteArray(albumArt, 0, albumArt.length);
	            if (albumArt != null) {
	            	albumArtView.setImageBitmap(songImage);
	            }
	            else {
	            	albumArtView.setImageDrawable(getResources().getDrawable(R.drawable.no_cover));
	            }
	            songTitleView.setVisibility(View.VISIBLE);
	            songTitleView.setText(metaRetriver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
	        } catch (Exception e) {
	        	albumArtView.setImageDrawable(getResources().getDrawable(R.drawable.no_cover));
	        	songTitleView.setVisibility(View.VISIBLE);
	        	songTitleView.setText(getResources().getString(R.string.untitled_track));
	        }
		}
	}
	
//	@Override
//	protected void onStop() {
//		super.onStop();
//
//		if(AudioService.isServiceStarted()) {
//			unbindToMusicPlayerService();
//		}
//	}
	
	private void bindToMusicPlayerService() {
		Intent intent = new Intent(this, AudioService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			LocalBinder binder = (LocalBinder) service;
			musicPlayerService = binder.getService();
			musicPlayerService.initializeSong(selectedSongUri);
			playPause.setBackgroundResource(R.drawable.btn_pause);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			musicPlayerService = null;
		}
	}; 
	
	private void unbindToMusicPlayerService() {

		if(musicPlayerService != null) {
			unbindService(mConnection);
			musicPlayerService = null;
		}
	}

}
