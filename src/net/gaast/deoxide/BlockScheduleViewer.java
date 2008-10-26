package net.gaast.deoxide;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.ListIterator;

import android.app.Activity;
import android.widget.LinearLayout;

public class BlockScheduleViewer extends LinearLayout implements SimpleScrollerListener {
    ScheduleData sched;

    /* This object is pretty messy. :-/ It contains the
     * following widgets: */
    
    /* Clocks at the top and bottom */
    BlockScheduleClock topClock;
    BlockScheduleClock bottomClock;
    
    /* mainTable is the middle part of the screen */
    LinearLayout mainTable;
    /* Separate this to keep them on screen when scrolling */
    LinearLayout tentHeaders;
    SimpleScroller tentHeadersScr;

    /* schedcont will contain all the actual data rows,
     * we'll get scrolling by stuffing it inside scrollert. */
    LinearLayout schedcont;
    LinearLayout schedrows[];
    SimpleScroller scrollert;

	BlockScheduleViewer(Activity ctx, ScheduleData sched_) {
		super(ctx);
    	sched = sched_;
    	
    	int x, y;
    	Calendar base, cal;
    	LinkedList<ScheduleDataLine> tents;
    	ListIterator<ScheduleDataLine> tenti;
    	BlockScheduleLine line;
    	BlockScheduleElement cell;
    	
    	setOrientation(LinearLayout.VERTICAL);
    	
    	schedrows = new LinearLayout[32];
    	schedcont = new LinearLayout(ctx);
    	schedcont.setOrientation(LinearLayout.VERTICAL);
    	schedcont.setBackgroundColor(0xFFFFFFFF);
    	
		base = Calendar.getInstance();
		base.setTime(sched.getFirstTime());
		base.set(Calendar.MINUTE, 0);

		topClock = new BlockScheduleClock(ctx, base);
		topClock.setScrollEventListener(this);
		addView(topClock);
		
		tentHeaders = new LinearLayout(ctx);
		tentHeaders.setOrientation(LinearLayout.VERTICAL);
		tentHeadersScr = new SimpleScroller(ctx, SimpleScroller.VERTICAL);
    	tentHeadersScr.addView(tentHeaders);
        tentHeadersScr.setScrollEventListener(this);
    	
		y = 0;
		tents = sched.getTents();
		tenti = tents.listIterator();
    	while (tenti.hasNext()) {
    		ListIterator<ScheduleDataItem> gigi;
    		ScheduleDataLine tent = tenti.next();
    		
    		line = new BlockScheduleLine(ctx); 

    		/* Tent name on the first column. */
			cell = new BlockScheduleElement(ctx);
			cell.setWidth(Deoxide.TentWidth);
			cell.setText(tent.getTitle());
			if ((++y & 1) > 0)
				cell.setBackgroundColor(0xFF000000);
			else
				cell.setBackgroundColor(0xFF3F3F3F);
			tentHeaders.addView(cell);

    		cal = Calendar.getInstance();
    		cal.setTime(base.getTime());
    		cal.add(Calendar.MINUTE, -15);

        	x = 0;
			gigi = tent.getItems().listIterator();
			while (gigi.hasNext()) {
				ScheduleDataItem gig = gigi.next();
				int gap;
				
				gap = (int) ((gig.getStartTime().getTime() -
						      cal.getTime().getTime()) / 60000);
				
				cell = new BlockScheduleElement(ctx);
				cell.setWidth(Deoxide.HourWidth * gap / 60);
				cell.setBackgroundColor(0xFFFFFFFF);
				cell.setTextColor(0xFF000000);
				line.addView(cell);
				cal.add(Calendar.MINUTE, gap);
				
				gap = (int) ((gig.getEndTime().getTime() -
					          cal.getTime().getTime()) / 60000);
				
				cell = new BlockScheduleElement(ctx);
				cell.setWidth(Deoxide.HourWidth * gap / 60);
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

    	scrollert = new SimpleScroller(ctx, SimpleScroller.HORIZONTAL | SimpleScroller.VERTICAL);
        scrollert.addView(schedcont);
        scrollert.setScrollEventListener(this);

		mainTable = new LinearLayout(ctx);
		mainTable.addView(tentHeadersScr);
		mainTable.addView(scrollert);
    	addView(mainTable, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
    	
		bottomClock = new BlockScheduleClock(ctx, base);
		bottomClock.setScrollEventListener(this);
		addView(bottomClock);
	}

	public void onScrollEvent(SimpleScroller src) {
		if (src == scrollert) {
			topClock.scrollTo(src.getScrollX(), 0);
			bottomClock.scrollTo(src.getScrollX(), 0);
			tentHeadersScr.scrollTo(0, src.getScrollY());
		} else if (src == topClock || src == bottomClock) {
			scrollert.scrollTo(src.getScrollX(), scrollert.getScrollY());
			if (src != topClock)
				topClock.scrollTo(src.getScrollX(), 0);
			if (src != bottomClock)
				bottomClock.scrollTo(src.getScrollX(), 0);
		} else if (src == tentHeadersScr) {
			scrollert.scrollTo(scrollert.getScrollX(), src.getScrollY());
		}
	}
}
