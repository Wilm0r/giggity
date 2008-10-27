package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.widget.LinearLayout;

public class BlockScheduleClock extends SimpleScroller {
	private BlockScheduleElement cell;
	private LinearLayout child;
	
	public BlockScheduleClock(Activity ctx, Calendar base, Calendar end) {
		super(ctx, SimpleScroller.HORIZONTAL);

		SimpleDateFormat df = new SimpleDateFormat("HH:mm");
		Calendar cal;
		
		cal = Calendar.getInstance();
		cal.setTime(base.getTime());
		
		child = new LinearLayout(ctx);
		
		cell = new BlockScheduleElement(ctx);
		// cell.setText("Tent/Time:");
		cell.setHeight(Deoxide.HourHeight);
		cell.setWidth(Deoxide.TentWidth);
		cell.setBackgroundColor(0xFF3F3F3F);
		child.addView(cell);

		while(true) {
			cell = new BlockScheduleElement(ctx);
			
			cell.setText(df.format(cal.getTime()));
			cell.setHeight(Deoxide.HourHeight);
			cell.setWidth(Deoxide.HourWidth / 2);
			if (cal.get(Calendar.MINUTE) == 0) {
				cell.setBackgroundColor(0xFF000000);
			} else {
				cell.setBackgroundColor(0xFF3F3F3F);
			}
			child.addView(cell);

			if (cal.after(end))
				break;
			
			cal.add(Calendar.MINUTE, 30);
		}
		
		addView(child);
	}
}
