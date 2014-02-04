package com.amp;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	
	static final String SELECTED_SONG = "selectedSong";
	private static final int SELECT_SONG = 1;
	boolean masterMode;
	ToggleButton playPause;
	ImageView albumArtView;
	TextView songTitleView;
	String intentType;
	Uri selectedSongUri = null;
	String selectedSongUriString = null;
	MediaMetadataRetriever metaRetriver;
    byte[] albumArt = null;

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
		        } else {
		            // pause is set
		        	playPause.setBackgroundResource(R.drawable.btn_pause);
		        }
		    }
		});
		
		intentType = getIntent().getStringExtra(SplashScreenActivity.GROUP_ACTION_EXTRA);
		
		// Check whether we're recreating a previously destroyed instance
	    if (savedInstanceState != null) {
	        // Restore value of members from saved state
	    	selectedSongUriString = savedInstanceState.getString(SELECTED_SONG);
	    } else {
	    	selectedSongUriString = getIntent().getStringExtra(SplashScreenActivity.SELECTED_SONG_URI_EXTRA);
	    }
	    
		setupWidgets(selectedSongUriString);
		
		if (intentType.equals(SplashScreenActivity.CREATE_GROUP_EXTRA)) {
			masterMode = true;
//			Toast.makeText(this, "create group", Toast.LENGTH_SHORT).show();
		}
		else {
			masterMode = false;
//			Toast.makeText(this, "join group", Toast.LENGTH_SHORT).show();
		}
		
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
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override 
	protected void onActivityResult(int requestCode,int resultCode,Intent data){

	  if(requestCode == 1){

	    if(resultCode == RESULT_OK){

	        //the selected audio
	    	selectedSongUri = data.getData(); 
	    	selectedSongUriString = selectedSongUri.toString();
	    	
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

}
