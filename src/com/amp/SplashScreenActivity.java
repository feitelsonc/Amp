package com.amp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.Button;

public class SplashScreenActivity extends Activity {
	
	public static String GROUP_ACTION_EXTRA = "group action extra";
	public static String JOIN_GROUP_EXTRA = "join group";
	public static String CREATE_GROUP_EXTRA = "create group";
	
	Button createGroup, joinGroup;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		getActionBar().hide();
		setContentView(R.layout.activity_splash_screen);
		
		createGroup = (Button) findViewById(R.id.createGroup);
		joinGroup = (Button) findViewById(R.id.joinGroup);
		
		final Intent intent = new Intent(this, MainActivity.class);
		
		createGroup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	intent.putExtra(GROUP_ACTION_EXTRA, CREATE_GROUP_EXTRA);
        	    startActivity(intent);
//        	    SplashScreenActivity.this.finish(); // don't allow user to return to splash screen
            }
        });
		
		joinGroup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	intent.putExtra(GROUP_ACTION_EXTRA, JOIN_GROUP_EXTRA);
        	    startActivity(intent);
//        	    SplashScreenActivity.this.finish(); // don't allow user to return to splash screen
            }
        });
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.splash_screen, menu);
		return false;
	}

}
