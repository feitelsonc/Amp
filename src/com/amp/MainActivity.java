package com.amp;

import java.util.ArrayList;

import android.app.ActionBar;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import com.amp.AudioService.LocalBinder;


public class MainActivity extends Activity implements OnSeekBarChangeListener, IPAddressDialog.IPAddressDialogListener {
	
	static final String SELECTED_SONG = "selectedSong";
	private static final int SELECT_SONG = 1;
	private boolean masterMode;
	private boolean connected;
	private TextView groupAddressView;
	private ViewFlipper viewSwitcher;
	private ToggleButton playPause;
	private ImageView albumArtView;
	private TextView songTitleView;
	private TextView timePlayed;
	private TextView timeLeft;
	private SeekBar musicProgress;
	private String intentType;
	private Uri selectedSongUri = null;
	private String selectedSongUriString = null;
	private MediaMetadataRetriever metaRetriver;
	private byte[] albumArt = null;
	private AudioService musicPlayerService = null;
    private Handler handler = new Handler();
    private Ticker ticker = null;

    private WifiP2pManager mManager;
    private Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;
    protected PeerListListener myPeerListListener;
    private ArrayList <WifiP2pDevice> devices = new ArrayList<WifiP2pDevice>();
    private String currentGroupAddress;

    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// change color of action bar
		ActionBar bar = getActionBar();
		bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#3f9fe0")));
		
		groupAddressView = (TextView) findViewById(R.id.groupAddress);
		viewSwitcher = (ViewFlipper) findViewById(R.id.viewSwitcher);
		musicProgress = (SeekBar) findViewById(R.id.musicProgress);
		musicProgress.setOnSeekBarChangeListener(this);
		timePlayed = (TextView) findViewById(R.id.timePlayed);
		timeLeft = (TextView) findViewById(R.id.timeLeft);
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
			if (viewSwitcher.getDisplayedChild() == 1) {
				viewSwitcher.setDisplayedChild(0);
			}
		}
		else {
			masterMode = false;
			if (viewSwitcher.getDisplayedChild() == 0) {
				viewSwitcher.setDisplayedChild(1);
			}
		}
	    
	    if(!AudioService.isServiceStarted()) {
	  		Intent intent = new Intent(this, AudioService.class);
	  		startService(intent);
		}
	    
	    bindToMusicPlayerService();
	    
		setupWidgets(selectedSongUriString);
		
		if(ticker == null && AudioService.isServiceStarted()) {
    		ticker = new Ticker();
			ticker.start();
    	}
		
		//COPY AND PASTE THESE
	    mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
	    mChannel = mManager.initialize(this, getMainLooper(), null);
	    mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
	    
	    mIntentFilter = new IntentFilter();
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
	    

		mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener()
		{
		    @Override
		    public void onSuccess() {
//		    	 Toast.makeText(getApplicationContext(),"Other peers have been discovered!", Toast.LENGTH_SHORT).show();
		    }
	
		    @Override
		    public void onFailure(int reasonCode) {
		    	
		    }
		});
		
		if(masterMode){
			mManager.createGroup(mChannel, new WifiP2pManager.ActionListener(){
    			
    			@Override
    			public void onSuccess(){
    				connected = true;
    				Toast.makeText(getApplicationContext(), "Group Created Success!", Toast.LENGTH_SHORT).show();
    			}
    			@Override
    			public void onFailure(int reason){
    				connected = false;
    				Toast.makeText(getApplicationContext(), Integer.valueOf(reason).toString(), Toast.LENGTH_SHORT).show();
    			}
    		}
    		);
		}
		
		
	}
	
	protected void onPause() {
	    super.onPause();
	    unregisterReceiver(mReceiver);
	}
	
	
	@Override
	public void onResume() {
		super.onResume();
		
		registerReceiver(mReceiver, mIntentFilter);
		
		if(ticker == null && AudioService.isServiceStarted()) {
			ticker = new Ticker();
			ticker.start();
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
//	        	Toast.makeText(this, R.string.toast_joined_group, Toast.LENGTH_SHORT).show();
//	        	showDialog();
	        	
	        	if (masterMode){
	        	
	        		
	        		if(connected) {
	        			Toast.makeText(getApplicationContext(), "in if(connected) {}", Toast.LENGTH_SHORT).show();
		        		mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener(){ 
	    					
							@Override
							public void onGroupInfoAvailable(WifiP2pGroup group) {
								Toast.makeText(getApplicationContext(), "onGroupInfoAvailable Method", Toast.LENGTH_SHORT).show();
								currentGroupAddress = group.getOwner().deviceAddress;
								groupAddressView.setText("Group Address: " + currentGroupAddress);
								
								groupAddressView.setVisibility(View.VISIBLE);
							}
	    				});
	        		}
	        		
	        	}
	        	
	        	else{
	        	
       		 	mManager.requestPeers(mChannel, new PeerListListener(){
  			    @Override
  			    public void onPeersAvailable(WifiP2pDeviceList peerList){
  		        //peers.clear();
  		        //peers.addAll(peerList.getDeviceList());
  			    	devices = new ArrayList<WifiP2pDevice> (peerList.getDeviceList());
  			    	WifiP2pDevice device = devices.get(0);
  			    	
  			    	final WifiP2pConfig config = new WifiP2pConfig();
  					config.deviceAddress = device.deviceAddress;
  					mManager.connect(mChannel, config, new ActionListener() {

  					    @Override
  					    public void onSuccess() {
  					        Toast.makeText(getApplicationContext(), "connected to: " + config.deviceAddress,Toast.LENGTH_LONG).show();
  					    }

  					    @Override
  					    public void onFailure(int reason) {
  					        //failure logic
  					    }
  					});
  			    	
  			    	Toast.makeText(getApplicationContext(), peerList.toString(), Toast.LENGTH_LONG).show();  		            
  			    }
       		 	});
	        	}
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
	    	
	    	if (viewSwitcher.getDisplayedChild() == 1) {
				viewSwitcher.setDisplayedChild(0);
			}

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

			if(musicPlayerService.isPlaying()) {
				ticker = new Ticker();
				ticker.start();
			}
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
	
	private class Ticker extends Thread {
    	TickerRunnable runnable = null;
    	
    	public Ticker() {
    		this(new TickerRunnable());
     	}
    	
    	private Ticker(TickerRunnable runnable) {
    		super(runnable, "audio_player_ticker");
    		this.runnable = runnable;
    	}
    	
    	public void stopTicker() {
    		runnable.stopTicker();
    	}
    }
	
	private class TickerRunnable implements Runnable {
	   	private final int TICKER_TIME = 250;
    	
    	private boolean canceled = false; 
    	
 
    	@Override
    	public void run() {
     		
     		while(!canceled) {
	    		try {
	    			Thread.sleep(TICKER_TIME);
	    		} catch (InterruptedException e) {
	    			return;
	    		} catch (Exception e) {
	    			return;
	    		}
	
	    		handler.post(new Runnable() {
	    			@Override
	    			public void run() {
	    				if(!canceled) {
	    					if (musicPlayerService.isPlaying())
	    						setPositionTrackerWidgets();
	    				}
	    			}
	    		});
     		}
    	}
    	
    	public void stopTicker() {
    		canceled = true;
    	}
	}
	
	private void setPositionTrackerWidgets() {
		musicProgress.setMax(musicPlayerService.getDuration());
		musicProgress.setProgress(musicPlayerService.getPosition());
			
		Integer minutesPlayed = (musicPlayerService.getPosition() % 3600) / 60;
		StringBuilder minutesPlayedString = new StringBuilder();
		minutesPlayedString.append(minutesPlayed.toString());

		Integer secondsPlayed = musicPlayerService.getPosition() % 60;
		StringBuilder secondsPlayedString = new StringBuilder();
		secondsPlayedString.append(secondsPlayed.toString());
		if (secondsPlayed < 10) {
			secondsPlayedString.insert(0, "0");
		}
		timePlayed.setText(minutesPlayedString + ":" + secondsPlayedString);
		
		Integer timeRemaining = musicPlayerService.getDuration() - musicPlayerService.getPosition();
		Integer minutesLeft = (timeRemaining % 3600) / 60;
		StringBuilder minutesLeftString = new StringBuilder();
		minutesLeftString.append(minutesLeft.toString());

		Integer secondsLeft = timeRemaining % 60;
		StringBuilder secondsLeftString = new StringBuilder();
		secondsLeftString.append(secondsLeft.toString());
		if (secondsLeft < 10) {
			secondsLeftString.insert(0, "0");
		}
		timeLeft.setText(minutesLeftString + ":" + secondsLeftString);
	}

	@Override
	public void onProgressChanged(SeekBar musicProgress, int arg1, boolean arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStartTrackingTouch(SeekBar musicProgress) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar musicProgress) {
		musicProgress.setProgress(musicProgress.getProgress());
		if (musicPlayerService != null && musicPlayerService.isPlaying()) {
			musicPlayerService.seekTo(musicProgress.getProgress()*1000);
		}
	}

	public void showDialog() {
        // Create an instance of the dialog fragment and show it
        DialogFragment dialog = new IPAddressDialog();
        dialog.show(getFragmentManager(), "ConnectDialog");
    }
	
	@Override
	public void onReturnValue(String IPAddress) {
		Toast.makeText(this, "Connected to: " + IPAddress, Toast.LENGTH_SHORT).show();
		
	}
	@Override
	public void onStop(){
		
		mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener(){

			@Override
			public void onFailure(int reason) {
				
			}

			@Override
			public void onSuccess() {
				
			}});
		super.onStop();
	}

}
