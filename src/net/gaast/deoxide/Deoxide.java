package net.gaast.deoxide;

import android.app.Activity;
import android.os.Bundle;

public class Deoxide extends Activity {
    /** Called when the activity is first created. */
    
	public static final int HourHeight = 16;
	public static final int HourWidth = 72;
	public static final int TentHeight = 48;
	public static final int TentWidth = 48;
	
	ScheduleData sched;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	sched = new ScheduleData("http://wilmer.gaast.net/deoxide/test.xml");
    	setTitle("Deoxide: " + sched.getTitle());
    	
        setContentView(new BlockScheduleViewer(this, sched));
    }
}