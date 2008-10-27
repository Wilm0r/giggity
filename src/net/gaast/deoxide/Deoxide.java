package net.gaast.deoxide;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;

public class Deoxide extends Activity {
    /** Called when the activity is first created. */
    
	public static final int HourHeight = 16;
	public static final int HourWidth = 72;
	public static final int TentHeight = 48;
	public static final int TentWidth = 32;
	
	ScheduleData sched;
	DeoxideDb db;
	DeoxideDbHelper dbh;
	
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	sched = new ScheduleData(this, "http://wilmer.gaast.net/deoxide/test.xml");
    	setTitle("Deoxide: " + sched.getTitle());

    	dbh = new DeoxideDbHelper(this, "deoxide0", null, 1);
    	db = new DeoxideDb(dbh.getWritableDatabase());
    	
        setContentView(new BlockScheduleViewer(this));
    }
    
    public void onPause() {
    	super.onPause();
    	dbh.close();
    }
    
    public ScheduleData getSchedule() {
    	return sched;
    }
    
    public DeoxideDb getDb() {
    	return db;
    }
}