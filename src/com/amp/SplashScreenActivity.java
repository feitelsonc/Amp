package com.amp;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

public class SplashScreenActivity extends Activity {
	
	public static String GROUP_ACTION_EXTRA = "group action extra";
	public static String JOIN_GROUP_EXTRA = "join group";
	public static String CREATE_GROUP_EXTRA = "create group";
	public static String SELECTED_SONG_URI_EXTRA = "song uri";
	
	Button createGroup, joinGroup;
	private static final int SELECT_SONG = 1;
	Uri selectedSong;
	//COPY AND PASTE THESE
    private WifiP2pManager mManager;
    private Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;
    private List peers;
    protected PeerListListener myPeerListListener;
    //end
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash_screen);
		
		createGroup = (Button) findViewById(R.id.createGroup);
		joinGroup = (Button) findViewById(R.id.joinGroup);
		
		final Intent intent = new Intent(this, MainActivity.class);
		
		createGroup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	Intent selectSongIntent = new Intent();
            	selectSongIntent.setType("audio/*");
            	selectSongIntent.setAction(Intent.ACTION_GET_CONTENT);
            	startActivityForResult(selectSongIntent, SELECT_SONG);
            }
        });
		
		joinGroup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	intent.putExtra(GROUP_ACTION_EXTRA, JOIN_GROUP_EXTRA);
        	    startActivity(intent);
        	    SplashScreenActivity.this.finish(); // don't allow user to return to splash screen
            }
        });
		//COPY AND PASTE THESE
	    mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
	    mChannel = mManager.initialize(this, getMainLooper(), null);
	    mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
	    
	    mIntentFilter = new IntentFilter();
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
	    peers = new ArrayList();
	    

		mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener()
		{
		    @Override
		    public void onSuccess() {
		    	 Toast.makeText(getApplicationContext(),"Other peers have been discovered!", Toast.LENGTH_SHORT).show();
		    }
	
		    @Override
		    public void onFailure(int reasonCode) {
		    	
		    }
		});
		


		//enD
	}	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.splash_screen, menu);
		return false;
	}
	
	@Override 
	protected void onActivityResult(int requestCode,int resultCode,Intent data){

	  if(requestCode == 1){

	    if(resultCode == RESULT_OK){

	        //the selected audio
	    	selectedSong = data.getData(); 
	    	
	    	String uri = selectedSong.toString();
	    	
	    	Intent intent = new Intent(this, MainActivity.class);
	    	intent.putExtra(GROUP_ACTION_EXTRA, CREATE_GROUP_EXTRA);
	    	intent.putExtra(SELECTED_SONG_URI_EXTRA, uri);
    	    startActivity(intent);
    	    SplashScreenActivity.this.finish(); // don't allow user to return to splash screen
	    }
	  }
	  super.onActivityResult(requestCode, resultCode, data);
	}
	
	//Copy and paste these
	protected void onResume() {
	    super.onResume();
	    registerReceiver(mReceiver, mIntentFilter);
	}
	/* unregister the broadcast receiver */
	@Override
	protected void onPause() {
	    super.onPause();
	    unregisterReceiver(mReceiver);
	}
	
	//end

}
