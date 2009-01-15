package net.gaast.deoxide;

import android.app.Application;

public class Deoxide extends Application {
	public static final int HourHeight = 16;
	public static final int HourWidth = 72;
	public static final int TentHeight = 48;
	public static final int TentWidth = 32;
	
	DeoxideDb db;
	DeoxideDb.Connection dbc;
	
    public void onCreate() {
    	super.onCreate();

    	//

    	db = new DeoxideDb(this);
    	dbc = db.getConnection();
    	//db.setSchedule(sched);
    	
        //setContentView(new BlockSchedule(this));
    }
    
    public void onPause() {
    	// TODO: Proper database cleanup!
    	//dbc.close();
    }
    
    public DeoxideDb getDb() {
    	return db;
    }
}