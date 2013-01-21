/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BlockSchedule extends LinearLayout implements SimpleScroller.Listener, ScheduleViewer {
	Giggity app;
	Schedule sched;
	Activity ctx;
	
	Colours c;

	/* This object is pretty messy. :-/ It contains the
	 * following widgets: */
	
	/* Clocks at the top and bottom */
	Clock topClock;
	Clock bottomClock;
	
	/* mainTable is the middle part of the screen */
	LinearLayout mainTable;
	/* Separate this to keep them on screen when scrolling */
	LinearLayout tentHeaders;
	SimpleScroller tentHeadersScr;

	/* schedCont will contain all the actual data rows,
	 * we'll get scrolling by stuffing it inside schedContScr. */
	AbsoluteLayout schedCont;
	SimpleScroller schedContScr;

	SharedPreferences pref;
	
	private double SizeScale;
	private int HourWidth = 72;
	private int HourHeight = 30;
	private int TentHeight = 48;
	private int TentWidth = 72;
	
	private Handler timer;
	
	@SuppressWarnings("deprecation")
	BlockSchedule(Activity ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		app = (Giggity) ctx.getApplication();
		sched = sched_;
		pref = PreferenceManager.getDefaultSharedPreferences(app);
		timer = new Handler();
		c = new Light();
		
		setOrientation(LinearLayout.VERTICAL);
		
		HourWidth = pref.getInt("block_schedule_hour_width", HourWidth);
		TentHeight = pref.getInt("block_schedule_tent_height", TentHeight);
		SizeScale = HourWidth / 72.0;

		draw();
	}

	private void draw() {
		this.removeAllViews();
		
		int x, y;
		Calendar base, cal, end;
		LinkedList<Schedule.Line> tents;
		Element cell;
		
		schedCont = new AbsoluteLayout(ctx);
		//schedCont.setBackgroundColor(c.background);
		//schedCont.setMinimumHeight(sched.getTents().size());
		
		Bitmap bmp = Bitmap.createBitmap(HourWidth, TentHeight, Bitmap.Config.ARGB_8888);
		bmp.setHasAlpha(true);
		for (x = 0; x < HourWidth; x++) {
			for (y = 0; y < TentHeight; y ++) {
				if (x == HourWidth / 4 && (y & 12) > 0)
					bmp.setPixel(x, y, c.lines);
				else if (x == HourWidth / 4 * 3 && (y & 8) > 0)
					bmp.setPixel(x, y, c.lines);
				else if (y == TentHeight - 1)
					bmp.setPixel(x, y, c.lines);
			}
		}
		BitmapDrawable bg = new BitmapDrawable(bmp);
		bg.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
		schedCont.setBackgroundDrawable(bg);
		
		base = Calendar.getInstance();
		base.setTime(sched.getFirstTime());
		base.set(Calendar.MINUTE, 0);
		
		end = Calendar.getInstance();
		end.setTime(sched.getLastTime());		

		topClock = new Clock(ctx, base, end);
		topClock.setScrollEventListener(this);
		addView(topClock);
		
		tentHeaders = new LinearLayout(ctx);
		tentHeaders.setOrientation(LinearLayout.VERTICAL);
		tentHeadersScr = new SimpleScroller(ctx, SimpleScroller.VERTICAL | 0);
		tentHeadersScr.addView(tentHeaders);
		tentHeadersScr.setScrollEventListener(this);
		//schedCont.setMinimumHeight(1000); /* To make the lines run to the bottom. */
		
		y = 0;
		tents = sched.getTents();
		for (Schedule.Line tent : tents) {
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
			h = TentHeight;
			
			for ( Schedule.Item gig : tent.getItems()) {
				posx = (int) ((gig.getStartTime().getTime() -
				               cal.getTime().getTime()) *
				              HourWidth / 3600000);
				w    = (int) ((gig.getEndTime().getTime() -
				               cal.getTime().getTime()) *
				              HourWidth / 3600000) - posx;
				
				cell = new Element(ctx);
				cell.setItem(gig);
				cell.setWidth(w);
				cell.setBackgroundColor(c.itembg[((y+x)&1)]);
				cell.setTextColor(c.itemfg[((y+x)&1)]);
				x ++;
				cell.setText(gig.getTitle());
				AbsoluteLayout.LayoutParams lp = new AbsoluteLayout.LayoutParams(w, h - 1, posx, y * h);
				schedCont.addView(cell, lp);
			}
			y ++;
		}

		schedContScr = new SimpleScroller(ctx, SimpleScroller.HORIZONTAL | SimpleScroller.VERTICAL | SimpleScroller.PINCH_TO_ZOOM);
		schedContScr.addView(schedCont);
		schedContScr.setScrollEventListener(this);

		mainTable = new LinearLayout(app);
		mainTable.addView(tentHeadersScr);
		mainTable.addView(schedContScr, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1));
		addView(mainTable, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
		
		bottomClock = new Clock(ctx, base, end);
		bottomClock.setScrollEventListener(this);
		addView(bottomClock);
		
		setBackgroundColor(c.background);
	}
	
	/* If the user scrolls one view, keep the others in sync. */
	@Override
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
		ScheduleViewActivity.onScroll(ctx);
	}
	
	@Override
	public void onResizeEvent(SimpleScroller src, float scaleX, float scaleY, int scrollX_, int scrollY_) {
		final float scrollX = scrollX_;
		final float scrollY = scrollY_;
		HourWidth *= scaleX;
		TentHeight *= scaleY;
		SizeScale *= scaleX;
		draw();
		
		SharedPreferences.Editor ed = pref.edit();
		ed.putInt("block_schedule_hour_width", HourWidth);
		ed.putInt("block_schedule_tent_height", TentHeight);
		ed.apply();
		
		/* Need to do this in a timer or it doesn't work, I guess
		 * because we need the layout code to do a cycle first. */
		this.post(new Runnable() {
			@Override
			public void run() {
				schedContScr.scrollTo((int) scrollX, (int) scrollY);	
			}
		});
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
			/* sqrt because 8 is pretty small already and scaling the font 
			 * linearly with the rest won't create more space for long titles */
			//setTextSize((float) (8 * Math.sqrt(SizeScale)));
			setTextSize(9);
		}
		
		public void setItem(Schedule.Item item_) {
			item = item_;
			setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					EventDialog evd = new EventDialog(ctx, item);
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
				super.setBackgroundColor(c.itembg[3]);
			} else {
				super.setBackgroundColor(bgcolor);
			}
		}
	}
	
	protected class Clock extends SimpleScroller {
		private Element cell;
		private LinearLayout child;
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
			
			update();
			addView(child);
		}
		
		/* Mark the current 30m period in the clock green. */
		public void update() {
			int i;
			Calendar cal = new GregorianCalendar();
			cal.setTime(base.getTime());
			for (i = 1; i < child.getChildCount(); i ++) {
				Element cell = (Element) child.getChildAt(i);
				long diff = System.currentTimeMillis() - cal.getTimeInMillis(); 
				if (diff >= 0 && diff < 1800000) {
					cell.setBackgroundColor(c.clockbg[2]);
					cell.setTextColor(c.clockfg[1]);
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
		public int background, lines;
		public int clockbg[], clockfg[];
		public int itembg[], itemfg[];
		public int tentbg[], tentfg[];
		
		public Colours() {
			clockbg = new int[3];
			clockfg = new int[3];
			itembg = new int[4];
			itemfg = new int[4];
			tentbg = new int[2];
			tentfg = new int[2];
		}
	}

	static private class Light extends Colours {
		public Light() {
			super();
			background = 0xFFF9FCDA;
			lines = 0xFFA3D3FC;
			clockbg[0] = 0xFFF5FC49;
			clockbg[1] = 0xFFF8FC9C;
			clockbg[2] = 0xFF00CF00;
			itembg[0] = 0xFFE0EFFC;
			itembg[1] = 0xFFC2E1FC;
			itembg[2] = 0xFF00CF00;
			itembg[3] = 0xFF00CF00;
			tentbg[0] = 0xFFFAFCB8;
			tentbg[1] = 0xFFF8FC9C;
			clockfg[0] = clockfg[1] = clockfg[2] = itemfg[0] = itemfg[1] =
				tentfg[0] = tentfg[1] = 0xFF000000;
			itemfg[0] = itemfg[1] = itemfg[2] = itemfg[3] = 0xFF000000;
		}
	}

	@Override
	public void refreshContents() {
		topClock.update();
		bottomClock.update();
	}
	
	@Override
	public boolean multiDay() {
		return false;
	}
}
