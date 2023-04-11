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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeSet;

import androidx.core.app.ActivityCompat;

/* OK so I'm not using ISO8601 ... but at least it's not middle-endian. And there's no portable date
   range format which is my real problem. So just silence that lint. */
@SuppressLint({"SimpleDateFormat"})
public class Giggity extends Application {
	private Db db;
	HashMap<String,ScheduleUI> scheduleCache = new HashMap<>();  // url→ScheduleUI
	TreeSet<Schedule.Item> remindItems = new TreeSet<>();
	Reminder reminder;

	static final String CHANNEL_ID = "X-GIGGITY-REMINDER";
	
	@Override
	public void onCreate() {
		super.onCreate();
		db = new Db(this);
		reminder = new Reminder(this);
		
		PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
		
		/* This was necessary when using timezone-naive Date classes. I've mostly dropped those
		 * but haven't finished picking up tz-awareness yet, also schedule files lack tz info
		 * still most of the time. So ... keep flushing data for now I guess. :-( */
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				for (Schedule sched : scheduleCache.values()) {
					sched.commit();
				}
				
				scheduleCache.clear();

				/* Ideally, reload all the schedules that were previously resident. But this
				 * was fragile when I wrote it, so ... just rely on the user reloading so that
				 * all alarms will get set in the right timezone?
				try {
					for (String url : urls)
						getSchedule(url, true);
				} catch (Exception e) {
					Log.e("Giggity", "Failed to reload schedules after timezone change..");
					e.printStackTrace();
				}
				*/
			}
		}, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));

		int importance = NotificationManager.IMPORTANCE_HIGH;  // Make sound but don't pup up.
		NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), importance);
		channel.setDescription(getString(R.string.notification_channel_description));
		NotificationManager notificationManager = getSystemService(NotificationManager.class);
		notificationManager.createNotificationChannel(channel);

		// Apparently needed now that I don't use android.util.Xml.parse anymore. (As it did not
		// work in standalone tests.)
		System.setProperty("org.xml.sax.driver","org.xmlpull.v1.sax2.Driver");
	}
	
	public Db.Connection getDb() {
		return db.getConnection();
	}
	
	public boolean hasSchedule(String url) {
		return scheduleCache.containsKey(url);
	}

	public ScheduleUI getCachedSchedule(String url) { return scheduleCache.get(url); }
	
	public void flushSchedules() {
		scheduleCache.clear();
		remindItems.clear();
	}

	public void flushSchedule(String url) {
		if (hasSchedule(url)) {
			Schedule sched = scheduleCache.get(url);
			for (Schedule.Item item : getRemindItems()) {
				if (item.getSchedule() == sched)
					remindItems.remove(item);
			}
		}
		scheduleCache.remove(url);
	}
	
	public ScheduleUI getSchedule(String url, Fetcher.Source source, Handler progress) throws Schedule.LoadException {
		if (!hasSchedule(url)) {
			scheduleCache.put(url, ScheduleUI.loadSchedule(this, url, source, progress));
		}
		return (scheduleCache.get(url));
	}

	public void updateRemind(Schedule.Item item) {
		if (item.getRemind()) {
			if (item.compareTo(ZonedDateTime.now()) < 0)
				remindItems.add(item);
		} else
			remindItems.remove(item);
		
		reminder.poke(item);
		Widget.updateWidget(this);
	}

	public void updateRemind() {
		for (Schedule.Item it : getRemindItems()) {
			updateRemind(it);
		}
	}
	
	protected Collection<Schedule.Item> getRemindItems() {
		return new ArrayList<>(remindItems);
	}
	
	public int dp2px(int dp) {
		float scale = getResources().getDisplayMetrics().density;
		return (int) (scale * dp);
	}
	
	/** Sigh */
	public void setPadding(View view, int left, int top, int right, int bottom) {
		view.setPadding(dp2px(left), dp2px(top), dp2px(right), dp2px(bottom));
	}

	/* TODO: Are these wrappers really that useful? */
	public Fetcher fetch(String url, Fetcher.Source source) throws IOException {
		return new Fetcher(this, url, source);
	}

	public Fetcher fetch(String url, Fetcher.Source source, String type) throws IOException {
		return new Fetcher(this, url, source, type);
	}

	// TODO: IIRC there's a localised version for this already? Though honestly I prefer mine since
	// it avoids doing atrocious middle-endian dates which is factually a good thing.
	public static String dateRange(Date start, Date end) {
		String ret = "";
		if (start.getDate() == end.getDate() && start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = new SimpleDateFormat("d MMMM").format(end);
		else if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = "" + start.getDate() + "–" + new SimpleDateFormat("d MMMM").format(end);
		else
			ret = new SimpleDateFormat("d MMMM").format(start) + "–" + new SimpleDateFormat("d MMMM").format(end);
		return ret + " " + (1900 + end.getYear());
	}

	public static String dateRange(ZonedDateTime start, ZonedDateTime end) {
		String ret = "";
		if (start.getDayOfMonth() == end.getDayOfMonth() && start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = DateTimeFormatter.ofPattern("d MMMM").format(end);
		else if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = "" + start.getDayOfMonth() + "–" + DateTimeFormatter.ofPattern("d MMMM").format(end);
		else
			ret = DateTimeFormatter.ofPattern("d MMMM").format(start) + "–" + DateTimeFormatter.ofPattern("d MMMM").format(end);
		return ret + " " + end.getYear();
	}

	public void setShadow(View v, boolean on) {
		v.setElevation(on ? dp2px(8) : 0);
	}

	/* ActionBar is not a view, just looks a lot like one! */
	public void setShadow(ActionBar v, boolean on) {
		v.setElevation(on ? dp2px(8) : 0);
	}

	static boolean fuzzyStartsWith(String prefix, String full) {
		if (prefix == null || full == null) {
			Log.e("fuzzyStartsWith", "Called with null argument.");
			return false;
		}
		prefix = prefix.replaceAll("<[^>]*>", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		full = full.replaceAll("<[^>]*>", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		return full.startsWith(prefix);
	}

	public void showKeyboard(Context ctx, View rx) {
		InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (rx != null) {
			imm.showSoftInput(rx, InputMethodManager.SHOW_IMPLICIT);
		} else {
			Activity a = (Activity) ctx;
			// TODO: Fecker isn't hiding anything. Some examples use getCurrentFocus().getWindowToken()
			// but at this stage no element has focus yet so that means null.getWindowToken() → kaboom
			imm.hideSoftInputFromWindow(new View(a).getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
		}
	}

	public static boolean checkReminderPermissions(Context ctx, boolean checked) {
		boolean ret = true;
		// Honestly don't bother translating this? If the user sabotages functionality they're trying to use...
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
		if (!checked || !pref.getBoolean("reminder_enabled", true) || Build.VERSION.SDK_INT < 31) {
			// If either no reminders are set, or the functionality is disabled, or we're on an "old" Android version, then don't worry. :)
			return true;
		}
		if (Build.VERSION.SDK_INT >= 33) {
			if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
				ret = false;
				if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) ctx, Manifest.permission.POST_NOTIFICATIONS)) {
					new AlertDialog.Builder(ctx)
						.setMessage("Giggity seems to be missing permission for sending the event notifications you've requested.")
						.setTitle("Missing notification permission")
						.setNegativeButton("Disable notifications", (dialogInterface, i) -> {
							SharedPreferences.Editor joe = pref.edit();
							joe.putBoolean("reminder_enabled", false);
							joe.commit();
						})
						.setPositiveButton("Grant", (dialogInterface, i) -> {
							ActivityCompat.requestPermissions((Activity) ctx, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0x31337);
						})
						.setCancelable(false)
						.show();
				} else {
					Log.d("perm", "ask?!");
					ActivityCompat.requestPermissions((Activity) ctx, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0x31337);
				}
			}
		}
		// From 33+ I'll rely on USE_EXACT_ALARM which is auto-granted but may trigger Play Store review. The UX for SCHEDULE_EXACT_ALARM is pretty meh.
		// I'm willing to take that risk, Giggity's pretty much a calendaring app, and it uses exact alarms for no other purpose than timely pre-event notifications.
		if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT < 33) {
			AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
			if (!am.canScheduleExactAlarms()) {
				ret = false;
				new AlertDialog.Builder(ctx)
					.setMessage("Giggity seems to be missing the SCHEDULE_EXACT_ALARM permission, which prevents it from sending event notifications at the right time.")
					.setTitle("Missing alarm permission")
					.setNegativeButton("Disable notifications", (dialogInterface, i) -> {
						SharedPreferences.Editor joe = pref.edit();
						joe.putBoolean("reminder_enabled", false);
						joe.commit();
					})
					.setPositiveButton("Grant", (dialogInterface, i) -> ctx.startActivity(new Intent(
							android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
							Uri.parse("package:" + ctx.getPackageName()))))
					.setCancelable(false)
					.show();
			}
		}
		return ret;
	}

	public static void zxingError(final Activity ctx) {
		new AlertDialog.Builder(ctx)
				.setMessage("This (deprecated) functionality depends on the ZXing Barcode scanner and will go away soon. Try ggt.gaa.st deeplinks instead.")
				.setTitle("Error")
				.setNegativeButton("Never mind", null)
				.setNeutralButton("ggt.gaa.st", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Wilm0r/giggity#deeplinking-into-giggity"));
						ctx.startActivity(intent);
					}
				})
				.setPositiveButton("Install", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						// Deeplink into fdroid only since for whatever tedious stupid reason the app
						// is visible but not installable on the Play Store anymore. But Giggity and
						// FDroid have a pretty strong overlap in users so I guess we're ok. :-)
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/en/packages/com.google.zxing.client.android/"));
						ctx.startActivity(intent);
					}
				})
				.show();
	}
}
