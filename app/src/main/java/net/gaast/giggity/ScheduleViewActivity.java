/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2021 Wilmer van der Gaast <wilmer@gaast.net>
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
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.transition.Explode;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.legacy.app.ActionBarDrawerToggle;

public class ScheduleViewActivity extends Activity {
	protected ScheduleUI sched;
	protected Giggity app;

	// This list is JUST used to highlight the current view in the navdrawer. Order is irrelevant.
	// There are two orders: One in arrays which is in order of invention for cfg compatibility.
	// The order in the nav drawer UI XML is the one that matters to the user.
	// TODO: Figure out whether I can stop needing this one :<
	private final static int VIEWS[] = {
		R.id.block_schedule,
		R.id.my_events,
		R.id.now_next,
		R.id.search,
		R.id.timetable,
		R.id.tracks,
	};

	private int curView;
	private boolean tabletView;  // EventDialog integrated instead of invoking a separate activity
	private boolean showHidden;

	/* Set this if when returning to this activity we need a *full* redraw.
	 * (I.e. when returning from the settings menu.) */
	private boolean redraw;
	private Handler timer;

	// So subclasses can disable nav drawer functionality. (SearchActivity)
	protected boolean wantDrawer = true;

	private DrawerLayout drawerLayout;
	private RelativeLayout drawer;
	private ActionBarDrawerToggle drawerToggle;

	private LinearLayout bigScreen;
	private ScheduleViewer viewer;
	private RelativeLayout viewerContainer;
	private EventDialogPager eventDialogView;
	private DayButtonsHider days;

	private SharedPreferences pref;

	private String showEventId;

	private BroadcastReceiver tzClose;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = (Giggity) getApplication();

		pref = PreferenceManager.getDefaultSharedPreferences(app);
		curView = getResources().getIdentifier(pref.getString("default_view", "net.gaast.giggity:id/block_schedule"), null, null);
		showHidden = pref.getBoolean("show_hidden", false);

		/* Consider making this a setting, some may find their tablet too small. */
		int screen = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
		tabletView = (screen >= Configuration.SCREENLAYOUT_SIZE_LARGE);

		// Fancy shared-element animations when opening event dialogs.
		getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
		//getWindow().setExitTransition(new ChangeImageTransform());
		getWindow().setExitTransition(new Explode());
		//getWindow().setAllowEnterTransitionOverlap(false);

		setContentView(R.layout.schedule_view_activity);
		View dl = drawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
		drawer = (RelativeLayout) dl.findViewById(R.id.drawer);

		drawer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// NOOP at least for now just so touches don't fall through to bigScreen.
			}
		});

		ViewGroup menu = (LinearLayout) dl.findViewById(R.id.menu);
		/* Set event handler for all static buttons, going to the option menu code. Dynamic buttons
		 * (from the schedule) have their own handlers. */
		for (int i = 0; i < menu.getChildCount(); ++i) {
			View btn = menu.getChildAt(i);
			btn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// Not for now as I can't undo it (toggler not calling handlers?) TODO v.setBackground(getResources().getDrawable(R.drawable.menu_gradient));
					onOptionsItemSelectedInt(v.getId());
					drawerLayout.closeDrawers();
				}
			});
		}

		if (wantDrawer) {
			/* Hamburger menu! */
			/* Should still consider v7-appcompat, depending on how much it, again, affects apk size.. */
			getActionBar().setDisplayHomeAsUpEnabled(true);
			drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_menu_white_24dp, R.string.navdrawer_on, R.string.navdrawer_off) {
				@Override
				public void onDrawerOpened(View drawerView) {
					super.onDrawerOpened(drawerView);
					invalidateOptionsMenu();
					/* Looks like this code doesn't actually run BTW. Need to figure that out later. */
					updateNavDrawer();
				}

				@Override
				public void onDrawerClosed(View drawerView) {
					super.onDrawerClosed(drawerView);
					invalidateOptionsMenu();
				}
			};
		} else {
			drawerLayout.removeView(drawer);
		}

		bigScreen = (LinearLayout) dl.findViewById(R.id.bigScreen);
		updateOrientation(getResources().getConfiguration().orientation);

		viewerContainer = (RelativeLayout) dl.findViewById(R.id.viewerContainer);

		/* TODO: See if I can do this in XML as well? (It's a custom private view.) */
		RelativeLayout.LayoutParams lp;
		lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		days = new DayButtonsHider();

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
		Uri parsed = Uri.parse(url);

		if (parsed.getHost() == null) {
			// Honestly no clue what can cause this (one article suggests underscores) but one user
			// on a Nokia with Android 9 managed!
			return;
		}

		if (parsed.getHost().equals("ggt.gaa.st") && parsed.getEncodedFragment() != null) {
			try {
				// Boo. Is there really no library to do this? Uri (instead of URL) supports CGI-
				// style arguments but not when that syntax is used after the #. (Using # instead of
				// ? to avoid the data hitting the server, should the query fall through.)
				for (String param : parsed.getEncodedFragment().split("&")) {
					if (param.startsWith("url=")) {
						url = URLDecoder.decode(param.substring(4), "utf-8");
					} else if (param.startsWith("json=")) {
						String jsonb64 = URLDecoder.decode(param.substring(5), "utf-8");
						byte[] json = Base64.decode(jsonb64, Base64.URL_SAFE);
						url = app.getDb().refreshSingleSchedule(json);
						if (url == null) {
							Toast.makeText(this, R.string.no_json_data, Toast.LENGTH_SHORT).show();
							finish();
							return;
						}
					}
				}
				parsed = Uri.parse(url);
			} catch (UnsupportedEncodingException e) {
				// IT'S UTF-8 DO YOU NOT SPEAK IT? WHAT YEAR IS IT?
			}
		}

		/* I think reminders come in via this activity (instead of straight to itemview)
		   because we may have to reload schedule data? */
		// TODO: Use parsed.
		if (url.contains("#")) {
			String parts[] = url.split("#", 2);
			url = parts[0];
			showEventId = parts[1];
		}

		if (app.hasSchedule(url)) {
			sched = app.getCachedSchedule(url);
			onScheduleLoaded();
		} else {
			Fetcher.Source fs;
			if (getIntent().getBooleanExtra("PREFER_ONLINE", false))
				fs = Fetcher.Source.ONLINE_CACHE;
			else
				fs = Fetcher.Source.CACHE_ONLINE;

			drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
			drawerToggle.setDrawerIndicatorEnabled(false);  // Shows a not functioning back button. :<
			loadScheduleAsync(url, fs);
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		if (drawerToggle != null) {
			drawerToggle.syncState();
		}
	}

	@Override
	public void onDestroy() {
		this.unregisterReceiver(tzClose);
		super.onDestroy();
	}

	/* Progress dialog for schedule/data load. Could consider putting it into Fetcher. */
	private class LoadProgressDialog extends ProgressDialog implements LoadProgress {
		private Handler updater;
		private LoadProgressDoneInterface done;

		public LoadProgressDialog(Context ctx) {
			super(ctx);

			setMessage(getResources().getString(R.string.loading_schedule));
			setIndeterminate(true);
			setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			setCanceledOnTouchOutside(false);
			setProgressNumberFormat(null);
			setMax(1);

			updater = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					if (msg.what == DONE) {
						if (done != null) {
							done.done();
						}
						dismiss();
					} else if (msg.what > 0 ) {
						if (msg.what <= 100) {
							if (getMax() == 1) {
								setIndeterminate(false);
								setMax(100);
							}
							setProgress(msg.what);
						}
					} else {
						dismiss();

						new AlertDialog.Builder(ScheduleViewActivity.this)
								.setTitle(R.string.loading_error)
								.setMessage(msg.obj != null ? msg.obj.toString() : "(null)")
								.show();
					}
				}
			};

			setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					/* This stops any further progress updates from getting delivered to a dead
					 * activity but keeps the download (?) running, which I suppose is fine. */
					updater = null;
					ScheduleViewActivity.this.finish();
				}
			});
		}

		public Handler getUpdater() {
			return updater;
		}

		public void setDone(LoadProgressDoneInterface done) {
			this.done = done;
		}
	}

	private class LoadProgressView extends FrameLayout implements LoadProgress {
		ProgressBar prog;
		private Handler updater;
		private LoadProgressDoneInterface done;
		private LoadProgressFallBackInterface fallBack;

		public LoadProgressView() {
			super(ScheduleViewActivity.this);
			inflate(ScheduleViewActivity.this, R.layout.schedule_load_progress, this);

			prog = findViewById(R.id.progressBar);
			prog.setMax(1);

			updater = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					if (msg.what == DONE) {
						if (done != null) {
							done.done();
						}
					} else if (msg.what == FROM_CACHE) {
						// Fetcher reports we're already reading from cache anyway.
						findViewById(R.id.load_cached).setEnabled(false);
					} else if (msg.what == STATIC_DONE) {
						// The download is done so no point in falling back (last stage probably <.1s anyway)
						findViewById(R.id.load_cached).setEnabled(false);
					} else if (msg.what > 0) {
						if (msg.what <= 100) {
							if (prog.getMax() == 1) {
								prog.setIndeterminate(false);
								prog.setMax(100);
							}
							prog.setProgress(msg.what);
						}
					} else {
						((TextView)findViewById(R.id.errorMessge)).setText(msg.obj != null ? msg.obj.toString() : "(null)");
						findViewById(R.id.errorContainer).setVisibility(VISIBLE);
						prog.setVisibility(GONE);
					}
				}
			};

			Button cached = findViewById(R.id.load_cached);
			cached.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					fallBack.fallBack();
				}
			});
		}

		public void setTitle(Db.DbSchedule dbs) {
			LinearLayout tb = findViewById(R.id.titleBar);
			tb.removeViewAt(0);
			ChooserActivity.makeScheduleTitleView(tb, dbs);
		}

		// TODO: Just don't export the handler directly I think?
		public Handler getUpdater() {
			return updater;
		}

		public void setDone(LoadProgressDoneInterface done) {
			this.done = done;
		}

		public void setFallBack(LoadProgressFallBackInterface fallBack) {
			this.fallBack = fallBack;
		}
	}

	// Interfaces can't be defined inside inner classes. :<
	interface LoadProgressDoneInterface {
		void done();
	}
	interface LoadProgressFallBackInterface {
		void fallBack();
	}
	interface LoadProgress {
		public static final int FROM_CACHE = 999997;   // This data is coming from cache
		public static final int STATIC_DONE = 999998;  // Done loading static data, only db stuff left to do
		public static final int DONE = 999999;
		public Handler getUpdater();
		public void setDone(LoadProgressDoneInterface done);
	}

	private void loadScheduleAsync(final String url, final Fetcher.Source source) {
		final LoadProgressView prog = new LoadProgressView();
		Db.DbSchedule dbs = app.getDb().getSchedule(url);
		if (dbs != null) {
			prog.setTitle(dbs);
		}
		prog.setDone(new LoadProgressDoneInterface() {
			@Override
			public void done() {
				onScheduleLoaded();
				viewerContainer.removeView((View) prog);
			}
		});
		try {
			app.fetch(url, Fetcher.Source.CACHE);
		} catch (IOException e) {
			// No cached copy available apparently so hide that option.
			prog.findViewById(R.id.load_cached).setVisibility(View.GONE);
		}
		prog.setFallBack(new LoadProgressFallBackInterface() {
			@Override
			public void fallBack() {
				viewerContainer.removeView((View) prog);
				loadScheduleAsync(url, Fetcher.Source.CACHE);
			}
		});
		viewerContainer.addView((View) prog, new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		new Thread() {
			@Override
			public void run() {
				try {
					sched = app.getSchedule(url, source, prog.getUpdater());
					if (prog.getUpdater() != null) {
						prog.getUpdater().sendEmptyMessage(LoadProgress.DONE);
					}
				} catch (Schedule.LateException e) {
					Log.d("LateException", "" + prog.getUpdater());
					// Hrm, we lost the race. TODO: Figure out what, but for now do nothing.
				} catch (Throwable t) {
					t.printStackTrace();
					if (prog.getUpdater() != null) {
						prog.getUpdater().sendMessage(Message.obtain(prog.getUpdater(), 0, t));
					}
				}
			}
		}.start();
	}

	/* Refreshes every minute. No network stuff here as it's scheduled to be at :00 second
	   (and automatically reschedules for that). */
	private Runnable minuteRefresher = new Runnable() {
		@Override
		public void run() {
			/* Run again at the next minute boundary. */
			timer.removeCallbacks(minuteRefresher);
			timer.postDelayed(minuteRefresher, 60000 + (-System.currentTimeMillis() % 60000));

			if (viewer != null)
				viewer.refreshContents();
		}
	};

	private Runnable miscRefresher = new Runnable() {
		@Override
		public void run() {
			if (viewer != null)
				viewer.refreshContents();
		}
	};

	private Runnable updateRoomStatus = new Runnable() {
		@Override
		public void run() {
			if (sched == null || !sched.hasRoomStatus())
				return;

			timer.removeCallbacks(updateRoomStatus);
			timer.postDelayed(updateRoomStatus, 60000);

			new Thread() {
				@Override
				public void run() {
					if ((sched.isToday() || BuildConfig.DEBUG) && sched.updateRoomStatus())
						runOnUiThread(miscRefresher);
				}
			}.start();
		}
	};

	@Override
	protected void onResume() {
		if (redraw) {
			redrawSchedule();
			redraw = false;
		} else {
			if (viewer != null) {
				viewer.onShow();
			}
		}
		minuteRefresher.run();
		updateRoomStatus.run();
		super.onResume();
	}

	@Override
	protected void onPause() {
		if (sched != null) {
			sched.commit();
		}
		super.onPause();
		timer.removeCallbacks(minuteRefresher);
		timer.removeCallbacks(updateRoomStatus);
	}

	private void onScheduleLoaded() {
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
		drawerToggle.setDrawerIndicatorEnabled(true);
		invalidateOptionsMenu();
		sched.setShowHidden(showHidden);
		ZonedDateTime now = ZonedDateTime.now();
		int day;
		if ((day = sched.setDay(now)) != -1 && now.isBefore(sched.getLastTimeZoned())) {
			// If today is on the schedule and we're not the past of its items yet, this is probably
			// where the user wants to go now instead of wherever they last were, so overwrite that.
			sched.getDb().setDay(day);
		}
		if (getIntent().hasExtra("SELECTIONS")) {
			Schedule.Selections sel = (Schedule.Selections) getIntent().getSerializableExtra("SELECTIONS");
			Dialog dia = new ScheduleUI.ImportSelections(this, sched, sel);
			dia.show();
		}
		if (curView == R.id.tracks && sched.getTracks() == null) {
			curView = R.id.timetable;
		}
		redrawSchedule();
		finishNavDrawer();
		updateNavDrawer();
		/* I think onResume() can get called before schedule is loaded in which
		   case no rescheduling happens, so give it an extra poke if we need to. */
		updateRoomStatus.run();

		/* Change our title + icon in the recent tasks view. Only supported from Lollipop+. */
		/* On first load, getIconBitmap() will kick off a background fetch so we'll miss out on
		   the custom icon then. So be it... */
		Bitmap icon = sched.getIconBitmap();
		ActivityManager.TaskDescription d;
		if (icon == null) {
			icon = ((BitmapDrawable)getResources().getDrawable(R.drawable.deoxide_icon)).getBitmap();
		}
		d = new ActivityManager.TaskDescription(sched.getTitle(), icon, getResources().getColor(R.color.primary));
		this.setTaskDescription(d);

		// Notifications were already scheduled at load time but that was before extra metadata (like c3nav)
		// was loaded. Kick off a refresh now.
		if (app.checkReminderPermissions(this, !app.getRemindItems().isEmpty())) {
			app.updateRemind();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (grantResults.length != 1) {
			return;
		}
		if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(this, "Note that event reminders won't show up if notifications aren't granted.", Toast.LENGTH_LONG).show();
		}
		// Nothing to do if it was granted: Alarms are already set, and this permission is only needed once those go off.
	}

	/* Add dynamic links based on schedule data. Diff from update* is this should be done only once. */
	public void finishNavDrawer() {
		if (!wantDrawer) {
			return;
		}
		if (sched == null) {
			Log.e("finishNavDrawer", "Called before critical was loaded?");
			return;
		}

		LinkedList<ZonedDateTime> days = sched.getDays();
		TextView dr = (TextView) drawerLayout.findViewById(R.id.drawer_date_range);
		dr.setText(Giggity.dateRange(days.getFirst(), days.getLast()));

		double offset = sched.getTzDiff();
		if (offset != 0) {
			String plus = offset > 0 ? "+" : "";
			dr.setText(dr.getText() + "\n" + getString(R.string.drw_tz_offset) + " " + plus + offset + "\n" + getString(R.string.drw_tz_is_yours));
		}

		if (sched.getLinks() != null) {
			Log.d("finishNavDrawer", "Links " + sched.getLinks().size());
			ViewGroup menu = (LinearLayout) drawerLayout.findViewById(R.id.menu);
			View sep = menu.findViewById(R.id.custom_sep);
			sep.setVisibility(View.VISIBLE);
			for (final Schedule.Link link : sched.getLinks()) {
				TextView item = (TextView) TextView.inflate(this, R.layout.burger_menu_item, null);
				app.setPadding(item, 8, 8, 8, 8);
				// Better would be if the retard would take the margin + padding layout options but
				// when I follow instructions from https://stackoverflow.com/questions/7714323/it-inflate-the-view-without-the-margin/7714382
				// the inflater will return a LinearLayout (menu) not TextView and shit explodes.
				// Since these items will never be active I got better things to do than convincing
				// the Android API to not be shite.
				item.setText(link.getTitle());
				item.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						openLink(link, true);
						drawerLayout.closeDrawers();
					}
				});
				menu.addView(item, menu.indexOfChild(sep));
			}
		}
	}

	/* Other updates that depend on more state (like currently active view). */
	public void updateNavDrawer() {
		if (!wantDrawer) {
			return;
		}
		/* Show currently selected view */
		for (int v : VIEWS) {
			navDrawerItemState((TextView) drawerLayout.findViewById(v), curView == v);
		}

		if (sched == null) {
			Log.e("updateNavDrawer", "Called before critical was loaded?");
			return;
		}

		drawerLayout.findViewById(R.id.tracks).setVisibility(
			(sched.getTracks() != null && sched.getTracks().size() > 0) ? View.VISIBLE : View.GONE);
		drawerLayout.findViewById(R.id.change_day).setVisibility(
				!viewer.multiDay() && (sched.getDays().size() > 1) ? View.VISIBLE : View.GONE);
		navDrawerItemState((TextView) drawerLayout.findViewById(R.id.show_hidden), showHidden);

		/* TimeTable extends the action bar with "tabs" and will have its own shadow. */
		app.setShadow(getActionBar(), !viewer.extendsActionBar());
	}

	private void navDrawerItemState(TextView v, boolean enabled) {
		if (enabled) {
			v.setBackgroundResource(R.drawable.burger_menu_active_background);
			v.setTextColor(getResources().getColor(R.color.light_text));
		} else {
			v.setBackgroundResource(R.color.light);
			v.setTextColor(((TextView)drawerLayout.findViewById(R.id.settings)).getTextColors());
		}
	}

	/** Open a link object, either just through the browser or by downloading locally and using a
	 * dedicated viewer.
	 * @param link Link object - if type is set we'll try to download and use a viewer, unless:
	 * @param allowDownload is set. This also used to avoid infinite loops in case of bugs.
	 */
	private void openLink(final Schedule.Link link, boolean allowDownload) {
		if (link.getType() != null) {
			Fetcher f = null;
			try {
				f = new Fetcher(app, link.getUrl(), Fetcher.Source.CACHE, link.getType());
			} catch (IOException e) {
				// Failure is ~expected so don't make a fuss about it at this stage. :-)
			}
			if (f != null) {
				Uri cached = f.cacheUri();
				try {
					Intent intent = new Intent();
					intent.setAction(Intent.ACTION_VIEW);
					intent.setDataAndType(cached, link.getType());
					intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					Log.d("ScheduleViewActivity", "Viewing content externally: " + intent);
					startActivity(intent);
				} catch (ActivityNotFoundException e) {
					new AlertDialog.Builder(ScheduleViewActivity.this)
							.setTitle(R.string.loading_error)
							.setMessage(getString(R.string.no_viewer_error) + " " +
									link.getType() + ": " + e.getMessage())
							.show();
				}
				return;
			} else if (allowDownload) {
				final LoadProgressDialog prog = new LoadProgressDialog(this);
				prog.setMessage(getResources().getString(R.string.loading_image));
				prog.setDone(new LoadProgressDoneInterface() {
					@Override
					public void done() {
						/* Try again, avoiding infinite-looping on this download just in case. */
						openLink(link, false);
					}
				});
				prog.show();

				Thread loader = new Thread() {
					@Override
					public void run() {
						try {
							Fetcher f = app.fetch(link.getUrl(), Fetcher.Source.ONLINE, link.getType());
							f.setProgressHandler(prog.getUpdater());

							/* Just slurp the entire file into a bogus buffer, what we need is the
							   file in ExternalCacheDir */
							byte[] buf = new byte[1024];
							//noinspection StatementWithEmptyBody
							while (f.getStream().read(buf) != -1) {}
							f.keep();

							/* Will trigger the done() above back in the main thread. */
							prog.getUpdater().sendEmptyMessage(LoadProgress.DONE);
						} catch (IOException e) {
							e.printStackTrace();
							prog.getUpdater().sendMessage(Message.obtain(prog.getUpdater(), 0, e));
						}
					}
				};
				loader.start();
				return;
			}
		}

		/* If type was not set, or if neither of the two inner ifs were true (do we have the file,
		   or, are we allowed to download it?), fall back to browser. */
		Uri uri = Uri.parse(link.getUrl());
		Intent browser = new Intent(Intent.ACTION_VIEW, uri);
		browser.addCategory(Intent.CATEGORY_BROWSABLE);
		startActivity(browser);
	}

	public void redrawSchedule() {
		if (sched == null) {
			Log.e("finishNavDrawer", "Called before critical was loaded?");
			return;
		}

		/* TODO: Use viewer.multiDay() here. Chicken-egg makes that impossible ATM. */
		if (curView != R.id.now_next && curView != R.id.my_events && curView != R.id.search && sched.getDays().size() > 1) {
			ZonedDateTime d = sched.setDay(sched.getDb().getDay());
			if (d != null) {
				setTitle(sched.getDayFormat().format(d) + ", " + sched.getTitle());
			}
		} else {
			sched.setDay(-1);
			setTitle(sched.getTitle());
		}

		if (curView == R.id.timetable) {
			setScheduleView(new TimeTable(this, (Collection) sched.getTents()));
		} else if (curView == R.id.now_next) {
			setScheduleView(new NowNext(this, sched));
		} else if (curView == R.id.my_events) {
			setScheduleView(new MyItemsView(this, sched));
		} else if (curView == R.id.tracks && sched.getTracks() != null) {
			setScheduleView(new TimeTable(this, (Collection) sched.getTracks()));
		} else if (curView == R.id.search) {
			setScheduleView(new ItemSearch(this, sched));
		} else {
			curView = R.id.block_schedule; /* Just in case curView is set to something weird. */
			setScheduleView(new BlockSchedule(this, sched));
		}

		/* User tapped on a reminder? */
		if (showEventId != null) {
			Schedule.Item item = sched.getItem(showEventId);
			/* Converting back and forth between item objects and just id strings is a little crappy but meh.. */
			ArrayList<Schedule.Item> items = new ArrayList<>();
			if (getIntent().hasExtra("others")) {
				Log.d("ScheduleViewActivity", "Copying others object :-/");
				for (String id : getIntent().getStringArrayExtra("others")) {
					Schedule.Item other_item = sched.getItem(id);
					if (other_item != null) {
						items.add(other_item);
					}
				}
			}
			showItem(item, items, false, null);
			showEventId = null;
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
		viewerContainer.addView((View) viewer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 3));
		viewer.onShow();

		days.show();
	}

	public void showItem(Schedule.Item item, final AbstractList<Schedule.Item> others, boolean new_activity, View animationOrigin) {
		/* No cleanup required for non-tablet view. */
		if (tabletView) {
			bigScreen.removeView(eventDialogView);
			eventDialogView = null;
		}
		/* And nothing else to do if we're cleaning up only. */
		if (item == null) {
			return;
		}

		String searchQuery = null;
		try {
			ItemSearch its = (ItemSearch) viewer;
			searchQuery = its.getQuery();
		} catch (ClassCastException e) {
			// OK! null will do.
		}
		if (tabletView && !new_activity) {
			eventDialogView = new EventDialogPager(this, item, others, searchQuery);  // TODO
			eventDialogView.setTitleClick(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showItem(eventDialogView.getShownItem(), others, true, null);
				}
			});
			bigScreen.addView(eventDialogView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 4));
		} else {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()),
					this, ScheduleItemActivity.class);
			if (others != null) {
				ArrayList<String> ids = new ArrayList<>();
				for (Schedule.Item o : others) {
					ids.add(o.getId());
				}
				intent.putExtra("search_query", searchQuery);
				intent.putExtra("others", ids.toArray(new String[others.size()]));
			}
			ActivityOptions options = null;
			if (animationOrigin != null) {
				options = ActivityOptions.makeSceneTransitionAnimation(
					this, animationOrigin, "title");
			}
			// TODO: Hmm if I don't care about the result why am I not just calling refreshItems()
			// in a on-resume handler or something?
			startActivityForResult(intent, 0, (options != null) ? options.toBundle() : null);
		}
	}

	@Override
	protected void onActivityResult(int reqCode, int resCode, Intent data) {
		/* On return from ScheduleItemActivity, remind state may have changed. Tell our viewer.
		 * (not looking at resCode, just always do this, doesn't really matter and it's always
		 * "cancelled" anyway. */
		refreshItems();
	}

	public void refreshItems() {
		if (viewer != null) {
			viewer.refreshItems();
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.i("BlockScheduleActivity", "Configuration changed");
		updateOrientation(newConfig.orientation);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO: Should I keep the Search button at all, now that it's just another view mode?
		if (curView == R.id.search) {
			// Well at least already don't show it if we're in search anyway.
			return false;
		}
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.scheduleviewactivity, menu);
		if (sched != null) {
			menu.findItem(R.id.search).setVisible(true);
		}
		return true;
	}

	public void showDayDialog() {
		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EE d MMMM");
		LinkedList<ZonedDateTime> days = sched.getDays();

		if (days.size() == 2) {
			/* If there are only two days, don't bother showing the dialog, even
			 * though we did promise to show it. :-P */
			sched.getDb().setDay(1 - sched.getDayNum());
			redrawSchedule();
			return;
		}

		CharSequence dayList[] = new CharSequence[days.size()];
		for (int i = 0; i < days.size(); i ++) {
			dayList[i] = dateFormat.format(days.get(i));
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.change_day);
		builder.setSingleChoiceItems(dayList, sched.getDayNum(), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				sched.getDb().setDay(item);
				redrawSchedule();
				dialog.dismiss();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void toggleShowHidden() {
		showHidden = !showHidden;
		sched.setShowHidden(showHidden);
		redrawSchedule();
		updateNavDrawer();
	}

	private void setView(int view_) {
		curView = view_;
		showItem(null, null, false, null);
		redrawSchedule();
		updateNavDrawer();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		/* ActionBar arrow/burger goes here as well. */
		if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		onOptionsItemSelectedInt(item.getItemId());

		return super.onOptionsItemSelected(item);
	}

	/* Called by either onOptionsItemSelected() above or by the nav drawer onclicks. BUT the custom
	 * buttons (if any) have their own event handlers. */
	private void onOptionsItemSelectedInt(int id) {
		switch (id) {
			case R.id.settings:
				redraw = true;
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				break;
			case R.id.change_day:
				showDayDialog();
				break;
			case R.id.show_hidden:
				toggleShowHidden();
				break;
			case R.id.export_selections:
				ScheduleUI.exportSelections(ScheduleViewActivity.this, sched);
				break;
			case R.id.home_shortcut:
				addHomeShortcut();
				break;
			case R.id.search:
			case R.id.timetable:
			case R.id.tracks:
			case R.id.block_schedule:
			case R.id.now_next:
			case R.id.my_events:
				setView(id);
				break;
		}
	}

	public void addHomeShortcut() {
		addHomeShortcut(true);
	}

	private void addHomeShortcut(boolean with_icon) {
		Intent shortcut = new Intent(Intent.ACTION_VIEW, Uri.parse(sched.getUrl()), this, ScheduleViewActivity.class);
		/* Make sure no other activities/events are in the activity stack anymore if the user uses
		   this shortcut as they're probably not interested in unrelated events at that point. */
		shortcut.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

		ShortcutInfoCompat.Builder sb = new ShortcutInfoCompat.Builder(this, sched.getUrl());
		sb.setIntent(shortcut);
		sb.setShortLabel(sched.getTitle());

		Bitmap bmp = null;
		if (with_icon) {
			bmp = sched.getIconBitmap();
			if (bmp != null) {
				sb.setIcon(IconCompat.createWithBitmap(bmp));
			}
		}

		/* Fall back to the usual Giggity logo. */
		if (bmp == null) {
			sb.setIcon(IconCompat.createWithResource(this, R.drawable.deoxide_icon));
		}

		/* Will show a dialog on O+ or so, and just sneakily create the icon through the old install
		   intent on older systems. Old code had handling of exception thrown due to oversized
		   icon, hopefully the support library will now do this?... */
		ShortcutManagerCompat.requestPinShortcut(this, sb.build(), null);
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

	// Used to contain the buttons, now just a handler to automatically show and hide the buttons,
	// rest is just part of the XML layout.
	private class DayButtonsHider {
		private ViewGroup dayButtons;
		private ImageButton dayPrev, dayNext;
		private Handler h;
		private Runnable hideEv;

		public DayButtonsHider() {
			dayButtons = viewerContainer.findViewById(R.id.dayButtons);
			dayNext = viewerContainer.findViewById(R.id.dayNext);
			dayNext.setImageResource(R.drawable.ic_arrow_forward_white_32dp);

			dayPrev = viewerContainer.findViewById(R.id.dayPrev);
			dayPrev.setImageResource(R.drawable.ic_arrow_back_white_32dp);

			dayPrev.setOnClickListener(new Button.OnClickListener() {
				@Override
				public void onClick(View v) {
					daySwitch(-1);
				}
			});
			dayNext.setOnClickListener(new Button.OnClickListener() {
				@Override
				public void onClick(View v) {
					daySwitch(+1);
				}
			});

			dayButtons.setVisibility(View.INVISIBLE);

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
			if (sched.getDays().size() == 2) {
				dayPrev.setVisibility(View.GONE);
				dayNext.setImageResource(R.drawable.ic_calendar_24px);
			}

			/* Z ordering in RelativeLayouts seems to be most-recently-added,
			 * so we have to keep bringing the buttons to front. :-/ */
			dayButtons.bringToFront();
			if (dayButtons.getVisibility() != View.VISIBLE) {
				dayButtons.setVisibility(View.VISIBLE);
				dayButtons.setAnimation(AnimationUtils.loadAnimation(ScheduleViewActivity.this, android.R.anim.fade_in));
			}
			
			/* Set a timer if we're now fading in the buttons, or reset it if
			 * they're already on screen. */
			h.removeCallbacks(hideEv);
			h.postDelayed(hideEv, 2000);
		}

		public void hide() {
			/* During the animation, visibility will be overridden to visible.
			 * Which means I can already set it to hidden now and the right
			 * thing will happen after the animation. */
			dayButtons.setVisibility(View.INVISIBLE);
			dayButtons.setAnimation(AnimationUtils.loadAnimation(ScheduleViewActivity.this, android.R.anim.fade_out));
		}

		private void daySwitch(int d) {
			sched.getDb().setDay((sched.getDayNum() + d + sched.getDays().size()) % sched.getDays().size());
			redrawSchedule();
		}
	}
}
