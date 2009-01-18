package net.gaast.deoxide;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

public class BlockScheduleActivity extends Activity {
	Schedule sched;
	BlockSchedule bs;
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
        	sched = new Schedule(this);
        	sched.loadDeox("http://wilmer.gaast.net/deoxide/test.xml");
        } catch (Throwable t) {
        	finish();
        	return;
        }
    	setTitle("Deoxide: " + sched.getTitle());
    	// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		bs = new BlockSchedule(this, sched);
		setContentView(bs);
	}
    
    protected void onPause() {
    	sched.commit();
    	super.onPause();
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    	Log.i("BlockScheduleActivity", "Orientation changed");
    	/* We really don't have to do anything special here. The
    	 * layouts will take care of everything. */
    }
}
