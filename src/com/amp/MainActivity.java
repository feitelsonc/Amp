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
import android.os.AsyncTask;
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
    private boolean servconnection = false;
    // private variables for wifi direct
    private WifiP2pManager mManager;
    private Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;
    protected PeerListListener myPeerListListener;
    private ArrayList <WifiP2pDevice> devices = new ArrayList<WifiP2pDevice>();
    private String currentGroupAddress;
    private ServerAsyncTask server = null;
    private ClientAsyncTask client = null;


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
		        		
		        		if (masterMode && server != null) {
		        			server.broadcastPause();
		        		}
		        		else if (!masterMode && client != null) {
		        			client.sendPause();
		        		}
		        	}
		        } else {
		            // Pause is set
		        	playPause.setBackgroundResource(R.drawable.btn_pause);
		        	musicPlayerService.play();
		        	
		        	if (masterMode && server != null) {
	        			server.broadcastPlay();
	        		}
		        	else if (!masterMode && client != null) {
	        			client.sendPlay();
	        		}
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
		
	    mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
	    mChannel = mManager.initialize(this, getMainLooper(), null);
	    mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);	    
	    mIntentFilter = new IntentFilter();
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
	    
		mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
			
		    @Override
		    public void onSuccess() {
		    	mManager.requestPeers(mChannel, new PeerListListener(){
		    		@Override
		    		public void onPeersAvailable(WifiP2pDeviceList peerList){
		    			devices = new ArrayList<WifiP2pDevice> (peerList.getDeviceList());
		  			    	
		    			if (devices.size() > 0) {
		    				Toast.makeText(getApplicationContext(), "Nearby device(s) found", Toast.LENGTH_SHORT).show();
		    			}
		    			else {
//		    				Toast.makeText(getApplicationContext(), "No nearby devices found", Toast.LENGTH_SHORT).show();
		    			}
		    		}
		    	});
		    }
	
		    @Override
		    public void onFailure(int reasonCode) {
		    }
		});
		
		if (masterMode) {
			mManager.createGroup(mChannel, new WifiP2pManager.ActionListener(){
    			
    			@Override
    			public void onSuccess(){
    				connected = true;
    				Toast.makeText(getApplicationContext(), "Group Created", Toast.LENGTH_SHORT).show();
					server = (ServerAsyncTask) new ServerAsyncTask(getApplicationContext(), musicPlayerService);
					server.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    			}
    			
    			@Override
    			public void onFailure(int reason){
    				connected = false;
    				Toast.makeText(getApplicationContext(), "Error: " + Integer.valueOf(reason).toString() + " creating group", Toast.LENGTH_SHORT).show();
    			}
    		});
		}
		
		else{
			
		}
	
	}
	
	protected void onPause() {
		super.onPause();
		
		if (ticker != null) {
			ticker.stopTicker();
			ticker = null;
		}
		
	    unregisterReceiver(mReceiver);
	}
	
	
	@Override
	public void onResume() {
		super.onResume();
		
		bindToMusicPlayerService();
		
		registerReceiver(mReceiver, mIntentFilter);
		
		if (ticker == null && AudioService.isServiceStarted()) {
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
	    if (!masterMode) {
	    	if (connected) {
		    	menu.findItem(R.id.joinGroup).setVisible(false);
		    	menu.findItem(R.id.exitGroup).setVisible(true);
		    }
		    else {
		    	menu.findItem(R.id.joinGroup).setVisible(true);
		    	menu.findItem(R.id.exitGroup).setVisible(false);
		    }
	    }
	    else {
	    	menu.findItem(R.id.joinGroup).setVisible(false);
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
;
	        	mManager.cancelConnect(mChannel, new WifiP2pManager.ActionListener(){

	        		@Override
	    			public void onSuccess() {
	    	        	Toast.makeText(getApplicationContext(), R.string.toast_exited_group, Toast.LENGTH_SHORT).show();
	    			}
	        		
	    			@Override
	    			public void onFailure(int reason) {
	    			}
	    			
	    		});
	        	
	        	mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener(){

	    			@Override
	    			public void onFailure(int reason) {
	    				Toast.makeText(getApplicationContext(), "Exit Group Unsuccessful", Toast.LENGTH_SHORT).show();
	    			}

	    			@Override
	    			public void onSuccess() {
	    				if (client != null) {
	    					client.cancelTask();
	    				}
	    				if (server != null) {
	    					server.cancelTask();
	    				}
	    				
	    				connected = false;
	    				Toast.makeText(getApplicationContext(), "Exited Group", Toast.LENGTH_SHORT).show();
	    				groupAddressView.setVisibility(View.GONE);
	    	        	invalidateOptionsMenu();
	    			}
	    		});
	            return true;
	        case R.id.joinGroup:
	        	showDialog();
	        	
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
	    	
	    	if (masterMode && server != null) {
    			server.broadcastSong();
    		}
    		else if (!masterMode && client != null) {
    			client.sendSong();
    		}
	    	
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
	       
	        try {
	        	// fix huge meta image art case
	        	albumArt = metaRetriver.getEmbeddedPicture();
 	        	if (albumArt.length < 2000000) {
 	        		Bitmap songImage = BitmapFactory.decodeByteArray(albumArt, 0, albumArt.length);
 	        		if (albumArt != null) {
 		            	albumArtView.setImageBitmap(songImage);
 		            }
 		            else {
 		            	albumArtView.setImageDrawable(getResources().getDrawable(R.drawable.no_cover));
 		            }
 	        	}
 	        	else {
 	        		albumArtView.setImageDrawable(getResources().getDrawable(R.drawable.no_cover));
 	        	}
	            
	            String songTitleText = metaRetriver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
	            songTitleView.setText(songTitleText);
	            
	            if (songTitleText == null) {
	            	songTitleView.setVisibility(View.GONE);
	            }
	            else {
	            	songTitleView.setVisibility(View.VISIBLE);
	            }
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
    	private boolean groupInfoChanged = false;
    	
 
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
	    				
	    				if (!groupInfoChanged) {
	    					
	    					mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener(){ 
		    					
								@Override
								public void onGroupInfoAvailable(WifiP2pGroup group) {
									if (group != null) {
										currentGroupAddress = group.getOwner().deviceAddress;
										groupAddressView.setText("Group Address: " + currentGroupAddress);
										groupAddressView.setVisibility(View.VISIBLE);
//										Toast.makeText(getApplicationContext(), group.getNetworkName(), Toast.LENGTH_LONG).show();
										groupInfoChanged = true;
										
									}
								}
		    				});
	    					
	    			
	    				}
	    				
	    				if(!canceled) {
	    					if (musicPlayerService != null) {
	    						if (musicPlayerService.isPlaying())
		    						setPositionTrackerWidgets();
	    					}
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
		
	}

	@Override
	public void onStartTrackingTouch(SeekBar musicProgress) {
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar musicProgress) {
		musicProgress.setProgress(musicProgress.getProgress());
		if (musicPlayerService != null && musicPlayerService.isPlaying()) {
			musicPlayerService.seekTo(musicProgress.getProgress()*1000);
			
			if (masterMode && server != null) {
    			server.broadcastSeekTo();
    		}
        	else if (!masterMode && client != null) {
    			client.sendSeekTo();
    		}
		}
	}

	public void showDialog() {
        // Create an instance of the dialog fragment and show it
        DialogFragment dialog = new IPAddressDialog();
        dialog.show(getFragmentManager(), "ConnectDialog");
    }
	
	@Override
	public void onReturnValue(final String MACAddress) {
		
		if (MACAddress.equals("")) {
			Toast.makeText(getApplicationContext(), "Invalid address",Toast.LENGTH_SHORT).show();
			return;
		}
		
		mManager.requestPeers(mChannel, new PeerListListener(){
    		@Override
    		public void onPeersAvailable(WifiP2pDeviceList peerList){
    			devices = new ArrayList<WifiP2pDevice> (peerList.getDeviceList());
  			    	
    			if (devices.size() > 0) {
    				WifiP2pDevice device = null;
    				for (int i=0; i<devices.size(); i++) {
    					if (devices.get(i).deviceAddress.equals(MACAddress)) {
    						device = devices.get(i);
    					}
    				}
    				
    				if (device != null) {
    					final WifiP2pConfig config = new WifiP2pConfig();
        				config.deviceAddress = device.deviceAddress;
        				
        				mManager.connect(mChannel, config, new ActionListener() {
        					@Override
        					public void onSuccess() {
        						Toast.makeText(getApplicationContext(), "Connected to: " + config.deviceAddress,Toast.LENGTH_SHORT).show();
        						connected = true;
        						invalidateOptionsMenu();
        						recursivelyInitializeServerConnection(mManager);
        						
        						if (viewSwitcher.getDisplayedChild() == 1) {
        							viewSwitcher.setDisplayedChild(0);
        						}
        					}	
        						
    	
        					@Override
        					public void onFailure(int reason) {}
        				});
					}
    				
    				else {
    					Toast.makeText(getApplicationContext(), "No device found with that address", Toast.LENGTH_SHORT).show();
    				}
    				
    			}
    			else {
    				Toast.makeText(getApplicationContext(), "No nearby devices found", Toast.LENGTH_SHORT).show();
    			}		            
    		}
    	});
		
	}
	
	@Override
	public void onStop(){
		super.onStop();
		
		mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener(){

			@Override
			public void onFailure(int reason) {
			}

			@Override
			public void onSuccess() {	
			}
		});
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		
		if (ticker != null) {
			ticker.stopTicker();
			ticker = null;
		}
		
		if (AudioService.isServiceStarted()) {
			stopService(new Intent(this, AudioService.class));
			unbindToMusicPlayerService();
		}
		
		if (client != null) {
			client.cancelTask();
		}
		
		if (server != null) {
			server.cancelTask();
		}
	}
	
	public void recursivelyInitializeServerConnection(WifiP2pManager manager){
		mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener(){

			@Override
			public void onConnectionInfoAvailable(WifiP2pInfo info) {
				if (info.groupOwnerAddress == null) {
					recursivelyInitializeServerConnection(mManager);
				}
				else {
					client = new ClientAsyncTask(getApplicationContext(), musicPlayerService, info.groupOwnerAddress.getHostAddress());
					client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					servconnection = true;	
//					Toast.makeText(getApplicationContext(), info.groupOwnerAddress.getHostAddress(), Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

}
