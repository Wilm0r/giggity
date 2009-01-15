package net.gaast.deoxide;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.ListIterator;

import android.app.Activity;
import android.widget.AbsoluteLayout;
import android.widget.LinearLayout;

public class BlockSchedule extends LinearLayout implements SimpleScrollerListener {
	Deoxide app;
    ScheduleData sched;
    DeoxideDb db;

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

    /* schedCont will contain all the actual data rows,
     * we'll get scrolling by stuffing it inside schedContScr. */
    AbsoluteLayout schedCont;
    SimpleScroller schedContScr;

	BlockSchedule(Activity ctx, ScheduleData sched_) {
		super(ctx);
		app = (Deoxide) ctx.getApplication();
    	sched = sched_;
    	db = app.getDb();
    	
    	int x, y;
    	Calendar base, cal, end;
    	LinkedList<ScheduleDataLine> tents;
    	ListIterator<ScheduleDataLine> tenti;
    	BlockScheduleElement cell;
    	
    	setOrientation(LinearLayout.VERTICAL);
    	
    	schedCont = new AbsoluteLayout(app);
    	schedCont.setBackgroundColor(0xFFFFFFFF);
    	schedCont.setMinimumHeight(sched.getTents().size());
    	
		base = Calendar.getInstance();
		base.setTime(sched.getFirstTime());
		base.set(Calendar.MINUTE, 0);
		
		end = Calendar.getInstance();
		end.setTime(sched.getLastTime());		

		topClock = new BlockScheduleClock(ctx, base, end);
		topClock.setScrollEventListener(this);
		addView(topClock);
		
		tentHeaders = new LinearLayout(app);
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
			int posx, posy, h, w;
    		
    		/* Tent name on the first column. */
			cell = new BlockScheduleElement(ctx);
			cell.setWidth(Deoxide.TentWidth);
			cell.setText(tent.getTitle());
			if ((y & 1) == 0)
				cell.setBackgroundColor(0xFF000000);
			else
				cell.setBackgroundColor(0xFF3F3F3F);
			tentHeaders.addView(cell);

    		cal = Calendar.getInstance();
    		cal.setTime(base.getTime());
    		cal.add(Calendar.MINUTE, -15);

        	x = 0;
        	h = Deoxide.TentHeight;
        	posy = (y++) * h;
			gigi = tent.getItems().listIterator();
			while (gigi.hasNext()) {
				ScheduleDataItem gig = gigi.next();
				
				posx = (int) ((gig.getStartTime().getTime() -
				               cal.getTime().getTime()) *
				              Deoxide.HourWidth / 3600000);
				w    = (int) ((gig.getEndTime().getTime() -
				               gig.getStartTime().getTime()) *
				              Deoxide.HourWidth / 3600000);
				
				cell = new BlockScheduleElement(ctx);
				cell.setWidth(w);
				if ((++x & 1) > 0 )
					cell.setBackgroundColor(0xFF000000);
				else
					cell.setBackgroundColor(0xFF3F3F3F);
				cell.setTextColor(0xFFFFFFFF);
				cell.setText(gig.getTitle());
				cell.setItem(gig);
				AbsoluteLayout.LayoutParams lp = new AbsoluteLayout.LayoutParams(w, h, posx, posy);
				schedCont.addView(cell, lp);
			}
    	}

    	schedContScr = new SimpleScroller(ctx, SimpleScroller.HORIZONTAL | SimpleScroller.VERTICAL);
        schedContScr.addView(schedCont);
        schedContScr.setScrollEventListener(this);

		mainTable = new LinearLayout(app);
		mainTable.addView(tentHeadersScr);
		mainTable.addView(schedContScr);
    	addView(mainTable, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
    	
		bottomClock = new BlockScheduleClock(ctx, base, end);
		bottomClock.setScrollEventListener(this);
		addView(bottomClock);
	}

	/* If the user scrolls one view, keep the others in sync. */
	public void onScrollEvent(SimpleScroller src) {
		if (src == schedContScr) {
			topClock.scrollTo(src.getScrollX(), 0);
			bottomClock.scrollTo(src.getScrollX(), 0);
			tentHeadersScr.scrollTo(0, src.getScrollY());
		} else if (src == topClock || src == bottomClock) {
			schedContScr.scrollTo(src.getScrollX(), schedContScr.getScrollY());
			if (src != topClock)
				topClock.scrollTo(src.getScrollX(), 0);
			if (src != bottomClock)
				bottomClock.scrollTo(src.getScrollX(), 0);
		} else if (src == tentHeadersScr) {
			schedContScr.scrollTo(schedContScr.getScrollX(), src.getScrollY());
		}
	}
}
