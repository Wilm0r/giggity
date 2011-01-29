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

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ScheduleListView extends ListView {
	ArrayList<?> list;
	EventAdapter adje;
	Context ctx;
	
	private boolean compact = false;
	private boolean showNow = true;
    
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
    
    protected void setCompact(boolean compact_) {
    	compact = compact_;
    }
    
    protected void setShowNow(boolean showNow_) {
    	showNow = showNow_;
    }
    
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
				int n = 0;
				Schedule.Item i = (Schedule.Item) items.get(position);
				RelativeLayout v = new RelativeLayout(ctx);
				Format df = new SimpleDateFormat("EE d MMM");
				Format tf = new SimpleDateFormat("HH:mm");
				
				TextView title, room, time, date;
				RelativeLayout.LayoutParams p;
				
				time = new TextView(ctx);
				time.setText(tf.format(i.getStartTime()) + "-" + tf.format(i.getEndTime()) + "  ");
				time.setTextSize(16);
				time.setId(++n);
				p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				v.addView(time, p);
				
				title = new TextView(ctx);
				title.setText(i.getTitle());
				title.setTextSize(16);
				title.setId(++n);
				p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				p.addRule(RelativeLayout.RIGHT_OF, time.getId());
				p.addRule(RelativeLayout.ALIGN_TOP, time.getId());
				v.addView(title, p);
				
				if (!compact) {
					date = new TextView(ctx);
					date.setText(df.format(i.getStartTime()) + "  ");
					date.setTextSize(12);
					date.setId(++n);
					p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
					p.addRule(RelativeLayout.BELOW, time.getId());
					p.addRule(RelativeLayout.ALIGN_LEFT, time.getId());
					p.addRule(RelativeLayout.ALIGN_RIGHT, time.getId());
					v.addView(date, p);
					
					room = new TextView(ctx);
					room.setText(i.getLine().getTitle());
					room.setTextSize(12);
					room.setId(++n);
					p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
					p.addRule(RelativeLayout.BELOW, title.getId());
					p.addRule(RelativeLayout.ALIGN_LEFT, title.getId());
					p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
					v.addView(room, p);
				}
				
				if (i.getRemind())
					v.setBackgroundColor(0x3300FF00);
				else if (showNow && i.compareTo(new Date()) == 0)
					v.setBackgroundColor(0x11FFFFFF);
				else
					v.setBackgroundColor(0x00000000);

				return v;
			} else {
				TextView tv = new TextView(ctx);
				tv.setText((String) items.get(position));
				tv.setTextSize(18);
				tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
				return tv;
			}
		}
    }
}
