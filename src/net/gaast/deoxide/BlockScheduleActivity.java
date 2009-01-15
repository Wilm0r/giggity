package net.gaast.deoxide;

import android.app.Activity;
import android.os.Bundle;

public class BlockScheduleActivity extends Activity {
	ScheduleData sched;
	BlockSchedule bs;
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sched = new ScheduleData(this, "http://wilmer.gaast.net/deoxide/test.xml");
    	setTitle("Deoxide: " + sched.getTitle());
		bs = new BlockSchedule(this, sched);
		setContentView(bs);
	}
}
