package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.ListIterator;

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
    	LinkedList<ScheduleDataLine> tents;
    	ListIterator<ScheduleDataLine> tenti;
    	ScheduleLine line;
    	ScheduleElement cell;
    	
    	sched = new ScheduleData("http://wilmer.gaast.net/deoxide/test.xml");
    	setTitle("Deoxide: " + sched.getTitle());
    	
    	schedrows = new LinearLayout[32];
    	schedcont = new LinearLayout(this);
    	scrollert = new ScrollView(this);
        scrollert.addView(schedcont);
    	schedcont.setOrientation(LinearLayout.VERTICAL);
    	
    	/* Time to generate the "clock" on the first row. */
    	line = new ScheduleLine(this);
		cell = new ScheduleElement(this);
		cell.setWidth(TentWidth);
		cell.setText("Tent/Time:");
		cell.setBackgroundColor(0xFF3F3F3F);
		line.addView(cell);

		cal = Calendar.getInstance();
		cal.setTime(sched.getFirstTime());
		for (x = 0; x < 24; x ++) {
			cell = new ScheduleElement(this);
			
			cell.setText(df.format(cal.getTime()));
			cell.setWidth(HourWidth / 2);
			if ((x & 1) == 0) {
				cell.setBackgroundColor(0xFF000000);
			} else {
				cell.setBackgroundColor(0xFF3F3F3F);
			}
			line.addView(cell);

			cal.add(Calendar.MINUTE, 30);
		}
		schedcont.addView(line);
		
		tents = sched.getTents();
		tenti = tents.listIterator();
    	while (tenti.hasNext()) {
    		ListIterator<ScheduleDataItem> gigi;
    		ScheduleDataLine tent = tenti.next();
    		
    		line = new ScheduleLine(this); 

    		/* Tent name on the first column. */
			cell = new ScheduleElement(this);
			cell.setWidth(TentWidth);
			cell.setText(tent.getTitle());
			if (true) { //(y & 1) != 0) {
				cell.setBackgroundColor(0xFF000000);
			} else {
				cell.setBackgroundColor(0xFF3F3F3F);
			}
			line.addView(cell);

    		cal = Calendar.getInstance();
    		cal.setTime(sched.getFirstTime());
    		cal.add(Calendar.MINUTE, -15);
        	df.setCalendar(cal);
			
			gigi = tent.getItems().listIterator();
			while (gigi.hasNext()) {
				ScheduleDataItem gig = gigi.next();
				int gap;
				String foo = cal.toString();
				
				gap = (int) ((gig.getStartTime().getTime() -
						      cal.getTime().getTime()) / 60000);
				
				cell = new ScheduleElement(this);
				cell.setWidth(HourWidth * gap / 60);
				cell.setBackgroundColor(0xFFFFFFFF);
				cell.setTextColor(0xFF000000);
				cell.setText("" + gap);
				line.addView(cell);
				cal.add(Calendar.MINUTE, gap);
				
				gap = (int) ((gig.getEndTime().getTime() -
					          cal.getTime().getTime()) / 60000);
				
				cell = new ScheduleElement(this);
				cell.setWidth(HourWidth * gap / 60);
				cell.setBackgroundColor(0xFF000000);
				cell.setTextColor(0xFFFFFFFF);
				cell.setText(gig.getTitle());
				cell.setItem(gig);
				line.addView(cell);
				cal.add(Calendar.MINUTE, gap);
			}
    		schedcont.addView(line);
    	}
        super.onCreate(savedInstanceState);
        setContentView(scrollert);
    }
}