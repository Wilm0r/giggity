package net.gaast.deoxide;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class BlockScheduleActivity extends Activity {
	Schedule sched;
	BlockSchedule bs;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
        	sched = new Schedule(this);
        	//sched.loadDeox("http://wilmer.gaast.net/deoxide/test.xml");
        	sched.loadXcal("http://fosdem.org/2009/schedule/xcal");
        } catch (Throwable t) {
        	finish();
        	return;
        }
    	setTitle("Block schedule: " + sched.getTitle());
    	// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		bs = new BlockSchedule(this, sched);
		setContentView(bs);
	}
    
    @Override
    protected void onPause() {
    	sched.commit();
    	super.onPause();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    	Log.i("BlockScheduleActivity", "Orientation changed");
    	/* We really don't have to do anything special here. The
    	 * layouts will take care of everything. */
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
    	menu.add(0, 1, 0, "Settings")
    		.setShortcut('0', 's')
    		.setIcon(android.R.drawable.ic_menu_preferences);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case 1:
    		Intent intent = new Intent(this, SettingsActivity.class);
    		startActivity(intent);
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
}
