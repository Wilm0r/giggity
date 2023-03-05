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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.text.MeasuredText;
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

public class ScheduleListView extends ListView implements ScheduleViewer {
	ArrayList<?> list;
	EventAdapter adje;
	Context ctx;
	int itemViewFlags = ScheduleItemView.SHOW_NOW | ScheduleItemView.SHOW_REMIND;
	int itemListFlags = 0;
	Giggity app;

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

		ViewGroup bla = new LinearLayout(ctx);
		inflate(ctx, R.layout.schedule_item, bla);
		TextView bliep = bla.findViewById(R.id.time);
		CharSequence timeText = bliep.getText();
		MeasuredText mt = new MeasuredText.Builder(timeText.toString().toCharArray())  // O_o
				                  .appendStyleRun(bliep.getPaint(), timeText.length(), false)
				                  .build();
		int greyWidth = (int) mt.getWidth(0, timeText.length()) + app.dp2px(10);

		// Grey background for the time(+date) column on the left, but continuous so drawn here.
		Bitmap bmp = Bitmap.createBitmap(greyWidth, 1, Bitmap.Config.ARGB_8888);
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

	protected void setHideDate(boolean hideDate) {
		if (hideDate)
			itemViewFlags |= ScheduleItemView.HIDE_DATE;
		else
			itemViewFlags &= ~ScheduleItemView.HIDE_DATE;
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

	protected void setMultiRoom(boolean multiRoom) {
		if (multiRoom)
			itemListFlags |= ScheduleItemView.MULTI_ROOM;
		else
			itemListFlags &= ~ScheduleItemView.MULTI_ROOM;
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
				if ((itemListFlags & ScheduleItemView.MULTI_ROOM) > 0 && position > 0) {
					if (items.get(position-1).getClass() == Schedule.Item.class) {
						if (((Schedule.Item)items.get(position)).getLine() != ((Schedule.Item)items.get(position-1)).getLine()) {
							flags &= ~ScheduleItemView.COMPACT;
						}
					} else if (items.get(position-1).getClass() == Schedule.Track.class) {
						if (((Schedule.Item) items.get(position)).getLine() != ((Schedule.Track) items.get(position - 1)).getLine()) {
							flags &= ~ScheduleItemView.COMPACT;
						}
					}
				}
				if ((itemListFlags & ScheduleItemView.HIDE_ENDTIME) > 0) {
					if (position < (items.size() - 1) && items.get(position + 1).getClass() == Schedule.Item.class) {
						Schedule.Item it2 = (Schedule.Item) items.get(position + 1);
						if (it1.getLine().equals(it2.getLine()) && it1.getEndTime().equals(it2.getStartTime())) {
							flags |= ScheduleItemView.HIDE_ENDTIME;
						}
					}
				}
				return new ScheduleItemView(ctx, it1, flags);
			} else if (items.get(position).getClass() == Schedule.Line.class) {
				return new ScheduleLineView(ctx, (Schedule.Line) items.get(position));
			} else if (items.get(position).getClass() == Schedule.Track.class) {
				return new ScheduleTrackView(ctx, (Schedule.Track) items.get(position));
			} else {
				String text = (String) items.get(position);
				if (text.trim().isEmpty()) {
					/* Still abusing whitespace-only strings for spacing. */
					TextView tv = new TextView(ctx);
					tv.setText(text);
					tv.setTextSize(18);
					tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
					tv.setTextColor(getResources().getColor(R.color.dark_text));
					app.setPadding(tv, 4, 0, 0, 0);
					return tv;
				} else {
					/* There's actual text. Box it. */
					RelativeLayout ret = new RelativeLayout(ctx);
					inflate(ctx, R.layout.schedule_line, ret);
					TextView tv = ret.findViewById(R.id.lineTitle);
					tv.setText(text.trim());
					return ret;
				}
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

			inflate(ctx, R.layout.schedule_line, this);

			TextView tv = findViewById(R.id.lineTitle);
			tv.setText(line.getTitle());

			Schedule.Track track = line.getTrack();
			if (track != null && !line.getTitle().toLowerCase().contains(track.getTitle().toLowerCase())) {
				tv = findViewById(R.id.lineSubTitle);
				tv.setText(track.getTitle());
				tv.setVisibility(View.VISIBLE);
			}

			if (line.getLocation() != null) {
				// TODO: Restore icon or so to indicate location info is available for room?
				// Also, maybe a nicer way to show (FOSDEM-specific, for now) room status
				setOnClickListener(ScheduleUI.locationClickListener(getContext(), line));

				// No clue when I stopped adding this view but I guess it can indeed stay away?
				ImageView iv = new ImageView(ctx);
				iv.setImageResource(R.drawable.ic_place_black_24dp);
				iv.setId(2);
			}
		}
	}

	private class ScheduleTrackView extends LinearLayout {
		Context ctx;
		Schedule.Track track;

		public ScheduleTrackView(Context context, Schedule.Track track_) {
			super(context);
			ctx = context;
			track = track_;

			inflate(ctx, R.layout.schedule_line, this);

			TextView tv = findViewById(R.id.lineTitle);
			tv.setText(track.getTitle());

			Schedule.Line allLine = track_.getLine();
			if (allLine != null && !track.getTitle().toLowerCase().contains(allLine.getTitle().toLowerCase())) {
				tv = findViewById(R.id.lineSubTitle);
				tv.setText(allLine.getTitle());
				tv.setVisibility(View.VISIBLE);
			}
		}
	}

	/* Need to change this to true in SearchActivity. */
	private boolean multiDay = false;
	
	@Override
	public boolean multiDay() {
		return multiDay;
	}

	@Override
	public boolean extendsActionBar() {
		return false;
	}
}
