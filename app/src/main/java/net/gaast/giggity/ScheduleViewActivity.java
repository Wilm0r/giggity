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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.AbstractCollection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Set;

public class ScheduleViewActivity extends Activity {
	protected Schedule sched;
	protected Giggity app;

	/* TODO: oops, with VIEW_* constants gone the default view pref is broken.
	   Are these constants safe across builds? Uh oh. */
	private final static int VIEWS[] = {
		R.id.block_schedule,
		R.id.timetable,
		R.id.now_next,
		R.id.my_events,
		R.id.tracks,
	};

	private int curView;
	
	private Format dateFormat = new SimpleDateFormat("EE d MMMM");
	
	/* Set this if when returning to this activity we need a *full* redraw.
	 * (I.e. when returning from the settings menu.) */
	private boolean redraw;
	private Handler timer;

	private DrawerLayout drawerLayout;
	private RelativeLayout drawer;
	private ActionBarDrawerToggle drawerToggle;

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
		curView = Integer.parseInt(pref.getString("default_view", "1"));

		drawerLayout = (DrawerLayout) getLayoutInflater().inflate(R.layout.schedule_view_activity, null);
		View dl = drawerLayout;  /* Shorthand */
		setContentView(dl);
		drawer = (RelativeLayout) dl.findViewById(R.id.drawer);

		drawer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// NOOP at least for now just so touches don't fall through to bigScreen.
			}
		});

		ViewGroup menu = (LinearLayout) dl.findViewById(R.id.menu);
		menu.getChildCount();
		for (int i = 0; i < menu.getChildCount(); ++i) {
			View btn = menu.getChildAt(i);
			btn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					//setView(v.getId());
					onOptionsItemSelectedInt(v.getId());
					drawerLayout.closeDrawers();
				}
			});
		}

		/* Hamburger menu! */
		/* Should still consider v7-appcompat, depending on how much it, again, affects apk size.. */
		this.getActionBar().setDisplayHomeAsUpEnabled(true);
		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_menu_white_24dp, R.string.ok, R.string.ok) {
			@Override
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
				invalidateOptionsMenu();
			}

			@Override
			public void onDrawerClosed(View drawerView) {
				super.onDrawerClosed(drawerView);
				invalidateOptionsMenu();
			}
		};

		bigScreen = (LinearLayout) dl.findViewById(R.id.bigScreen);
		updateOrientation(getResources().getConfiguration().orientation);

		viewerContainer = (RelativeLayout) dl.findViewById(R.id.viewerContainer);

		/* TODO: See if I can do this in XML as well? (It's a custom private view. */
		RelativeLayout.LayoutParams lp;
		lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		days = new DayButtons(this);
		viewerContainer.addView(days, lp);
		
		redraw = false;
		timer = new Handler();

		/* If the OS informs us that the timezone changes, close this activity so the schedule
		   gets reloaded. (This because input is usually TZ-unaware while our objects aren't.) */
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
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
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
		updateNavDrawer();
	}

	public void updateNavDrawer() {
		/* Show currently selected view */
		for (int v : VIEWS) {
			drawerLayout.findViewById(v).setBackgroundColor((curView == v) ? 0xFFB0BEC5 : 0xFFCFD8DC);
		}

		if (sched != null) {
			LinkedList<Date> days = sched.getDays();
			TextView dr = (TextView) drawerLayout.findViewById(R.id.date_range);
			dr.setText(Giggity.dateRange(days.getFirst(), days.getLast()));

			drawerLayout.findViewById(R.id.tracks).setVisibility(sched.getTracks() != null ? View.VISIBLE : View.GONE);
			drawerLayout.findViewById(R.id.change_day).setVisibility(sched.getDays().size() > 1 ? View.VISIBLE : View.GONE);
		}
	}

	public void redrawSchedule() {
		/* TODO: User viewer.multiDay() here. Chicken-egg makes that impossible ATM. */
		if (curView != R.id.now_next && curView != R.id.my_events && curView != R.id.tracks && sched.getDays().size() > 1) {
			sched.setDay(sched.getDb().getDay());
			setTitle(sched.getDayFormat().format(sched.getDay()) + ", " + sched.getTitle());
		} else {
			sched.setDay(-1);
			setTitle(sched.getTitle());
		}
		
		if (curView == R.id.timetable) {
			setScheduleView(new TimeTable(this, sched));
		} else if (curView == R.id.now_next) {
			setScheduleView(new NowNext(this, sched));
		} else if (curView == R.id.my_events) {
			setScheduleView(new MyItemsView(this, sched));
		} else if (curView == R.id.tracks) {
			setScheduleView(new TrackList(this, sched));
		} else /* if (curView == R.id.block_schedule) */ {
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
		curView = view_;
		setEventDialog(null, null);
		redrawSchedule();
		updateNavDrawer();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		/* ActionBar arrow/burger goes here as well. */
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		onOptionsItemSelectedInt(item.getItemId());

		return super.onOptionsItemSelected(item);
	}

	private boolean onOptionsItemSelectedInt(int id) {
		switch (id) {
			case R.id.settings:
				redraw = true;
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.change_day:
				showDayDialog();
				return true;
			case R.id.search:
				this.onSearchRequested();
				return true;
			case R.id.export_selections:
				ScheduleUI.exportSelections(this, sched);
				return true;
			case R.id.timetable:
			case R.id.tracks:
			case R.id.block_schedule:
			case R.id.now_next:
			case R.id.my_events:
				setView(id);
				return true;
		}
		return true; // TODO - void
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
