package com.amp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.widget.Toast;
/**
 * A BroadcastReceiver that notifies of important Wi-Fi p2p events.
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager mManager;
    private Channel mChannel;
    private SplashScreenActivity mActivity;

    PeerListListener myPeerListListener;
    
    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel,
            SplashScreenActivity activity) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.mActivity = activity;
        
        
    }

    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
        	int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            	Toast.makeText(this.mActivity,"Wifi P2P enabled", Toast.LENGTH_SHORT).show();
            } else {
            	Toast.makeText(this.mActivity,"Wifi P2P not enabled", Toast.LENGTH_SHORT).show();
            }

        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
        	 if (mManager != null) {
        	        mManager.requestPeers(mChannel, new PeerListListener(){
        			    @Override
        			    public void onPeersAvailable(WifiP2pDeviceList peerList){
        		            //peers.clear();
        		            //peers.addAll(peerList.getDeviceList());
        		            Toast.makeText(mActivity,peerList.toString(), Toast.LENGTH_LONG).show();
        			    }
        		    });
        	        Toast.makeText(this.mActivity,"Peers Changed Action", Toast.LENGTH_SHORT).show();
        	    }
            // Call WifiP2pManager.requestPeers() to get a list of current peers
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
        	Toast.makeText(this.mActivity,"Peers Connection Changed Action", Toast.LENGTH_SHORT).show();
            // Respond to new connection or disconnections
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
        	Toast.makeText(this.mActivity,"This Device Changed Action", Toast.LENGTH_SHORT).show();
            // Respond to this device's wifi state changing
        }
    }
    
    
}