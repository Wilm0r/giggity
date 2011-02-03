/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

/* Sorry, this class is a glorious hack because I don't have a clue how Java and threading work. :-) */

public class ScheduleViewActivity extends Activity {
	private Schedule sched;
    private Giggity app;
    
    private final static int VIEW_BLOCKSCHEDULE = 1;
    private final static int VIEW_TIMETABLE = 2;
    private final static int VIEW_NOWNEXT= 3;
    
    private int view;
    
	private Format df = new SimpleDateFormat("EE d MMMM");
	
	/* Set this if when returning to this activity we need a *full* redraw.
	 * (I.e. when returning from the settings menu.) */
	private boolean redraw;
	private Handler timer;
	ScheduleViewer viewer;

    private SharedPreferences pref;
    
    private String showEventId;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (Giggity) getApplication();
        
        pref = PreferenceManager.getDefaultSharedPreferences(app);
        view = Integer.parseInt(pref.getString("default_view", "1"));
        
    	String url = getIntent().getDataString();
        
		if (url.contains("#")) {
			String parts[] = url.split("#", 2);
			url = parts[0];
			showEventId = parts[1];
		}
		
        if (app.hasSchedule(url)) {
        	try {
				sched = app.getSchedule(url, false);
			} catch (Exception e) {
				// Java makes me tired.
			}
        	onScheduleLoaded();
        } else {
        	horribleAsyncLoadHack(url);
        }
        
        redraw = false;
        timer = new Handler();
    }
    
    private void horribleAsyncLoadHack(String source_) { 
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
		    		
		    		new AlertDialog.Builder(ScheduleViewActivity.this)
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
    	    		sched = app.getSchedule(source, false);
    				resultHandler.sendEmptyMessage(1);
    			} catch (Throwable t) {
    				t.printStackTrace();
    				resultHandler.sendMessage(Message.obtain(resultHandler, 0, t));
    			}
    		}
        };

        loader.start();
	}
    
    private Runnable refresher = new Runnable() {
		@Override
		public void run() {
			if (viewer != null)
				viewer.refreshContents();

			/* Run again at the next minute boundary. */
			timer.postDelayed(refresher, 60000 - (System.currentTimeMillis() % 60000));
		}
    };
    
    @Override
    protected void onResume() {
    	if (sched != null) {
    		sched.resume();
    	}
    	
    	/* Bugfix: Search sets day to -1, have to revert that. */
    	if (view != VIEW_NOWNEXT && sched != null && sched.getDays().size() > 1)
    		sched.setDay(sched.getDb().getDay());
		
    	if (redraw) {
    		onScheduleLoaded();
    		redraw = false;
    	}
    	refresher.run();
    	super.onResume();
    }
    
    @Override
    protected void onPause() {
    	if (sched != null) {
    		sched.commit();
    		sched.sleep();
    	}
    	super.onPause();
    	timer.removeCallbacks(refresher);
    }
    
    private void onScheduleLoaded() {
    	if (view != VIEW_NOWNEXT && sched.getDays().size() > 1) {
    		sched.setDay(sched.getDb().getDay());
    		setTitle(df.format(sched.getDay()) + ", " + sched.getTitle());
    	} else {
    		sched.setDay(-1);
    		setTitle(sched.getTitle());
    	}
    	
    	if (view == VIEW_TIMETABLE) {
    		setContentView(new TimeTable(this, sched));
    	} else if (view == VIEW_BLOCKSCHEDULE) {
    		setContentView(new BlockSchedule(this, sched));
    	} else if (view == VIEW_NOWNEXT) {
    		setContentView(new NowNext(this, sched));
    	}
    	
    	if (showEventId != null) {
    		Schedule.Item item = sched.getItem(showEventId);
    		if (item != null) {
				EventDialog evd = new EventDialog(this, item);
		    	evd.setOnDismissListener(new OnDismissListener() {
		   			public void onDismiss(DialogInterface dialog) {
		   				showEventId = null;
		   			}
		   		});
				evd.show();
    		}
    	}
    }
    
    @Override
    public void setContentView(View viewer_) {
    	viewer = (ScheduleViewer) viewer_;
    	super.setContentView(viewer_);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    	Log.i("BlockScheduleActivity", "Configuration changed");
    	/* We really don't have to do anything special here. The
    	 * layouts will take care of everything. */
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
    	menu.add(0, 1, 0, "Settings")
    		.setShortcut('0', 's')
    		.setIcon(android.R.drawable.ic_menu_preferences);
   		menu.add(1, 2, 0, "Choose day")
			.setShortcut('1', 'd')
			.setIcon(android.R.drawable.ic_menu_day);
   		menu.add(1, 3, 0, "Timetable")
			.setShortcut('2', 't')
			.setIcon(android.R.drawable.ic_menu_agenda);
   		menu.add(1, 4, 0, "Block schedule")
			.setShortcut('3', 'b')
			.setIcon(R.drawable.blockschedule);
   		menu.add(1, 5, 0, "Now and next")
			.setShortcut('4', 'n')
			.setIcon(R.drawable.ic_menu_clock_face);
    	
    	return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.findItem(2).setVisible(view != VIEW_NOWNEXT);
    	menu.findItem(3).setVisible(view != VIEW_TIMETABLE);
    	menu.findItem(4).setVisible(view != VIEW_BLOCKSCHEDULE);
    	menu.findItem(5).setVisible(view != VIEW_NOWNEXT && sched.getDays().size() > 1);
    	return true;
    }
    
    public void showDayDialog() {
    	LinkedList<Date> days = sched.getDays();
    	CharSequence dayList[] = new CharSequence[days.size()];
    	int i, cur = -1;
    	for (i = 0; i < days.size(); i ++) {
    		if (sched.getDay().equals(days.get(i)))
    			cur = i;
    		dayList[i] = df.format(days.get(i));
    	}
    	
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle("Choose day");
    	builder.setSingleChoiceItems(dayList, cur, new DialogInterface.OnClickListener() {
    	    public void onClick(DialogInterface dialog, int item) {
    	    	sched.getDb().setDay(item);
    	    	onScheduleLoaded();
    	        dialog.dismiss();
    	    }
    	});
    	AlertDialog alert = builder.create();
    	alert.show();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case 1:
    		redraw = true;
    		Intent intent = new Intent(this, SettingsActivity.class);
    		startActivity(intent);
    		return true;
    	case 2:
    		showDayDialog();
    		return true;
    	case 3:
    		view = VIEW_TIMETABLE;
    		onScheduleLoaded();
    		return true;
    	case 4:
    		view = VIEW_BLOCKSCHEDULE;
    		onScheduleLoaded();
    		return true;
    	case 5:
    		view = VIEW_NOWNEXT;
    		onScheduleLoaded();
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
}
