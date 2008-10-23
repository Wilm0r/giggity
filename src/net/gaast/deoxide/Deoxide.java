package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.ListIterator;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

public class Deoxide extends Activity {
    /** Called when the activity is first created. */
    
	public static final int HourHeight = 16;
	public static final int HourWidth = 72;
	public static final int TentHeight = 48;
	public static final int TentWidth = 48;
	
	LinearLayout clockrows; /* Clock->Schedule->Clock (V) */
	LinearLayout schedcols; /* Tent headers->Schedule (H) */ 
	
    LinearLayout schedcont;
    LinearLayout schedrows[];
    SimpleScroller scrollert;
    ScheduleData sched;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	int x, y;
    	Calendar cal;
    	LinkedList<ScheduleDataLine> tents;
    	ListIterator<ScheduleDataLine> tenti;
    	BlockScheduleLine line;
    	BlockScheduleElement cell;
    	
    	clockrows = new LinearLayout(this);
    	clockrows.setOrientation(LinearLayout.VERTICAL);
    	
    	super.onCreate(savedInstanceState);
        setContentView(clockrows);

    	sched = new ScheduleData("http://wilmer.gaast.net/deoxide/test.xml");
    	setTitle("Deoxide: " + sched.getTitle());
    	
    	schedrows = new LinearLayout[32];
    	schedcont = new LinearLayout(this);
    	schedcont.setOrientation(LinearLayout.VERTICAL);
    	schedcont.setBackgroundColor(0xFFFFFFFF);
    	
    	/* Time to generate the "clock" on the first row. */
    	line = new BlockScheduleLine(this);

		cal = Calendar.getInstance();
		cal.setTime(sched.getFirstTime());
		cal.set(Calendar.MINUTE, 0);
		clockrows.addView(new BlockScheduleClock(this, cal));
		
		y = 0;
		tents = sched.getTents();
		tenti = tents.listIterator();
    	while (tenti.hasNext()) {
    		ListIterator<ScheduleDataItem> gigi;
    		ScheduleDataLine tent = tenti.next();
    		
    		line = new BlockScheduleLine(this); 

    		/* Tent name on the first column. */
			cell = new BlockScheduleElement(this);
			cell.setWidth(TentWidth);
			cell.setText(tent.getTitle());
			if ((++y & 1) > 0)
				cell.setBackgroundColor(0xFF000000);
			else
				cell.setBackgroundColor(0xFF3F3F3F);
			line.addView(cell);

    		cal = Calendar.getInstance();
    		cal.setTime(sched.getFirstTime());
    		cal.add(Calendar.MINUTE, -15);

        	x = 0;
			gigi = tent.getItems().listIterator();
			while (gigi.hasNext()) {
				ScheduleDataItem gig = gigi.next();
				int gap;
				
				gap = (int) ((gig.getStartTime().getTime() -
						      cal.getTime().getTime()) / 60000);
				
				cell = new BlockScheduleElement(this);
				cell.setWidth(HourWidth * gap / 60);
				cell.setBackgroundColor(0xFFFFFFFF);
				cell.setTextColor(0xFF000000);
				line.addView(cell);
				cal.add(Calendar.MINUTE, gap);
				
				gap = (int) ((gig.getEndTime().getTime() -
					          cal.getTime().getTime()) / 60000);
				
				cell = new BlockScheduleElement(this);
				cell.setWidth(HourWidth * gap / 60);
				if ((++x & 1) > 0 )
					cell.setBackgroundColor(0xFF000000);
				else
					cell.setBackgroundColor(0xFF3F3F3F);
				cell.setTextColor(0xFFFFFFFF);
				cell.setText(gig.getTitle());
				cell.setItem(gig);
				line.addView(cell);

				cal.add(Calendar.MINUTE, gap);
			}
    		schedcont.addView(line);
    	}

    	scrollert = new SimpleScroller(this, SimpleScroller.HORIZONTAL | SimpleScroller.VERTICAL);
        scrollert.addView(schedcont);
    	clockrows.addView(scrollert);
    }
}