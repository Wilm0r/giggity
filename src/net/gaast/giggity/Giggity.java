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
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

public class Giggity extends Application {
	Db db;
	Db.Connection dbc;
	
	HashMap<String,Schedule> scheduleCache;
	Schedule lastSchedule;
	
	TreeSet<Schedule.Item> remindItems;
	//Service reminder;
	
    public void onCreate() {
    	super.onCreate();
    	db = new Db(this);
    	
    	scheduleCache = new HashMap<String,Schedule>();
    	remindItems = new TreeSet<Schedule.Item>();
    	
    	PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
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
    
    public Schedule getSchedule(String url) throws Exception {
    	if (!hasSchedule(url)) {
    		Schedule sched = new Schedule(this);
    		sched.loadSchedule(url);
    		scheduleCache.put(url, sched);
    	}
    	return (lastSchedule = scheduleCache.get(url));
    }
    
    public Schedule getLastSchedule() {
    	/* Ugly, but I need it for search, since it starts a new activity with no state.. :-/ */
    	return lastSchedule;
    }
    
    public void updateRemind(Schedule.Item item) {
    	if (item.getRemind())
    		remindItems.add(item);
    	else
        	remindItems.remove(item);
    	
    	/* Start the service in case it's not running yet, and set an alarm 
    	 * in a second to have it go through all reminders. */
    	startService(new Intent(this, Reminder.class));
		AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000,
		       PendingIntent.getBroadcast(this, 0, new Intent(Reminder.ACTION), 0));
    }
    
    protected AbstractSet<Schedule.Item> getRemindItems() {
    	return remindItems;
    }
}
