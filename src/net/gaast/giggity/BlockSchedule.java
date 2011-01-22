package net.gaast.giggity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsoluteLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BlockSchedule extends LinearLayout implements SimpleScroller.Listener, ShuffleLayout.Listener {
	Giggity app;
    Schedule sched;
    
    Colours c;

    /* This object is pretty messy. :-/ It contains the
     * following widgets: */
    
    /* Clocks at the top and bottom */
    Clock topClock;
    Clock bottomClock;
    
    /* mainTable is the middle part of the screen */
    LinearLayout mainTable;
    /* Separate this to keep them on screen when scrolling */
    ShuffleLayout tentHeaders;
    SimpleScroller tentHeadersScr;

    /* schedCont will contain all the actual data rows,
     * we'll get scrolling by stuffing it inside schedContScr. */
    ShuffleLayout schedCont;
    SimpleScroller schedContScr;

    SharedPreferences pref;
    
	private static final int HourHeight = 16;
	private static final int TentHeight = 48;
	private static final int TentWidth = 32;
	
	private int HourWidth;
	
	BlockSchedule(Activity ctx, Schedule sched_) {
		super(ctx);
		app = (Giggity) ctx.getApplication();
    	sched = sched_;
    	pref = PreferenceManager.getDefaultSharedPreferences(app);

    	/* Not working yet. :-( */
    	Class[] styles = getClass().getDeclaredClasses();
    	int i;
    	c = new Light();
    	for (i = 0; i < styles.length; i ++) {
			Log.d("style", pref.getString("block_schedule_style", "") + " " + styles[i].getSimpleName());
    		if (styles[i].getSuperclass() == Colours.class &&
    			styles[i].getSimpleName().equals(pref.getString("block_schedule_style", ""))) {
    			try {
					c = (Colours) styles[i].newInstance();
				} catch (IllegalAccessException e) {
				} catch (InstantiationException e) {
				}
			}
    	}
    	
    	int x, y;
    	Calendar base, cal, end;
    	LinkedList<Schedule.Line> tents;
    	ListIterator<Schedule.Line> tenti;
    	Element cell;
    	
    	setOrientation(LinearLayout.VERTICAL);
    	
        HourWidth = Integer.parseInt(pref.getString("block_schedule_element_size", "72"));
    	
    	schedCont = new ShuffleLayout(ctx, ShuffleLayout.DISABLE_DRAG_SHUFFLE);
    	schedCont.setBackgroundColor(c.background);
    	schedCont.setMinimumHeight(sched.getTents().size());
    	
		base = Calendar.getInstance();
		base.setTime(sched.getFirstTime());
		base.set(Calendar.MINUTE, 0);
		
		end = Calendar.getInstance();
		end.setTime(sched.getLastTime());		

		topClock = new Clock(ctx, base, end);
		topClock.setScrollEventListener(this);
		addView(topClock);
		
		tentHeaders = new ShuffleLayout(ctx, 0);
		tentHeaders.setShuffleEventListener(this);
		tentHeadersScr = new SimpleScroller(ctx, SimpleScroller.VERTICAL |
				(pref.getBoolean("block_schedule_tent_shuffling", true) ?
						SimpleScroller.DISABLE_DRAG_SCROLL : 0));
    	tentHeadersScr.addView(tentHeaders);
        tentHeadersScr.setScrollEventListener(this);
        
		y = 0;
		tents = sched.getTents();
		tenti = tents.listIterator();
    	while (tenti.hasNext()) {
    		Iterator<Schedule.Item> gigi;
    		Schedule.Line tent = tenti.next();
    		AbsoluteLayout line;
			int posx, h, w;
    		
    		/* Tent name on the first column. */
			cell = new Element(ctx);
			cell.setWidth(TentWidth);
			cell.setText(tent.getTitle());
			cell.setBackgroundColor(c.tentbg[y&1]);
			cell.setTextColor(c.tentfg[y&1]);
			tentHeaders.addView(cell);

    		cal = Calendar.getInstance();
    		cal.setTime(base.getTime());
    		cal.add(Calendar.MINUTE, -15);

        	x = 0;
        	y ++;
        	h = TentHeight;
        	line = new AbsoluteLayout(ctx);
        	
			gigi = tent.getItems().iterator();
			while (gigi.hasNext()) {
				Schedule.Item gig = gigi.next();
				
				posx = (int) ((gig.getStartTime().getTime() -
				               cal.getTime().getTime()) *
				              HourWidth / 3600000);
				w    = (int) ((gig.getEndTime().getTime() -
				               gig.getStartTime().getTime()) *
				              HourWidth / 3600000);
				
				cell = new Element(ctx);
				cell.setItem(gig);
				cell.setWidth(w);
				cell.setBackgroundColor(c.itembg[((y+x)&1)]);
				cell.setTextColor(c.itemfg[((y+x)&1)]);
				x ++;
				cell.setText(gig.getTitle());
				AbsoluteLayout.LayoutParams lp = new AbsoluteLayout.LayoutParams(w, h, posx, 0);
				line.addView(cell, lp);
			}
			
			/* TODO: Grrrr, how do I get the width of topClock for here? :-/ */
			schedCont.addView(line, new LinearLayout.LayoutParams(-1, h));
    	}

    	schedContScr = new SimpleScroller(ctx, SimpleScroller.HORIZONTAL | SimpleScroller.VERTICAL);
        schedContScr.addView(schedCont);
        schedContScr.setScrollEventListener(this);

		mainTable = new LinearLayout(app);
		mainTable.addView(tentHeadersScr);
		mainTable.addView(schedContScr);
    	addView(mainTable, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
    	
		bottomClock = new Clock(ctx, base, end);
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

	@Override
	public void onSwapEvent(int top) {
		schedCont.swapChildren(top);
	}

	protected class Element extends TextView {
		int bgcolor;
		Schedule.Item item;
		Giggity app;
		
		public Element(Activity ctx) {
			super(ctx);
			setGravity(Gravity.CENTER_HORIZONTAL);
			setHeight(TentHeight);
			setTextColor(0xFFFFFFFF);
			setPadding(0, 3, 0, 0);
			setTextSize(8);
		}
		
		public void setItem(Schedule.Item item_) {
			item = item_;
			setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					EventDialog evd = new EventDialog(getContext(), item);
			    	evd.setOnDismissListener(new OnDismissListener() {
			   			public void onDismiss(DialogInterface dialog) {
			   				setBackgroundColor(bgcolor);
			   			}
			   		});
					evd.show();
				}
			});
		}
		
		public void setBackgroundColor(int color) {
			bgcolor = color;
			if (item != null && item.getRemind()) {
				super.setBackgroundColor(0xff00cf00);
			} else {
				super.setBackgroundColor(bgcolor);
			}
		}
	}
	
	protected class Clock extends SimpleScroller {
		private Element cell;
		private LinearLayout child;
		private Handler updater;
		private Calendar base;
		
		public Clock(Activity ctx, Calendar base_, Calendar end) {
			super(ctx, SimpleScroller.HORIZONTAL);

			base = new GregorianCalendar();
			base.setTime(base_.getTime());
			
			SimpleDateFormat df = new SimpleDateFormat("HH:mm");
			Calendar cal;
			
			cal = Calendar.getInstance();
			cal.setTime(base.getTime());
			
			child = new LinearLayout(ctx);
			
			cell = new Element(ctx);
			cell.setHeight(HourHeight);
			cell.setWidth(TentWidth);
			cell.setBackgroundColor(c.clockbg[1]);
			child.addView(cell);

			while(true) {
				cell = new Element(ctx);
				
				cell.setText(df.format(cal.getTime()));
				cell.setHeight(HourHeight);
				cell.setWidth(HourWidth / 2);
				child.addView(cell);

				if (cal.after(end))
					break;
				
				cal.add(Calendar.MINUTE, 30);
			}
			
			updater = new Handler();
			//update();
			updater.postAtFrontOfQueue(updatef);
			addView(child);
		}
		
		private Runnable updatef = new Runnable() {
			@Override
			public void run() {
				update();
				Log.d("b", "running updater");
			}
		};
		
		/* Mark the current 30m period in the clock green. */
		public void update() {
			int i;
			Calendar cal = new GregorianCalendar();
			cal.setTime(base.getTime());
			Log.d("update", "running 1");
			for (i = 1; i < child.getChildCount(); i ++) {
				Element cell = (Element) child.getChildAt(i);
				long diff = System.currentTimeMillis() - cal.getTimeInMillis(); 
				if (diff >= 0 && diff < 1800000) {
					cell.setBackgroundColor(0xFF00CF00);
					cell.setTextColor(c.clockfg[1]);
					//updater.postAtTime(updatef, cal.getTimeInMillis() + 1800000);
				} else if (cal.get(Calendar.MINUTE) == 0) {
					cell.setBackgroundColor(c.clockbg[0]);
					cell.setTextColor(c.clockfg[0]);
				} else {
					cell.setBackgroundColor(c.clockbg[1]);
					cell.setTextColor(c.clockfg[1]);
				}

				cal.add(Calendar.MINUTE, 30);
			}
		}
	}
	
	static private abstract class Colours {
		public int background;
		public int clockbg[], clockfg[];
		public int itembg[], itemfg[];
		public int tentbg[], tentfg[];
		
		public Colours() {
			clockbg = new int[2];
			clockfg = new int[2];
			itembg = new int[2];
			itemfg = new int[2];
			tentbg = new int[2];
			tentfg = new int[2];
		}
	}

	@SuppressWarnings("unused")
	static private class BlackWhite extends Colours {
		public BlackWhite() {
			super();
			background = 0xFFFFFFFF;
			clockbg[0] = 0xFF3F3F3F;
			clockbg[1] = 0xFF000000;
			itembg[0] = 0xFF3F3F3F;
			itembg[1] = 0xFF000000;
			tentbg[0] = 0xFF3F3F3F;
			tentbg[1] = 0xFF000000;
			clockfg[0] = clockfg[1] = itemfg[0] = itemfg[1] =
				tentfg[0] = tentfg[1] = 0xFFFFFFFF;
		}
	}

	static private class Light extends Colours {
		public Light() {
			super();
			background = 0xFFF9FCDA;
			clockbg[0] = 0xFFF5FC49;
			clockbg[1] = 0xFFF8FC9C;
			itembg[0] = 0xFFE0EFFC;
			itembg[1] = 0xFFC2E1FC;
			tentbg[0] = 0xFFFAFCB8;
			tentbg[1] = 0xFFF8FC9C;
			clockfg[0] = clockfg[1] = itemfg[0] = itemfg[1] =
				tentfg[0] = tentfg[1] = 0xFF000000;
			itemfg[0] = itemfg[1] = 0xFF000000;
		}
	}
}
