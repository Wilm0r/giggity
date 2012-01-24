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

import java.util.AbstractList;
import java.util.ArrayList;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ScheduleListView extends ListView implements ScheduleViewer {
	ArrayList<?> list;
	EventAdapter adje;
	Context ctx;
	int itemViewFlags = ScheduleItemView.SHOW_NOW | ScheduleItemView.SHOW_REMIND;
	
	@SuppressWarnings("rawtypes")
	public ScheduleListView(Context ctx_) {
		super(ctx_);
		ctx = ctx_;
		
		this.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> l, View v, int position, long id) {
				Schedule.Item item = (Schedule.Item) list.get(position);
				EventDialog evd = new EventDialog(ctx, item);
				evd.setOnDismissListener(new OnDismissListener() {
					public void onDismiss(DialogInterface dialog) {
						adje.notifyDataSetChanged();
					}
				});
				evd.show();
			}
		});
		
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
	
	@Override
	public void refreshContents() {
		adje.notifyDataSetChanged();
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
				return new ScheduleItemView(ctx, (Schedule.Item) items.get(position), itemViewFlags);
			} else {
				TextView tv = new TextView(ctx);
				tv.setText((String) items.get(position));
				tv.setTextSize(18);
				tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
				return tv;
			}
		}
	}
	
	@Override
	public boolean multiDay() {
		return false;
	}
}
