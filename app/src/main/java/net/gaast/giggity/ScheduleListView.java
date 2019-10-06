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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Calendar;

public class ScheduleListView extends ListView implements ScheduleViewer {
	ArrayList<?> list;
	EventAdapter adje;
	Context ctx;
	int itemViewFlags = ScheduleItemView.SHOW_NOW | ScheduleItemView.SHOW_REMIND;
	int itemListFlags = 0;
	Giggity app;

	@SuppressWarnings("rawtypes")
	public ScheduleListView(Context ctx_) {
		super(ctx_);
		ctx = ctx_;
		app = (Giggity) ctx.getApplicationContext();

		this.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> l, View v, int position, long id) {
				// Find all other Item entries before and after the current one. Ugly. :-(
				ArrayList<Schedule.Item> others = new ArrayList<Schedule.Item>();
				for (int i = position; i >= 0 && list.get(i).getClass() == Schedule.Item.class; --i) {
					others.add(0, (Schedule.Item) list.get(i));
				}
				for (int i = position + 1; i < list.size() && list.get(i).getClass() == Schedule.Item.class; ++i) {
					others.add((Schedule.Item) list.get(i));
				}

				Schedule.Item item = (Schedule.Item) list.get(position);
				ScheduleViewActivity sva = (ScheduleViewActivity) ctx;

				sva.showItem(item, others, false, v);
			}
		});

		// Grey background for the time(+date) column on the left, but continuous so drawn here.
		Bitmap bmp = Bitmap.createBitmap(app.dp2px(102), 1, Bitmap.Config.ARGB_8888);
		bmp.setDensity(getResources().getDisplayMetrics().densityDpi);
		for (int x = 0; x < bmp.getWidth() - 1; x++) {
			bmp.setPixel(x, 0, getResources().getColor(R.color.time_back));
		}
		// Leave the last pixel transparent since that one gets repeated all the way to the right?

		BitmapDrawable bg = new BitmapDrawable(bmp);
		bg.setTileModeY(Shader.TileMode.REPEAT);
		bg.setTargetDensity(getResources().getDisplayMetrics().densityDpi);
		setBackgroundDrawable(bg);

		setDivider(new ColorDrawable(getResources().getColor(R.color.time_back)));
		setDividerHeight(app.dp2px(1));

		list = new ArrayList();
		setAdapter(adje = new EventAdapter(list));
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void setList(AbstractList list_) {
		list.clear();
		list.addAll(list_);
		adje.notifyDataSetChanged();
	}
	
	protected AbstractList<?> getList() {
		return list;
	}
	
	protected void setCompact(boolean compact) {
		if (compact)
			itemViewFlags |= ScheduleItemView.COMPACT;
		else
			itemViewFlags &= ~ScheduleItemView.COMPACT;
	}
	
	protected void setShowNow(boolean showNow) {
		if (showNow)
			itemViewFlags |= ScheduleItemView.SHOW_NOW;
		else
			itemViewFlags &= ~ScheduleItemView.SHOW_NOW;
	}
	
	protected void setShowRemind(boolean showRemind) {
		if (showRemind)
			itemViewFlags |= ScheduleItemView.SHOW_REMIND;
		else
			itemViewFlags &= ~ScheduleItemView.SHOW_REMIND;
	}

	protected void setHideEndtime(boolean hideEndtime) {
		if (hideEndtime)
			itemListFlags |= ScheduleItemView.HIDE_ENDTIME;
		else
			itemListFlags &= ~ScheduleItemView.HIDE_ENDTIME;
	}
	
	@Override
	public void refreshContents() {
		adje.notifyDataSetChanged();
	}

	@Override
	public void refreshItems() {
		adje.notifyDataSetChanged();
	}

	@Override
	public void onShow() {
		app.showKeyboard(getContext(), null);
	}

	private class EventAdapter extends BaseAdapter {
		AbstractList<?> items;
		
		public EventAdapter(AbstractList<?> items_) {
			items = items_;
		}
		
		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public Object getItem(int position) {
			return items.get(position);
		}

		@Override
		public long getItemId(int position) {
			return (long) position;
		}

		@Override
		public boolean isEnabled(int position) {
			return items.get(position).getClass() == Schedule.Item.class;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (items.get(position).getClass() == Schedule.Item.class) {
				Schedule.Item it1 = (Schedule.Item) items.get(position);
				int flags = itemViewFlags;
				if ((itemListFlags & ScheduleItemView.HIDE_ENDTIME) > 0) {
					if (position < (items.size() - 1) && items.get(position + 1).getClass() == Schedule.Item.class) {
						Schedule.Item it2 = (Schedule.Item) items.get(position + 1);
						if (it1.getEndTime().equals(it2.getStartTime())) {
							flags |= ScheduleItemView.HIDE_ENDTIME;
						}
					}
				}
				return new ScheduleItemView(ctx, it1, flags);
			} else if (items.get(position).getClass() == Schedule.Line.class) {
				return new ScheduleLineView(ctx, (Schedule.Line) items.get(position));
			} else {
				TextView tv = new TextView(ctx);
				tv.setText((String) items.get(position));
				tv.setTextSize(18);
				tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
				tv.setTextColor(getResources().getColor(R.color.dark_text));
				app.setPadding(tv, 4, 0, 0, 0);
				return tv;
			}
		}
	}

	private class ScheduleLineView extends LinearLayout {
		Context ctx;
		Schedule.Line line;

		public ScheduleLineView(Context context, Schedule.Line line_) {
			super(context);
			ctx = context;
			line = line_;

			String track = null;
			for (Schedule.Item item : line.getItems()) {
				if (item.getTrack() == null) {
					track = null;
					break;
				} else if (track == null) {
					/* If the name of the track is in the room name already, don't repeat it. */
					if (line.getTitle().toLowerCase().contains(item.getTrack().toLowerCase()))
						break;
					track = item.getTrack();
				} else if (!track.equals(item.getTrack())) {
					track = null;
					break;
				}
			}

			inflate(ctx, R.layout.schedule_line, this);

			TextView tv = findViewById(R.id.lineTitle);
			tv.setText(line.getTitle() + (track == null ? "" : " (" + track + ")"));

			if (line.getLocation() != null) {
				// TODO: Restore icon or so to indicate location info is available for room?
				// Also, maybe a nicer way to show (FOSDEM-specific, for now) room status
				setOnClickListener(ScheduleUI.locationClickListener(getContext(), line));

				ImageView iv = new ImageView(ctx);
				iv.setImageResource(R.drawable.ic_place_black_24dp);
				iv.setId(2);
			}
		}
	}
	
	/* Need to change this to true in SearchActivity. */
	private boolean multiDay = false;
	
	@Override
	public boolean multiDay() {
		return multiDay;
	}

	public void setMultiDay(boolean md) {
		multiDay = md;
	}

	@Override
	public boolean extendsActionBar() {
		return false;
	}
}
