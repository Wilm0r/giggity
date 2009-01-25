package net.gaast.deoxide;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

/* Sorry, this class is a glorious hack because I don't have a clue how Java and threading work. :-) */

public class BlockScheduleActivity extends Activity {
	Schedule sched;
	BlockSchedule bs;
    Deoxide app;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (Deoxide) getApplication();
        
        if (app.hasSchedule(getIntent().getDataString())) {
        	try {
				sched = app.getSchedule(getIntent().getDataString());
			} catch (Exception e) {
				// Java makes me tired.
			}
        	onScheduleLoaded();
        } else {
        	horribleAsyncLoadHack(getIntent().getDataString());
        }
    }
    
    private void horribleAsyncLoadHack(String source_) { 
        /* HACK! I suppose there are better ways to do "this" in Java? :-) */
        final Activity this_ = this;
        final String source;
        final Thread loader;
        final Handler resultHandler;
        final ProgressDialog prog;
        
        prog = new ProgressDialog(this);
        prog.setMessage("Loading schedule data...");
        prog.setIndeterminate(true);
        prog.show();

        source = source_;
        
	    resultHandler = new Handler() {
	    	@Override
	    	public void handleMessage(Message msg) {
	    		if (msg.what > 0) {
	    			onScheduleLoaded();
		    		prog.dismiss();
	    		} else {
		    		prog.dismiss();
		    		
		    		new AlertDialog.Builder(this_)
						.setTitle("Load error")
						.setMessage(msg.obj.toString())
						.show()
			    		.setOnDismissListener(new OnDismissListener() {
			    			public void onDismiss(DialogInterface dialog) {
			    				finish();
			    			}
			    		});
	    		}
	    	}
	    };

        loader = new Thread() {
    		@Override
    		public void run() {
    			try {
    	    		sched = app.getSchedule(source);
    				resultHandler.sendEmptyMessage(1);
    			} catch (Throwable t) {
    				resultHandler.sendMessage(Message.obtain(resultHandler, 0, t));
    			}
    		}
        };

        loader.start();

    	// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
	}
    
    @Override
    protected void onResume() {
    	if (sched != null) {
    		sched.resume();
    	}
    	super.onResume();
    }
    
    @Override
    protected void onPause() {
    	if (sched != null) {
    		sched.commit();
    		sched.sleep();
    	}
    	super.onPause();
    }
    
    private void onScheduleLoaded() {
    	setTitle("Block schedule: " + sched.getTitle());
		bs = new BlockSchedule(this, sched);
		setContentView(bs);
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
