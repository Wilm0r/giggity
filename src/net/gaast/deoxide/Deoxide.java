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
	
    LinearLayout sched;
    LinearLayout schedrows[];
    ScrollView scrollert;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	int x, y;
    	Calendar cal = Calendar.getInstance();
    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");
    	
    	cal.set(Calendar.HOUR_OF_DAY, 11);
    	cal.set(Calendar.MINUTE, 0);
    	cal.set(Calendar.SECOND, 0);
    	df.setCalendar(cal);

    	schedrows = new LinearLayout[32];
    	sched = new LinearLayout(this);
    	scrollert = new ScrollView(this);
        scrollert.addView(sched);
    	sched.setOrientation(LinearLayout.VERTICAL);
    	for (y = 0; y < 16; y ++) {
    		LinearLayout row = new LinearLayout(this); 
    		schedrows[y] = row;
    		if (y == 0) {
    			TextView cell = new ScheduleElement(this);
    			cell.setWidth(TentWidth);
    			cell.setText("Tent/Time:");
				cell.setBackgroundColor(0xFF3F3F3F);
    			row.addView(cell);
    		} else {
    			TextView cell = new ScheduleElement(this);
    			cell.setWidth(TentWidth);
    			cell.setText("Tent " + y);
    			if ((y & 1) != 0) {
    				cell.setBackgroundColor(0xFF000000);
    			} else {
    				cell.setBackgroundColor(0xFF3F3F3F);
    			}
    			row.addView(cell);
    		}
    		if (y == 0) for (x = 0; x < 24; x ++) {
    			TextView cell = new ScheduleElement(this);
    			
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
    		sched.addView(row);
    	}
        super.onCreate(savedInstanceState);
        setContentView(scrollert);
    }
}