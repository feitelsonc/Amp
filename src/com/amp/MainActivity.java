package com.amp;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	boolean masterMode;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
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
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return false;
	}

}
