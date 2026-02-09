/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2021 Wilmer van der Gaast <wilmer@gaast.net>
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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsoluteLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TreeSet;

@SuppressLint("SimpleDateFormat")
public class BlockScheduleVertical extends LinearLayout implements NestedScroller.Listener, ScheduleViewer {
	/* This is a vertical (i.e. time goes down instead of to the right) version of BlockSchedule.
	 * You'd think that these classes should be related but there's not THAT much reuse potential,
	 * other than the "Element" subclass and the colours, so for now I'm not going to bother. */
	Giggity app;
	Schedule sched;
	Activity ctx;
	
	Colours c;

	/* This object is pretty messy. :-/ It contains the
	 * following widgets: */
	
	Clock leftClock;

	/* mainTable is the middle part of the screen */
	LinearLayout mainTable;
	/* Separate this to keep them on screen when scrolling */
	LinearLayout tentHeaders;
	HorizontalScrollView tentHeadersScr;

	/* schedCont will contain all the actual data rows,
	 * we'll get scrolling by stuffing it inside schedContScr. */
	AbsoluteLayout schedCont;
	NestedScroller schedContScr;

	SharedPreferences pref;

	// All in "scaled pixels". The first four just need to get multiplied by the constructor.
	private int HourSize = 96;
	private int HeaderSize = 15;
	private int TentSize = 48;
	private final float fontSizeSmall = 12;
	private float fontSize = 14; // scaled/configurable

	BlockScheduleVertical(Activity ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		app = (Giggity) ctx.getApplication();
		sched = sched_;
		pref = PreferenceManager.getDefaultSharedPreferences(app);
		c = new Colours(getResources());
		
		setOrientation(LinearLayout.VERTICAL);
		
		HourSize *= getResources().getDisplayMetrics().density;
		HeaderSize *= getResources().getDisplayMetrics().density;
		TentSize *= getResources().getDisplayMetrics().density;

		HourSize = pref.getInt("block_schedule_hour_width", HourSize);
		TentSize = pref.getInt("block_schedule_tent_height", TentSize);

		draw();
	}

	@SuppressWarnings("deprecation")
	private void draw() {
		removeAllViews();

		pref = PreferenceManager.getDefaultSharedPreferences(app);
		// Re-fetching the setting to try to make this a little less sticky when columns are for
		// example temporarily extra wide (rotated screen, day with fewer tents?).
		TentSize = Math.max(pref.getInt("block_schedule_tent_height", TentSize), (Resources.getSystem().getDisplayMetrics().widthPixels - HeaderSize) / sched.getTents().size());
		
		int x, y;
		Calendar base, cal, end;
		ArrayList<Schedule.Line> tents;

		String fontSetting = pref.getString("font_size", "medium");
		if (fontSetting.equals("small")) {
			fontSize = fontSizeSmall;
		} else if (fontSetting.equals("medium")) {
			fontSize = 14; //(int) (HourSize / 3 / 3.6 / getResources().getDisplayMetrics().density);
		} else if (fontSetting.equals("large")){
			fontSize = 16; //(int) (HourSize / 3 / 2.6 / getResources().getDisplayMetrics().density);
		} else if (fontSetting.equals("xlarge")) {
			fontSize = 18;
		}

		schedCont = new AbsoluteLayout(ctx);

		base = Calendar.getInstance();
		base.setTime(sched.getFirstTime());
		base.add(Calendar.MINUTE, -(base.get(Calendar.MINUTE) % 30));

		end = Calendar.getInstance();
		end.setTime(sched.getLastTime());
		// Some slack needed for edge-to-edge, should remain mostly invisible.
		end.add(Calendar.HOUR_OF_DAY, 2);

		/* Simple background pattern with halfhour/tent grid. */
		Bitmap bmp = Bitmap.createBitmap(TentSize * 2, HourSize, Bitmap.Config.ARGB_8888);
		int[] bg0 = new int[TentSize];
		Arrays.fill(bg0, c.mainbg[0]);
		int[] bg1 = new int[TentSize];
		Arrays.fill(bg1, c.mainbg[1]);
		for (y = 0; y < HourSize; y++) {
			if (y < HourSize / 2) {
				bmp.setPixels(bg0, 0, TentSize, 0, y, TentSize, 1);
				bmp.setPixels(bg1, 0, TentSize, TentSize, y, TentSize, 1);
			} else {
				bmp.setPixels(bg1, 0, TentSize, 0, y, TentSize, 1);
			}
		}
		bmp.setDensity(getResources().getDisplayMetrics().densityDpi);
		BitmapDrawable bg = new BitmapDrawable(getResources(), bmp);
		bg.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
		schedCont.setBackground(bg);

		tentHeaders = new LinearLayout(ctx);
		tentHeaders.setOrientation(LinearLayout.HORIZONTAL);
		tentHeaders.setBackgroundColor(c.tentbg[1]);
		tentHeadersScr = new HorizontalScrollView(ctx);
		tentHeadersScr.addView(tentHeaders);
		tentHeadersScr.setHorizontalScrollBarEnabled(false);
		tentHeadersScr.setVerticalScrollBarEnabled(false);
		tentHeadersScr.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// Return true to pretend that we "handled" the event already and we don't need the
				// ScrollView code to handle it harder. :-)
				return true;
			}
		});
		addView(tentHeadersScr);

		TextView pad = new TextView(ctx);
		pad.setGravity(Gravity.CENTER_HORIZONTAL);
		pad.setHeight(HeaderSize);
		pad.setTextSize(fontSizeSmall);
		pad.setWidth(HeaderSize);
		pad.setBackgroundColor(c.clockbg[1]);
		pad.setPadding(0, 0, 0, 0);
		tentHeaders.addView(pad, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

		x = 0;
		int initScroll = HourSize * 4;
		int bottomY = 0;
		tents = new ArrayList<>(sched.getTents());
		for (Schedule.Line tent : tents) {
			int posy, w, h;

			/* Tent name on the first column. */
			TextView head = new TextView(ctx);
//			head.setHeight(HourHeight);
			head.setWidth(TentSize);
			head.setGravity(Gravity.CENTER_HORIZONTAL);
			head.setText(tent.getTitle());
			head.setMaxLines(2);
			head.setTextSize(fontSizeSmall);
			head.setBackgroundColor(c.tentbg[x&1]);
			head.setTextColor(c.tentfg[x&1]);
			tentHeaders.addView(head); //, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

			cal = Calendar.getInstance();
			cal.setTime(base.getTime());
//			cal.add(Calendar.MINUTE, -15);

			y = 0;  // for alternating between background colours, not for positioning
			w = TentSize;
			
			for (Schedule.Item gig : tent.getItems()) {
				posy = (int) ((gig.getStartTime().getTime() -
				               cal.getTime().getTime()) *
						HourSize / 3600000);
				h    = (int) ((gig.getEndTime().getTime() -
				               cal.getTime().getTime()) *
						HourSize / 3600000) - posy + 1;

				initScroll = Math.min(initScroll, posy);
				bottomY = Math.max(bottomY, posy + h);

				Element cell = new Element(ctx);
				cell.setItem(gig);
				cell.setBackgroundColor(c.itembg[((x+y)&1)]);
				cell.setTextColor(c.itemfg[((x+y)&1)]);
				y ++;
				AbsoluteLayout.LayoutParams lp = new AbsoluteLayout.LayoutParams(w - 1, h - 2, x * w, posy);
				schedCont.addView(cell, lp);
			}
			x ++;
		}

		// If *some* of the room names are long and 2-line, add padding to the ones that are not.
		// This really just ensures that the cells (thus background colours) are all the same size.
		tentHeaders.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				TreeSet<Integer> heights = new TreeSet<>();
				for (int i = 1; i < tentHeaders.getChildCount(); ++i) {
					heights.add(tentHeaders.getChildAt(i).getHeight());
				}
				if (heights.size() > 1) {
					for (int i = 1; i < tentHeaders.getChildCount(); ++i) {
						TextView v = (TextView) tentHeaders.getChildAt(i);
						if (v.getHeight() < heights.last()) {
							v.setText("\n" + v.getText());
							v.setMinLines(2);
						}
					}
				}
				// And avoid getting stuck in a loop of course.
				tentHeaders.getViewTreeObserver().removeOnGlobalLayoutListener(this);
			}
		});

		schedCont.setMinimumHeight(bottomY + HourSize / 2);

		schedContScr = new NestedScroller(ctx, NestedScroller.PINCH_TO_ZOOM);
		schedContScr.addView(schedCont);
		schedContScr.setScrollEventListener(this);

		mainTable = new LinearLayout(app);
		leftClock = new Clock(ctx, base, end);
		mainTable.addView(leftClock);
		mainTable.addView(schedContScr, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));
		addView(mainTable, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1));

		float hours;
		if (sched.isToday()) {
			Date now = new Date();
			hours = (float) (now.getTime() - sched.getFirstTime().getTime()) / 3600 / 1000;
			int scrollY = (int) ((hours - 1) * HourSize);
			schedContScr.setInitialXY(0, scrollY);
		} else {
			schedContScr.setInitialXY(0, initScroll);
		}

		setBackgroundColor(c.background);
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		schedCont.setPadding(0, 0, right, 0);
		schedCont.setClipToPadding(false);
	}

	/* If the user scrolls one view, keep the others in sync. */
	@Override
	public void onScrollEvent(NestedScroller src, int x, int y) {
		if (src == schedContScr) {
			leftClock.scrollTo(0, y);
			tentHeadersScr.scrollTo(x, 0);
		}
		ScheduleViewActivity.onScroll(ctx);
	}
	
	@Override
	public void onResizeEvent(NestedScroller src, float scaleX, float scaleY, int scrollX, int scrollY) {
		HourSize *= scaleY;
		TentSize *= scaleX;
		HourSize = Math.max(60, Math.min(HourSize, 1000));
		TentSize = Math.max(30, Math.min(TentSize, 400));

		SharedPreferences.Editor ed = pref.edit();
		ed.putInt("block_schedule_hour_width", HourSize);
		ed.putInt("block_schedule_tent_height", TentSize);
		ed.apply();

		draw();

		schedContScr.setInitialXY(scrollX, scrollY);
	}

	protected class Element extends LinearLayout {
		private int bgcolor;
		private Schedule.Item item;
		private TextView inner;

		public Element(Activity ctx) {
			super(ctx);
			inner = new TextView(ctx);
			inner.setGravity(Gravity.CENTER_HORIZONTAL);

			addView(inner, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			setPadding(2, 2, 2, 2);
			inner.setHeight(TentSize - 4);
			// This was here but seems no-op? setTextColor(0xFFFFFFFF);
//			setPadding(0, 0, 0, 0);
			inner.setTextSize(fontSize);
		}

		public void setItem(Schedule.Item item_) {
			item = item_;
			inner.setText(item.getTitle());
			setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					ScheduleViewActivity sva = (ScheduleViewActivity) ctx;
					sva.showItem(item, new ArrayList<>(item.getLine().getItems()), false, Element.this);
				}
			});
		}

		@Override
		public void setBackgroundColor(int color) {
			bgcolor = color;
			super.setBackgroundColor(bgcolor + 0x0f0f0f);
			if (item != null && item.getRemind()) {
				inner.setBackgroundColor(c.itembg[3]);
			} else {
				inner.setBackgroundColor(bgcolor);
			}
			if (item != null && item.isHidden()) {
				setAlpha(.25F);
			} else if (item != null && sched.isToday() && item.getEndTime().before(new Date())) {
				setAlpha(.5F);
			} else {
				setAlpha(1F);
			}
		}

		public void setWidth(int width) {
			inner.setWidth(width - 4 );
		}

		public void setTextColor(int colour) {
			inner.setTextColor(colour);
		}

		public void setBackgroundColor() {
			setBackgroundColor(bgcolor);
		}
	}
	
	protected class Clock extends ScrollView {
		private AbsoluteLayout child_;
		private Calendar base_;
		
		public Clock(Activity ctx, Calendar base, Calendar end) {
			super(ctx);

			setHorizontalScrollBarEnabled(false);
			setVerticalScrollBarEnabled(false);

			TextView cell;

			base_ = new GregorianCalendar();
			base_.setTime(base.getTime());
			
			SimpleDateFormat df = new SimpleDateFormat("HH:mm");
			Calendar cal;
			
			cal = Calendar.getInstance();
			cal.setTime(base_.getTime());

			AbsoluteLayout.LayoutParams lp = new AbsoluteLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0);

			child_ = new AbsoluteLayout(ctx);

			int i = 0;
			while(true) {
				cell = new TextView(ctx);
				cell.setRotation(-90);
				cell.setText(df.format(cal.getTime()));
				cell.setGravity(Gravity.RIGHT);
				// Yes you won't believe just how painful vertical elements are in the Android
				// composer. Measuring etc. is done *before* rotation so if you set the dimensions
				// to anything other than exactly square, very weird stuff will happen. While there
				// are some examples out there to fix this, none worked (and none claimed to be
				// great). So what do I do? Build and measure the elements as a square first! ...
				cell.setHeight(HourSize / 2);
				cell.setWidth(HourSize / 2);
				cell.setTextSize(fontSizeSmall);
				child_.addView(cell, new AbsoluteLayout.LayoutParams(HourSize / 2, HourSize / 2, 0, i * HourSize / 2));

				if (cal.after(end))
					break;
				
				cal.add(Calendar.MINUTE, 30);
				i++;
			}
			
			update();

			// ... then at the end I crop it using an intermediate view. Is that allowed? ;)
			LinearLayout shrink = new LinearLayout(ctx);
			shrink.addView(child_, new ViewGroup.LayoutParams(HeaderSize, ViewGroup.LayoutParams.WRAP_CONTENT));

			addView(shrink);
		}

		/* Mark the current 30m period in the clock green. */
		public void update() {
			int i;
			Calendar cal = new GregorianCalendar();
			cal.setTime(base_.getTime());
			for (i = 0; i < child_.getChildCount(); i ++) {
				TextView cell = (TextView) child_.getChildAt(i);
				long diff = System.currentTimeMillis() - cal.getTimeInMillis();
				if (diff >= 0 && diff < 1800000) {
					cell.setBackgroundColor(c.clockbg[2]);
					cell.setTextColor(c.clockfg[1]);
				} else {
					if (sched.isToday() && diff > 0) {
						cell.setAlpha(.5F);
					}
					if (cal.get(Calendar.MINUTE) == 0) {
						cell.setBackgroundColor(c.clockbg[0]);
						cell.setTextColor(c.clockfg[0]);
					} else {
						cell.setBackgroundColor(c.clockbg[1]);
						cell.setTextColor(c.clockfg[1]);
					}
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
	
	static private class Colours {
		public int background, lines;
		public int[] clockbg;
		public int[] clockfg;
		public int[] itembg;
		public int[] itemfg;
		public int[] mainbg;
		public int[] tentbg;
		public int[] tentfg;
		
		public Colours(Resources r) {
			background = r.getColor(R.color.blocks_bg);
			lines = r.getColor(R.color.blocks_lines);
			clockbg = new int[]{
					r.getColor(R.color.blocks_clockbg_0),
					r.getColor(R.color.blocks_clockbg_1),
					r.getColor(R.color.blocks_clockbg_now),
			};
			clockfg = new int[]{
					r.getColor(R.color.blocks_clockfg),
					r.getColor(R.color.blocks_clockfg),
					r.getColor(R.color.blocks_clockfg_now),
			};
			itembg = new int[]{
					r.getColor(R.color.blocks_itembg_0),
					r.getColor(R.color.blocks_itembg_1),
					r.getColor(R.color.blocks_itembg_selected),
					r.getColor(R.color.blocks_itembg_selected),
			};
			itemfg = new int[]{
					r.getColor(R.color.blocks_itemfg),
					r.getColor(R.color.blocks_itemfg),
					r.getColor(R.color.blocks_itemfg_selected),
					r.getColor(R.color.blocks_itemfg_selected),
			};
			mainbg = new int[]{
					r.getColor(R.color.blocks_mainbg_0),
					r.getColor(R.color.blocks_mainbg_1),
			};
			tentbg = new int[]{
					r.getColor(R.color.blocks_tentbg_0),
					r.getColor(R.color.blocks_tentbg_1),
			};
			tentfg = new int[]{
					r.getColor(R.color.blocks_tentfg),
					r.getColor(R.color.blocks_tentfg),
			};
		}
	}

	@Override
	public void refreshContents() {
		leftClock.update();
		if (sched.isToday()) {
			refreshItems();
		}
	}

	@Override
	public void refreshItems() {
		for (int i = 0; i < schedCont.getChildCount(); ++i) {
			Element e = (Element) schedCont.getChildAt(i);
			e.setBackgroundColor();
		}
	}

	@Override
	public void onShow() {
		app.showKeyboard(false, schedCont);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("scrollX", schedContScr.getScrollX());
		outState.putInt("scrollY", schedContScr.getVscrollY());
	}

	@Override
	public void restoreState(Bundle inState) {
		schedContScr.setInitialXY(inState.getInt("scrollX", 0), inState.getInt("scrollY", 0));
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
