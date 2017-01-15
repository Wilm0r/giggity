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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;

@SuppressLint("SimpleDateFormat")
public class BlockSchedule extends LinearLayout implements NestedScroller.Listener, ScheduleViewer {
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
	ScrollView tentHeadersScr;

	/* schedCont will contain all the actual data rows,
	 * we'll get scrolling by stuffing it inside schedContScr. */
	AbsoluteLayout schedCont;
	NestedScroller schedContScr;

	SharedPreferences pref;

	// All in "scaled pixels". The first four just need to get multiplied by the constructor.
	private int HourWidth = 96;
	private int HourHeight = 15;
	private int TentHeight = 48;
	private int TentWidth = 64;
	private final float fontSizeSmall = 12;
	private float fontSize = 12; // scaled/configurable
	
	BlockSchedule(Activity ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		app = (Giggity) ctx.getApplication();
		sched = sched_;
		pref = PreferenceManager.getDefaultSharedPreferences(app);
		c = new Light();
		
		setOrientation(LinearLayout.VERTICAL);
		
		HourWidth *= getResources().getDisplayMetrics().density;
		HourHeight *= getResources().getDisplayMetrics().density;
		TentHeight *= getResources().getDisplayMetrics().density;
		TentWidth *= getResources().getDisplayMetrics().density;

		HourWidth = pref.getInt("block_schedule_hour_width", HourWidth);
		TentHeight = pref.getInt("block_schedule_tent_height", TentHeight);

		draw();
	}

	@SuppressWarnings("deprecation")
	private void draw() {
		removeAllViews();
		
		int x, y;
		Calendar base, cal, end;
		LinkedList<Schedule.Line> tents;

		String fontSetting = pref.getString("font_size", "medium");
		if (fontSetting.equals("small")) {
			fontSize = fontSizeSmall;
		} else if (fontSetting.equals("medium")) {
			fontSize = (int) (TentHeight / 3.6 / getResources().getDisplayMetrics().density);
		} else {
			fontSize = (int) (TentHeight / 2.6 / getResources().getDisplayMetrics().density);
		}

		schedCont = new AbsoluteLayout(ctx);

		base = Calendar.getInstance();
		base.setTime(sched.getFirstTime());
		base.add(Calendar.MINUTE, -(base.get(Calendar.MINUTE) % 30));

		end = Calendar.getInstance();
		end.setTime(sched.getLastTime());

		/* Little hack to create a background drawable with some (dotted) lines for easier readability. */
		Bitmap bmp = Bitmap.createBitmap(HourWidth, TentHeight, Bitmap.Config.ARGB_8888);
		for (x = 0; x < HourWidth; x++) {
			/* Horizontal line at bottom */
			bmp.setPixel(x, TentHeight - 1, c.lines);
		}
		int hourX = 1;
		if (base.get(Calendar.MINUTE) == 0) {
			hourX = HourWidth / 4;
		} else {
			hourX = HourWidth * 3 / 4;
		}
		for (y = 0; y < TentHeight; y++) {
			/* Long-dotted line on hour boundaries */
			if ((y & 12) > 0)
				bmp.setPixel(hourX, y, c.lines);
			/* Short-dotted line on :30 hourly boundaries */
			if ((y & 8) > 0)
				bmp.setPixel(HourWidth - hourX, y, c.lines);
		}
		bmp.setDensity(getResources().getDisplayMetrics().densityDpi);
		BitmapDrawable bg = new BitmapDrawable(bmp);
		bg.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
		bg.setTargetDensity(getResources().getDisplayMetrics().densityDpi);
		schedCont.setBackgroundDrawable(bg);

		topClock = new Clock(ctx, base, end);
		addView(topClock);
		
		tentHeaders = new LinearLayout(ctx);
		tentHeaders.setOrientation(LinearLayout.VERTICAL);
		tentHeadersScr = new ScrollView(ctx);
		tentHeadersScr.addView(tentHeaders);
		tentHeadersScr.setVerticalScrollBarEnabled(false);
		tentHeadersScr.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// Return true to pretend that we "handled" the event already and we don't need the
				// ScrollView code to handle it harder. :-)
				return true;
			}
		});

		y = 0;
		tents = sched.getTents();
		for (Schedule.Line tent : tents) {
			int posx, h, w;

			/* Tent name on the first column. */
			TextView head = new TextView(ctx);
			head.setHeight(TentHeight);
			head.setWidth(TentWidth);
			head.setGravity(Gravity.CENTER_HORIZONTAL);
			head.setText(tent.getTitle());
			head.setTextSize(fontSizeSmall);
			head.setBackgroundColor(c.tentbg[y&1]);
			head.setTextColor(c.tentfg[y&1]);
			tentHeaders.addView(head);

			cal = Calendar.getInstance();
			cal.setTime(base.getTime());
			cal.add(Calendar.MINUTE, -15);

			x = 0;
			h = TentHeight;
			
			for (Schedule.Item gig : tent.getItems()) {
				posx = (int) ((gig.getStartTime().getTime() -
				               cal.getTime().getTime()) *
				              HourWidth / 3600000);
				w    = (int) ((gig.getEndTime().getTime() -
				               cal.getTime().getTime()) *
				              HourWidth / 3600000) - posx + 1;
				
				Element cell = new Element(ctx);
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

		schedContScr = new NestedScroller(ctx, NestedScroller.PINCH_TO_ZOOM);
		schedContScr.addView(schedCont);
		schedContScr.setScrollEventListener(this);

		mainTable = new LinearLayout(app);
		mainTable.addView(tentHeadersScr);
		mainTable.addView(schedContScr, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));
		addView(mainTable, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1));
		
		bottomClock = new Clock(ctx, base, end);
		addView(bottomClock);

		Date now = new Date();
		if (sched.getFirstTime().before(now) && sched.getLastTime().after(now)) {
			float hours = (float) (now.getTime() - sched.getFirstTime().getTime()) / 3600 / 1000;
			int scrollX = (int) (hours - 1) * HourWidth;
			schedContScr.setInitialXY(scrollX, 0);
		}
		
		setBackgroundColor(c.background);
	}
	
	/* If the user scrolls one view, keep the others in sync. */
	@Override
	public void onScrollEvent(NestedScroller src, int x, int y) {
		if (src == schedContScr) {
			topClock.scrollTo(x, 0);
			bottomClock.scrollTo(x, 0);
			tentHeadersScr.scrollTo(0, y);
		}
		ScheduleViewActivity.onScroll(ctx);
	}
	
	@Override
	public void onResizeEvent(NestedScroller src, float scaleX, float scaleY, int scrollX, int scrollY) {
		HourWidth *= scaleX;
		TentHeight *= scaleY;
		HourWidth = Math.max(60, Math.min(HourWidth, 1000));
		TentHeight = Math.max(30, Math.min(TentHeight, 400));

		draw();
		
		SharedPreferences.Editor ed = pref.edit();
		ed.putInt("block_schedule_hour_width", HourWidth);
		ed.putInt("block_schedule_tent_height", TentHeight);
		ed.apply();

		schedContScr.setInitialXY(scrollX, scrollY);
	}

	protected class Element extends TextView {
		private int bgcolor;
		private Schedule.Item item;

		public Element(Activity ctx) {
			super(ctx);
			setGravity(Gravity.CENTER_HORIZONTAL);
			setHeight(TentHeight);
			setTextColor(0xFFFFFFFF);
			setPadding(0, 0, 0, 0);
			setTextSize(fontSize);
		}
		
		public void setItem(Schedule.Item item_) {
			item = item_;
			setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					ScheduleViewActivity sva = (ScheduleViewActivity) ctx;
					sva.showItem(item, new ArrayList<>(item.getLine().getItems()));
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
			if (item != null && item.isHidden()) {
				setAlpha(.5F);
			} else {
				setAlpha(1F);
			}
		}

		public void setBackgroundColor() {
			setBackgroundColor(bgcolor);
		}
	}
	
	protected class Clock extends HorizontalScrollView {
		private LinearLayout child_;
		private Calendar base_;
		
		public Clock(Activity ctx, Calendar base, Calendar end) {
			super(ctx);

			setHorizontalScrollBarEnabled(false);

			TextView cell;

			base_ = new GregorianCalendar();
			base_.setTime(base.getTime());
			
			SimpleDateFormat df = new SimpleDateFormat("HH:mm");
			Calendar cal;
			
			cal = Calendar.getInstance();
			cal.setTime(base_.getTime());

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);

			child_ = new LinearLayout(ctx);
			
			cell = new TextView(ctx);
			cell.setGravity(Gravity.CENTER_HORIZONTAL);
			cell.setHeight(HourHeight);
			cell.setWidth(TentWidth);
			cell.setBackgroundColor(c.clockbg[1]);
			child_.addView(cell, lp);

			while(true) {
				cell = new TextView(ctx);
				
				cell.setText(df.format(cal.getTime()));
				cell.setGravity(Gravity.CENTER_HORIZONTAL);
				cell.setHeight(HourHeight);
				cell.setWidth(HourWidth / 2);
				cell.setTextSize(fontSizeSmall);
				child_.addView(cell, lp);

				if (cal.after(end))
					break;
				
				cal.add(Calendar.MINUTE, 30);
			}
			
			update();
			addView(child_);
		}
		
		/* Mark the current 30m period in the clock green. */
		public void update() {
			int i;
			Calendar cal = new GregorianCalendar();
			cal.setTime(base_.getTime());
			for (i = 1; i < child_.getChildCount(); i ++) {
				TextView cell = (TextView) child_.getChildAt(i);
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

		/* Nah. The clocks are tiny and have a little more range than the schedule which looks ugly. So just block scrolling. */
		@Override
		public boolean onTouchEvent(MotionEvent ev) {
			return true;
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
	public void refreshItems() {
		for (int i = 0; i < schedCont.getChildCount(); ++i) {
			Element e = (Element) schedCont.getChildAt(i);
			e.setBackgroundColor();
		}
	}

	@Override
	public boolean multiDay() {
		return false;
	}

	@Override
	public boolean extendsActionBar() {
		return false;
	}
}
