package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

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
    	Calendar cal;
    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");
    	
    	setTitle("Deoxide: Lowlands 2008 zaterdag 15 aug");
    	sched = new ScheduleData("http://wilmer.gaast.net/deoxide/test.xml");
    	
    	schedrows = new LinearLayout[32];
    	schedcont = new LinearLayout(this);
    	scrollert = new ScrollView(this);
        scrollert.addView(schedcont);
    	schedcont.setOrientation(LinearLayout.VERTICAL);
    	for (y = 0; y <= sched.getTents().length; y ++) {
    		LinearLayout row = new LinearLayout(this); 
    		schedrows[y] = row;
    		ScheduleElement cell;

    		/* Headers on the first column. */
    		if (y == 0) {
    			cell = new ScheduleElement(this);
    			cell.setWidth(TentWidth);
    			cell.setText("Lowlands 2008 vrijdag");
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

    		cal = Calendar.getInstance();
    		cal.set(2008, 7, 15, 13, 0, 0);
    		cal.set(Calendar.MILLISECOND, 0);
        	df.setCalendar(cal);
			
			if (y == 0) {
				/* Time headers on the first row. */
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
    		} else {
    			ScheduleDataItem gigs[] = sched.getTentSchedule(sched.getTents()[y-1]);
    			int i;
    			
    			for (i = 0; i < gigs.length; i ++) {
    				int gap, gap2;
    				String foo = cal.toString();
    				
    				gap = (int) ((gigs[i].getStartTime().getTime() -
    						      cal.getTime().getTime()) / 60000);
    				
    				cell = new ScheduleElement(this);
    				cell.setWidth(HourWidth * gap / 60);
    				cell.setBackgroundColor(0xFFFFFFFF);
    				cell.setTextColor(0xFF000000);
    				cell.setText("" + gap);
    				row.addView(cell);
    				cal.add(Calendar.MINUTE, gap);
    				
    				gap2 = gap;
    				gap = (int) ((gigs[i].getEndTime().getTime() -
						          cal.getTime().getTime()) / 60000);
    				
    				cell = new ScheduleElement(this);
    				cell.setWidth(HourWidth * gap / 60);
    				cell.setBackgroundColor(0xFF000000);
    				cell.setTextColor(0xFFFFFFFF);
    				cell.setText(gigs[i].getTitle());
    				cell.setItem(gigs[i]);
    				row.addView(cell);
    				cal.add(Calendar.MINUTE, gap);
    			}
    		}
    		schedcont.addView(row);
    	}
        super.onCreate(savedInstanceState);
        setContentView(scrollert);
    }
}