package net.gaast.deoxide;

import android.app.Application;
import android.os.Bundle;

public class Deoxide extends Application {
	public static final int HourHeight = 16;
	public static final int HourWidth = 72;
	public static final int TentHeight = 48;
	public static final int TentWidth = 32;
	
	DeoxideDb db;
	DeoxideDbHelper dbh;
	
    public void onCreate() {
    	super.onCreate();

    	//

    	dbh = new DeoxideDbHelper(this, "deoxide0", null, 1);
    	db = new DeoxideDb(dbh.getWritableDatabase());
    	//db.setSchedule(sched);
    	
        //setContentView(new BlockSchedule(this));
    }
    
    public void onPause() {
    	dbh.close();
    }
    
    public DeoxideDb getDb() {
    	return db;
    }
}