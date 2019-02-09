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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

/* OK so I'm not using ISO8601 ... but at least it's not middle-endian. And there's no portable date
   range format which is my real problem. So just silence that lint. */
@SuppressLint({"SimpleDateFormat"})
public class Giggity extends Application {
	private Db db;
	
	HashMap<String,ScheduleUI> scheduleCache;  // url→ScheduleUI
	@Deprecated
	ScheduleUI lastSchedule;
	
	TreeSet<Schedule.Item> remindItems;
	
	@Override
	public void onCreate() {
		super.onCreate();
		db = new Db(this);
		
		scheduleCache = new HashMap<>();
		remindItems = new TreeSet<>();
		
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
				lastSchedule = null;

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
	}
	
	public Db.Connection getDb() {
		return db.getConnection();
	}
	
	public boolean hasSchedule(String url) {
		return scheduleCache.containsKey(url);
	}
	
	public void flushSchedules() {
		scheduleCache.clear();
		/* I *think* this one's safe because alarms are still set and once the first rings, the
		   schedule will get reloaded. May not go as well if the user is observing multiple
		   schedules ATM but who does that..
		 */
		remindItems.clear();
	}

	public void flushSchedule(String url) {
		if (hasSchedule(url)) {
			Schedule sched = scheduleCache.get(url);
			for (Schedule.Item item : new ArrayList<Schedule.Item>(remindItems)) {
				if (item.getSchedule() == sched)
					remindItems.remove(item);
			}
		}
		scheduleCache.remove(url);
	}
	
	public ScheduleUI getSchedule(String url, Fetcher.Source source, Handler progress) throws Exception {
		if (!hasSchedule(url)) {
			scheduleCache.put(url, ScheduleUI.loadSchedule(this, url, source, progress));
		}
		return (lastSchedule = scheduleCache.get(url));
	}

	public ScheduleUI getSchedule(String url, Fetcher.Source source) throws Exception {
		return getSchedule(url, source, null);
	}

	@Deprecated
	public ScheduleUI getLastSchedule() {
		/* Ugly, but I need it for search, since it starts a new activity with no state.. :-/ */
		return lastSchedule;
	}
	
	public void updateRemind(Schedule.Item item) {
		if (item.getRemind()) {
			if (item.compareTo(ZonedDateTime.now()) < 0)
				remindItems.add(item);
		} else
			remindItems.remove(item);
		
		Reminder.poke(this, item);
		Widget.updateWidget(this);
	}
	
	protected AbstractSet<Schedule.Item> getRemindItems() {
		return remindItems;
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
			ret = "" + start.getDate() + "-" + new SimpleDateFormat("d MMMM").format(end);
		else
			ret = new SimpleDateFormat("d MMMM").format(start) + "-" + new SimpleDateFormat("d MMMM").format(end);
		return ret + " " + (1900 + end.getYear());
	}

	public static String dateRange(ZonedDateTime start, ZonedDateTime end) {
		String ret = "";
		if (start.getDayOfMonth() == end.getDayOfMonth() && start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = DateTimeFormatter.ofPattern("d MMMM").format(end);
		else if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = "" + start.getDayOfMonth() + "-" + DateTimeFormatter.ofPattern("d MMMM").format(end);
		else
			ret = DateTimeFormatter.ofPattern("d MMMM").format(start) + "-" + DateTimeFormatter.ofPattern("d MMMM").format(end);
		return ret + " " + (1900 + end.getYear());
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
}
