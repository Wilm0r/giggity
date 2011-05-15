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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;

public class Giggity extends Application {
	Db db;
	Db.Connection dbc;
	
	HashMap<String,Schedule> scheduleCache;
	Schedule lastSchedule;
	
	TreeSet<Schedule.Item> remindItems;
	
	@Override
    public void onCreate() {
    	super.onCreate();
    	db = new Db(this);
    	
    	scheduleCache = new HashMap<String,Schedule>();
    	remindItems = new TreeSet<Schedule.Item>();
    	
    	PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
    	
    	/* This makes me sad: Most schedule file formats use timezone-unaware times.
    	 * And Java's Date objects are timezone aware. The result is that if you load
    	 * a file and then change the timezone on your phone, Giggity will show the
    	 * wrong times. The easiest fix for now is to just reload everything.. */
    	registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				HashSet<String> urls = new HashSet<String>();
				for (Schedule sched : scheduleCache.values()) {
					urls.add(sched.getUrl());
					sched.commit();
					sched.sleep();
				}
				
				scheduleCache.clear();
				lastSchedule = null;
				/* Disabled for now, database initialisation issue. (Crashes on db writes.)
				 * This means alarms are still wrong but the user will most likely reload
				 * before that becomes a problem.
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
    
    public Schedule getSchedule(String url, boolean offline) throws Exception {
    	if (!hasSchedule(url)) {
    		Schedule sched = new Schedule(this);
			if (offline) {
				/* Reminder shouldn't touch the network unless really necessary. */
				try {
					sched.loadSchedule(url, false);
				} catch (Exception e) {
					return getSchedule(url, false);
				}
			} else
				sched.loadSchedule(url, true);
	   		scheduleCache.put(url, sched);
    	}
    	return (lastSchedule = scheduleCache.get(url));
    }
    
    public Schedule getLastSchedule() {
    	/* Ugly, but I need it for search, since it starts a new activity with no state.. :-/ */
    	return lastSchedule;
    }
    
    public void updateRemind(Schedule.Item item) {
    	if (item.getRemind()) {
    		if (item.compareTo(new Date()) < 0)
    			remindItems.add(item);
    	} else
        	remindItems.remove(item);
    	
    	Reminder.poke(this, item);
    }
    
    protected AbstractSet<Schedule.Item> getRemindItems() {
    	return remindItems;
    }
}
