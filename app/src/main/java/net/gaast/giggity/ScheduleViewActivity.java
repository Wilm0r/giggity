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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class ScheduleViewActivity extends Activity {
	protected Schedule sched;
	protected Giggity app;
	
	private final static int VIEW_BLOCKSCHEDULE = 1;
	private final static int VIEW_TIMETABLE = 2;
	private final static int VIEW_NOWNEXT = 3;
	private final static int VIEW_MINE = 4;
	private final static int VIEW_TRACKS = 5;
	
	private int view;
	
	private Format dateFormat = new SimpleDateFormat("EE d MMMM");
	
	/* Set this if when returning to this activity we need a *full* redraw.
	 * (I.e. when returning from the settings menu.) */
	private boolean redraw;
	private Handler timer;
	
	private LinearLayout bigScreen;
	private ScheduleViewer viewer;
	private RelativeLayout viewerContainer;
	private View eventDialogView;
	private EventDialog eventDialog;
	private DayButtons days;

	private SharedPreferences pref;
	
	private String showEventId;
	
	private BroadcastReceiver tzClose;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = (Giggity) getApplication();
		
		pref = PreferenceManager.getDefaultSharedPreferences(app);
		view = Integer.parseInt(pref.getString("default_view", "1"));
		
		/* Might be usable somewhere: this.getActionBar().setSubtitle("Blaaaaa"); */
		
		bigScreen = new LinearLayout(this);
		updateOrientation(getResources().getConfiguration().orientation);
		setContentView(bigScreen);
		
		viewerContainer = new RelativeLayout(this);
		bigScreen.addView((View) viewerContainer, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 3));
		RelativeLayout.LayoutParams lp;
		lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		days = new DayButtons(this);
		viewerContainer.addView(days, lp);
		
		redraw = false;
		timer = new Handler();
		
		tzClose = new BroadcastReceiver() {
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				ScheduleViewActivity.this.finish();
			}
		};
		registerReceiver(tzClose, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
		
		if (!getIntent().getAction().equals(Intent.ACTION_VIEW))
			return;
		
		String url = getIntent().getDataString();
		Fetcher.Source fs;
		if (getIntent().getBooleanExtra("PREFER_CACHED", false))
			fs = Fetcher.Source.CACHE_ONLINE;
		else
			fs = Fetcher.Source.ONLINE_CACHE;
		
		if (url.contains("#")) {
			String parts[] = url.split("#", 2);
			url = parts[0];
			showEventId = parts[1];
		}
		
		if (app.hasSchedule(url)) {
			try {
				sched = app.getSchedule(url, fs);
			} catch (Exception e) {
				// Java makes me tired.
				e.printStackTrace();
			}
			onScheduleLoaded();
		} else {
			loadScheduleAsync(url, fs);
		}
	}
	
	@Override
	public void onDestroy() {
		this.unregisterReceiver(tzClose);
		super.onDestroy();
	}
	
	private void loadScheduleAsync(String url_, Fetcher.Source source_) { 
		final String url = url_;
		final Fetcher.Source source = source_;
		final Thread loader;
		final Handler resultHandler;
		final ProgressDialog prog;
		
		final int DONE = 999999;
		
		prog = new ProgressDialog(this);
		prog.setMessage(this.getResources().getString(R.string.loading_schedule));
		prog.setIndeterminate(true);
		prog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		prog.setCanceledOnTouchOutside(false);
		prog.setProgressNumberFormat(null);
		prog.setMax(1);
		/* 
		timer.postDelayed(new Runnable() {
			@Override
			public void run() {
				//prog.hide();
				prog.setButton(DialogInterface.BUTTON_NEGATIVE, "Use cached copy", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// blaat
					}
				});
				prog.show();
			}
		}, 5);
		*/
		prog.show();

		resultHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == DONE) {
					onScheduleLoaded();
					prog.dismiss();
				} else if (msg.what > 0 ) {
					if (prog.getMax() == 1) {
						prog.setIndeterminate(false);
						prog.setMax(100);
					}
					prog.setProgress(msg.what);
				} else {
					prog.dismiss();
					
					new AlertDialog.Builder(ScheduleViewActivity.this)
						.setTitle(R.string.loading_error)
						.setMessage(msg.obj != null ? msg.obj.toString() : "(null)")
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
					sched = app.getSchedule(url, source, resultHandler);
					resultHandler.sendEmptyMessage(DONE);
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
		/* Bugfix: Search sets day to -1, have to revert that. */
		if (sched != null && sched.getDays().size() > 1 && !viewer.multiDay())
			sched.setDay(sched.getDb().getDay());
		
		if (redraw) {
			redrawSchedule();
			redraw = false;
		}
		refresher.run();
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		//setEventDialog(null, null);
		
		if (sched != null) {
			sched.commit();
		}
		super.onPause();
		timer.removeCallbacks(refresher);
	}
	
	@Override
	protected void onStop() {
		/* TODO: Remove the event dialog *here*. It's a bit annoying that it disappears even 
		 * when you just open a browser link, but I'm too worried about introducing bugs if
		 * I reshuffle this stuff more (the database code is very fragile). And hardly anyone
		 * seems to be using Giggity on a tablet anyway.. */
		super.onStop();
	}
	
	private void onScheduleLoaded() {
		if (getIntent().hasExtra("SELECTIONS")) {
			Schedule.Selections sel = (Schedule.Selections) getIntent().getSerializableExtra("SELECTIONS");
			Dialog dia = new ScheduleUI.ImportSelections(this, sched, sel);
			dia.show();
		}
		redrawSchedule();
	}
	
	public void redrawSchedule() {
		/* TODO: User viewer.multiDay() here. Chicken-egg makes that impossible ATM. */
		if (view != VIEW_NOWNEXT && view != VIEW_MINE && view != VIEW_TRACKS && sched.getDays().size() > 1) {
			sched.setDay(sched.getDb().getDay());
			setTitle(sched.getDayFormat().format(sched.getDay()) + ", " + sched.getTitle());
		} else {
			sched.setDay(-1);
			setTitle(sched.getTitle());
		}
		
		if (view == VIEW_TIMETABLE) {
			setScheduleView(new TimeTable(this, sched));
		} else if (view == VIEW_NOWNEXT) {
			setScheduleView(new NowNext(this, sched));
		} else if (view == VIEW_MINE) {
			setScheduleView(new MyItemsView(this, sched));
		} else if (view == VIEW_TRACKS) {
			setScheduleView(new TrackList(this, sched));
		} else /* if (view == VIEW_BLOCKSCHEDULE) */ {
			setScheduleView(new BlockSchedule(this, sched));
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
		
		this.invalidateOptionsMenu();
	}
	
	/** Called by EventDialog when an item is deleted. Not passing an argument 
	 * since more than one item can be deleted at once. */
	protected void onItemHidden() {
		redrawSchedule();
	}
	
	private void updateOrientation(int orientation) {
		if (orientation == Configuration.ORIENTATION_PORTRAIT)
			bigScreen.setOrientation(LinearLayout.VERTICAL);
		else
			bigScreen.setOrientation(LinearLayout.HORIZONTAL);
	}
	
	public void setScheduleView(View viewer_) {
		if (viewer != null)
			viewerContainer.removeView((View) viewer);
		viewer = (ScheduleViewer) viewer_;
		viewerContainer.addView((View) viewer, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 3));
		
		days.show();
	}
	
	public void setEventDialog(EventDialog d, Schedule.Item item) {
		int screen = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
		if (screen >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
			bigScreen.removeView(eventDialogView);
			if (eventDialog != null)
				eventDialog.onDismiss(null);
			
			if (d != null) {
				eventDialogView = d.genDialog(true);
				eventDialogView.setBackgroundResource(android.R.drawable.dialog_frame);
				bigScreen.addView(eventDialogView, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 4));
			}
		} else if (d != null) {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()),
			                           this, ScheduleItemActivity.class);
			startActivityForResult(intent, 0);
		}
		eventDialog = d;
	}

	@Override
	protected void onActivityResult(int reqCode, int resCode, Intent data) {
		if (eventDialog != null)
			eventDialog.onDismiss(null);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.i("BlockScheduleActivity", "Configuration changed");
		updateOrientation(newConfig.orientation);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.scheduleviewactivity, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (viewer == null || sched == null) {
			/* Note: I used to return false here which worked and seemed more correct, but ...
			 * now it looks like Android will keep not showing the menu if I return false just
			 * the first time? Return true. Menu shouldn't actually be usable as it's out of focus
			 * so I hope this is safe.. */
			return true;
		}

		menu.findItem(R.id.change_day).setVisible(!viewer.multiDay() && sched.getDays().size() > 1);
		menu.findItem(R.id.timetable).setVisible(view != VIEW_TIMETABLE);
		menu.findItem(R.id.tracks).setVisible(view != VIEW_TRACKS && sched.getTracks() != null);
		menu.findItem(R.id.block_schedule).setVisible(view != VIEW_BLOCKSCHEDULE);
		menu.findItem(R.id.now_next).setVisible(view != VIEW_NOWNEXT);
		menu.findItem(R.id.my_events).setVisible(view != VIEW_MINE);
		return true;
	}
	
	public void showDayDialog() {
		LinkedList<Date> days = sched.getDays();
		CharSequence dayList[] = new CharSequence[days.size()];
		int i, cur = -1;
		for (i = 0; i < days.size(); i ++) {
			if (sched.getDay().equals(days.get(i)))
				cur = i;
			dayList[i] = dateFormat.format(days.get(i));
		}
		
		if (days.size() == 2) {
			/* If there are only two days, don't bother showing the dialog, even
			 * though we did promise to show it. :-P */
			sched.getDb().setDay(1 - cur);
			redrawSchedule();
			return;
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.change_day);
		builder.setSingleChoiceItems(dayList, cur, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				sched.getDb().setDay(item);
				redrawSchedule();
				dialog.dismiss();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void setView(int view_) {
		view = view_;
		setEventDialog(null, null);
		redrawSchedule();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.settings:
			redraw = true;
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return true;
		case R.id.change_day:
			showDayDialog();
			return true;
		case R.id.timetable:
			setView(VIEW_TIMETABLE);
			return true;
		case R.id.tracks:
			setView(VIEW_TRACKS);
			return true;
		case R.id.block_schedule:
			setView(VIEW_BLOCKSCHEDULE);
			return true;
		case R.id.now_next:
			setView(VIEW_NOWNEXT);
			return true;
		case R.id.my_events:
			setView(VIEW_MINE);
			return true;
		case R.id.search:
			this.onSearchRequested();
			return true;
		case R.id.export_selections:
			ScheduleUI.exportSelections(this, sched);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public void onScroll() {
		if (sched.getDays().size() > 1)
			days.show();
	}
	
	/* Ugly convenience function to be used by schedule viewers to indicate
	 * that the user scrolled so we should show day switch buttons. */
	public static void onScroll(Context ctx) {
		ScheduleViewActivity me;
		try {
			me = (ScheduleViewActivity) ctx;
		} catch (ClassCastException e) {
			e.printStackTrace();
			return;
		}
		me.onScroll();
	}
	
	private class DayButtons extends RelativeLayout {
		private Button dayPrev, dayNext;
		private Handler h;
		private Runnable hideEv;
		
		public DayButtons(Context ctx) {
			super(ctx);

			RelativeLayout.LayoutParams lp;
			
			dayPrev = new Button(ScheduleViewActivity.this);
			dayPrev.setText("<");
			lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			addView(dayPrev, lp);

			dayNext = new Button(ScheduleViewActivity.this);
			dayNext.setText(">");
			lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			addView(dayNext, lp);
			
			dayPrev.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					daySwitch(-1);
				}
			});
			dayNext.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					daySwitch(+1);
				}
			});
			
			setVisibility(View.INVISIBLE);
			
			h = new Handler();
			hideEv = new Runnable() {
				@Override
				public void run() {
					hide();
				}
			};
		}
		
		public void show() {
			if (sched == null || viewer == null || sched.getDays().size() <= 1 || viewer.multiDay())
				return;
			
			/* Z ordering in RelativeLayouts seems to be most-recently-added,
			 * so we have to keep bringing the buttons to front. :-/ */
			this.bringToFront();
			if (this.getVisibility() != View.VISIBLE) {
				setVisibility(View.VISIBLE);
				days.setAnimation(AnimationUtils.loadAnimation(ScheduleViewActivity.this, android.R.anim.fade_in));
			}
			
			/* Set a timer if we're now fading in the buttons, or reset it if
			 * they're already on screen. */
			h.removeCallbacks(hideEv);
			h.postDelayed(hideEv, 2000);
		}
		
		public void hide() {
			/* During the animation, visibility will be overriden to visible.
			 * Which means I can already set it to hidden now and the right
			 * thing will happen after the animation. */
			setVisibility(View.INVISIBLE);
			days.setAnimation(AnimationUtils.loadAnimation(ScheduleViewActivity.this, android.R.anim.fade_out));
		}
		
		private void daySwitch(int d) {
			LinkedList<Date> days = sched.getDays();
			int i, cur = -1;
			for (i = 0; i < days.size(); i ++)
				if (sched.getDay().equals(days.get(i)))
					cur = i;
			
			sched.getDb().setDay((cur + d + days.size()) % days.size());
			redrawSchedule();
		}
	}
}
