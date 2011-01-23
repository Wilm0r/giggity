package net.gaast.giggity;

import java.util.HashMap;

import android.app.Application;
import android.preference.PreferenceManager;

public class Giggity extends Application {
	Db db;
	Db.Connection dbc;
	
	HashMap<String,Schedule> scheduleCache;
	
    public void onCreate() {
    	super.onCreate();
    	db = new Db(this);
    	
    	scheduleCache = new HashMap<String,Schedule>();
    	
    	PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
    }
    
    public Db.Connection getDb() {
    	return db.getConnection();
    }
    
    public boolean hasSchedule(String url) {
    	return scheduleCache.containsKey(url);
    }
    
    public void flushSchedule(String url) {
    	scheduleCache.remove(url);
    }
    
    public Schedule getSchedule(String url) throws Exception {
    	if (!hasSchedule(url)) {
    		Schedule sched = new Schedule(this);
    		sched.loadSchedule(url);
    		scheduleCache.put(url, sched);
    	}
    	return scheduleCache.get(url);
    }
}