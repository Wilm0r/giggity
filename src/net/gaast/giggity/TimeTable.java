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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;

public class TimeTable extends RelativeLayout implements ScheduleViewer {
	Giggity app;
	Schedule sched;
	
	Gallery tents;
	ScheduleListView scroller;
	TableLayout table;
	OnItemSelectedListener tentsel;
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public TimeTable(Activity ctx, Schedule sched_) {
		super(ctx);
		app = (Giggity) ctx.getApplication();
		sched = sched_;

		Iterator<Schedule.Line> tenti;
		ArrayList fullList = new ArrayList();
		Iterator<Schedule.Item> itemi;
		Schedule.Item item = null;
		
		tenti = sched.getTents().iterator();
		while (tenti.hasNext()) {
			Schedule.Line tent = tenti.next();
			itemi = tent.getItems().iterator();
			fullList.add("\n\n" + tent.getTitle());
			
			while (itemi.hasNext()) {
				item = itemi.next();
				fullList.add(item);
			}
		}
    	
    	scroller = new ScheduleListView(ctx);
		scroller.setCompact(true); /* Hide tent + day info, redundant in this view. */
		scroller.setList(fullList);
		addView(scroller, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		tents = new Gallery(ctx);
    	tents.setAdapter(new TentListAdapter(ctx, sched_.getTents()));
		addView(tents, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

		/* Set up some navigation listeners. */
    	tentsel = new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				int to;
				for (to = 0; to < scroller.getCount(); to++) {
					try {
						if (((Schedule.Item)scroller.getList().get(to)).getLine() ==
							sched.getTents().get(position)) {
							scroller.setSelection(to - 1);
							break;
						}
					} catch (ClassCastException e) {
						/* Title */
					}
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
    	};
    	tents.setOnItemSelectedListener(tentsel);
    	
    	scroller.setOnScrollListener(new OnScrollListener() {
    		private boolean scrolling = false;
			@Override
			public void onScroll(AbsListView v, int first, int visible, int total) {
				if (!scrolling)
					return;
				/* Find the first real item currently on-screen. */
				while (scroller.getList().get(first).getClass() == String.class && first < total)
					first++;
				if (first == total)
					return; /* Hmm. Just titles, no events? */
				int to;
				for (to = 0; to < sched.getTents().size(); to ++)
					if (sched.getTents().get(to) == ((Schedule.Item)scroller.getList().get(first)).getLine())
						tents.setSelection(to);
			}

			/* This function is supposed to kill feedback loops between the 
			 * gallery and the scroller, in both directions, by disabling
			 * this listener when we're not manually scrolling, and the
			 * other one when we are. */
			@Override
			public void onScrollStateChanged(AbsListView v, int scrollState) {
				/* Disable this listener while scrolling to avoid the feedback loop. */
				if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
					tents.setOnItemSelectedListener(null);
				else if (scrollState == OnScrollListener.SCROLL_STATE_IDLE)
					tents.setOnItemSelectedListener(tentsel);
				
				scrolling = scrollState != OnScrollListener.SCROLL_STATE_IDLE;
			}
    	});
	}
	
	private class TentListAdapter extends BaseAdapter {
		Context ctx;
		LinkedList<Schedule.Line> tents;
		
		public TentListAdapter(Context ctx_, LinkedList<Schedule.Line> tents_) {
			ctx = ctx_;
			tents = tents_;
		}

		@Override
		public int getCount() {
			return tents.size();
		}

		@Override
		public Object getItem(int position) {
			return tents.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView ret = new TextView(ctx);
			
			ret.setText(tents.get(position).getTitle());
			ret.setBackgroundDrawable(ctx.getResources().getDrawable(android.R.drawable.dialog_frame));
			ret.setTextColor(0xffffffff);
			ret.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
			ret.setTextSize(10);
			
			return ret;
		}
	}
	
	/* Can only define interfaces in top-level classes. :-/ */ 
	public interface OnSwitchListener {
		public void onSwitchEvent(int direction);
	}

	@Override
	public void refreshContents() {
		scroller.refreshContents();
	}
}
