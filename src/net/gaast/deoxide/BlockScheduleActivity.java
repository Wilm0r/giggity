package net.gaast.deoxide;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class BlockScheduleActivity extends Activity {
	Schedule sched;
	BlockSchedule bs;
    ProgressDialog prog;
    Handler resultHandler;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /* HACK! I suppose there are better ways to do this in Java? :-) */
        final Activity this_ = this;
        
        prog = new ProgressDialog(this);
        prog.setMessage("Loading schedule data...");
        prog.setIndeterminate(true);
        prog.show();
        
    	sched = new Schedule(this);
        Loader l = new Loader(sched, this.getIntent().getDataString());
        
	    resultHandler = new Handler() {
	    	@Override
	    	public void handleMessage(Message msg) {
	    		if (msg.what > 0) {
	    	    	setTitle("Block schedule: " + sched.getTitle());
		    		bs = new BlockSchedule(this_, sched);
		    		prog.dismiss();
		    		setContentView(bs);
	    		} else {
	    			finish();
	    		}
	    	}
	    };

        l.start();

        /*
         * 	public void loadData(String url) {
                try {
                	//sched.loadSchedule("http://wilmer.gaast.net/deoxide/test.xml");
                	//sched.loadSchedule("http://fosdem.org/2009/schedule/xcal");
                	sched.loadSchedule(this.getIntent().getDataString());
                } catch (Throwable t) {
                	//finish();
                	return;
                }
        	}
        */
        
    	// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
	}
    
    private class Loader extends Thread {
    	Schedule sched;
    	String source;
    	
    	public Loader(Schedule sched_, String source_) {
    		sched = sched_;
    		source = source_;
    	}
    	
		@Override
		public void run() {
			try {
				sched.loadSchedule(source);	
				resultHandler.sendEmptyMessage(1);
			} catch (Throwable t) {
				resultHandler.sendEmptyMessage(0);
			}
		}
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
