package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class Deoxide extends Activity {
    /** Called when the activity is first created. */
    
	public static final int TentHeight = 32;
	public static final int TentWidth = 48;
	public static final int HourWidth = 72;
	
    LinearLayout schedcont;
    LinearLayout schedrows[];
    ScrollView scrollert;
    ScheduleData sched;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	int x, y;
    	Calendar cal = Calendar.getInstance();
    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");
    	
    	sched = new ScheduleData();
    	
    	cal.set(Calendar.HOUR_OF_DAY, 11);
    	cal.set(Calendar.MINUTE, 0);
    	cal.set(Calendar.SECOND, 0);
    	df.setCalendar(cal);

    	schedrows = new LinearLayout[32];
    	schedcont = new LinearLayout(this);
    	scrollert = new ScrollView(this);
        scrollert.addView(schedcont);
    	schedcont.setOrientation(LinearLayout.VERTICAL);
    	for (y = 0; y <= sched.getTents().length; y ++) {
    		LinearLayout row = new LinearLayout(this); 
    		schedrows[y] = row;
    		TextView cell;
    		if (y == 0) {
    			cell = new ScheduleElement(this);
    			cell.setWidth(TentWidth);
    			cell.setText("Tent/Time:");
				cell.setBackgroundColor(0xFF3F3F3F);
    		} else {
    			cell = new ScheduleElement(this);
    			cell.setWidth(TentWidth);
    			cell.setText(sched.getTents()[y-1]);
    			if ((y & 1) != 0) {
    				cell.setBackgroundColor(0xFF000000);
    			} else {
    				cell.setBackgroundColor(0xFF3F3F3F);
    			}
    		}
			row.addView(cell);
    		if (y == 0) {
    			for (x = 0; x < 24; x ++) {
	    			cell = new ScheduleElement(this);
	    			
	    			cell.setText(df.format(cal.getTime()));
	    			cell.setWidth(HourWidth / 2);
	    			if ((x & 1) == 0) {
	    				cell.setBackgroundColor(0xFF000000);
	    			} else {
	    				cell.setBackgroundColor(0xFF3F3F3F);
	    			}
	    			row.addView(cell);
	
	    			cal.add(Calendar.MINUTE, 30);
	    		}
    		}
    		schedcont.addView(row);
    	}
        super.onCreate(savedInstanceState);
        setContentView(scrollert);
    }
}