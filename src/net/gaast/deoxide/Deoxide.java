package net.gaast.deoxide;

import java.util.HashMap;

import net.gaast.deoxide.Schedule.LoadDataException;
import net.gaast.deoxide.Schedule.LoadNetworkException;
import android.app.Application;
import android.preference.PreferenceManager;

public class Deoxide extends Application {
	DeoxideDb db;
	DeoxideDb.Connection dbc;
	
	HashMap<String,Schedule> scheduleCache;
	
    public void onCreate() {
    	super.onCreate();
    	db = new DeoxideDb(this);
    	
    	scheduleCache = new HashMap<String,Schedule>();
    	
    	PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
    }
    
    public DeoxideDb.Connection getDb() {
    	return db.getConnection();
    }
    
    public boolean hasSchedule(String url) {
    	return scheduleCache.containsKey(url);
    }
    
    public Schedule getSchedule(String url) throws LoadNetworkException, LoadDataException {
    	if (!hasSchedule(url)) {
    		Schedule sched = new Schedule(this);
    		sched.loadSchedule(url);
    		scheduleCache.put(url, sched);
    	}
    	return scheduleCache.get(url);
    }
}