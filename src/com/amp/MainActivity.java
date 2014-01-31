package com.amp;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	
	boolean masterMode;
	ToggleButton playPause;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// change color of action bar
		ActionBar bar = getActionBar();
		bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#3f9fe0")));
		
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
		
		String intentType = getIntent().getStringExtra(SplashScreenActivity.GROUP_ACTION_EXTRA);
		if (intentType.equals(SplashScreenActivity.CREATE_GROUP_EXTRA)) {
			masterMode = true;
			Toast.makeText(this, "create group", Toast.LENGTH_SHORT).show();
		}
		else {
			masterMode = false;
			Toast.makeText(this, "join group", Toast.LENGTH_SHORT).show();
		}
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_activity_actions, menu);
	    return super.onCreateOptionsMenu(menu);
	}

}
