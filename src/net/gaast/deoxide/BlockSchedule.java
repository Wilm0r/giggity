package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsoluteLayout;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class BlockSchedule extends LinearLayout implements SimpleScroller.Listener, ShuffleLayout.Listener {
	Deoxide app;
    Schedule sched;

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
    AbsoluteLayout schedCont;
    SimpleScroller schedContScr;

	BlockSchedule(Activity ctx, Schedule sched_) {
		super(ctx);
		app = (Deoxide) ctx.getApplication();
    	sched = sched_;
    	
    	int x, y;
    	Calendar base, cal, end;
    	LinkedList<Schedule.Line> tents;
    	ListIterator<Schedule.Line> tenti;
    	Element cell;
    	
    	setOrientation(LinearLayout.VERTICAL);
    	
    	schedCont = new AbsoluteLayout(app);
    	schedCont.setBackgroundColor(0xFFFFFFFF);
    	schedCont.setMinimumHeight(sched.getTents().size());
    	
		base = Calendar.getInstance();
		base.setTime(sched.getFirstTime());
		base.set(Calendar.MINUTE, 0);
		
		end = Calendar.getInstance();
		end.setTime(sched.getLastTime());		

		topClock = new Clock(ctx, base, end);
		topClock.setScrollEventListener(this);
		addView(topClock);
		
		tentHeaders = new ShuffleLayout(ctx);
		tentHeaders.setShuffleEventListener(this);
		tentHeadersScr = new SimpleScroller(ctx, SimpleScroller.VERTICAL); // | SimpleScroller.DISABLE_DRAG_SCROLL);
    	tentHeadersScr.addView(tentHeaders);
        tentHeadersScr.setScrollEventListener(this);
    	
		y = 0;
		tents = sched.getTents();
		tenti = tents.listIterator();
    	while (tenti.hasNext()) {
    		Iterator<Schedule.Item> gigi;
    		Schedule.Line tent = tenti.next();
			int posx, posy, h, w;
    		
    		/* Tent name on the first column. */
			cell = new Element(ctx);
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
			gigi = tent.getItems().iterator();
			while (gigi.hasNext()) {
				Schedule.Item gig = gigi.next();
				
				posx = (int) ((gig.getStartTime().getTime() -
				               cal.getTime().getTime()) *
				              Deoxide.HourWidth / 3600000);
				w    = (int) ((gig.getEndTime().getTime() -
				               gig.getStartTime().getTime()) *
				              Deoxide.HourWidth / 3600000);
				
				cell = new Element(ctx);
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
	public void onSwapEvent(int y1, int y2) {
		int i;
		Log.d("ose1", "" + y1 + " " + y2);
		for (i = 0; i < schedCont.getChildCount(); i ++) {
			View c = schedCont.getChildAt(i);
			if (c.getTop() == y1) {
				AbsoluteLayout.LayoutParams lp;
				lp = (AbsoluteLayout.LayoutParams) c.getLayoutParams();
				lp.y = y2;
				c.setLayoutParams(lp);
			} else if (c.getTop() == y2) {
				AbsoluteLayout.LayoutParams lp;
				lp = (AbsoluteLayout.LayoutParams) c.getLayoutParams();
				lp.y = y1;
				c.setLayoutParams(lp);
			}
		}
		Log.d("ose0", "" + y1 + " " + y2);
	}

	protected class Element extends TextView implements OnClickListener {
		Schedule.Item item;
		Deoxide app;
		
		public Element(Activity ctx) {
			super(ctx);
			setGravity(Gravity.CENTER_HORIZONTAL);
			setHeight(Deoxide.TentHeight);
			setTextColor(0xFFFFFFFF);
			setPadding(0, 3, 0, 0);
			setTextSize(8);
		}
		
		public void setItem(Schedule.Item item_) {
			item = item_;
			setOnClickListener(this);
		}
		
		public void onClick(View v) {
	    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");

			LinearLayout content = new LinearLayout(getContext());
			content.setOrientation(LinearLayout.VERTICAL);

			TextView desc = new TextView(getContext());
			desc.setText(df.format(item.getStartTime().getTime()) + "-" +
		    		     df.format(item.getEndTime().getTime()) + ": " +
		    		     item.getDescription());

			ScrollView descscr = new ScrollView(getContext());
			descscr.addView(desc);
			content.addView(descscr, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 2));

			LinearLayout bottomBox = new LinearLayout(getContext());
			bottomBox.setGravity(Gravity.RIGHT);

			final CheckBox cb = new CheckBox(getContext());
			cb.setText("Remind me");
			cb.setChecked(item.getRemind());
			bottomBox.addView(cb, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
			
			LinkedList<Schedule.Item.Link> links = item.getLinks();
			if (links != null) {
				Iterator<Schedule.Item.Link> linki = links.listIterator();
				while (linki.hasNext()) {
					Schedule.Item.Link link = linki.next();
					LinkButton btn = new LinkButton(getContext(), link);
					bottomBox.addView(btn);
				}
			}

			content.addView(bottomBox, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));

	    	new AlertDialog.Builder(getContext())
				.setTitle(getText())
	    		.setView(content)
	    		.show()
	    		.setOnDismissListener(new OnDismissListener() {
	    			public void onDismiss(DialogInterface dialog) {
	    				item.setRemind(cb.isChecked());
	    			}
	    		});
		}
		
		private class LinkButton extends ImageButton implements OnClickListener {
			Schedule.Item.Link link;
			
			public LinkButton(Context ctx, Schedule.Item.Link link_) {
				super(ctx);
				link = link_;
				setImageDrawable(link.getType().getIcon());
				setOnClickListener(this);
			}
			
			public void onClick(View v) {
		    	Uri uri = Uri.parse(link.getUrl());
		    	Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		    	intent.addCategory(Intent.CATEGORY_BROWSABLE);
		    	getContext().startActivity(intent);
			}
		}
	}
	
	protected class Clock extends SimpleScroller {
		private Element cell;
		private LinearLayout child;
		
		public Clock(Activity ctx, Calendar base, Calendar end) {
			super(ctx, SimpleScroller.HORIZONTAL);

			SimpleDateFormat df = new SimpleDateFormat("HH:mm");
			Calendar cal;
			
			cal = Calendar.getInstance();
			cal.setTime(base.getTime());
			
			child = new LinearLayout(ctx);
			
			cell = new Element(ctx);
			cell.setHeight(Deoxide.HourHeight);
			cell.setWidth(Deoxide.TentWidth);
			cell.setBackgroundColor(0xFF3F3F3F);
			child.addView(cell);

			while(true) {
				cell = new Element(ctx);
				
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
}
