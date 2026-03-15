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

import android.app.Activity;
import android.os.Bundle;
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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

public class TimeTable extends LinearLayout implements ScheduleViewer {
	private Giggity app;
	private Activity ctx;
	
	private OnItemSelectedListener groupSelL;
	private ScheduleListView scroller;
	
	private ArrayList<Schedule.ItemList> groups;

	private ArrayList fullList = new ArrayList();
	private HashMap<Schedule.Item,Schedule.ItemList> revGroups = new HashMap<>();

	public TimeTable(Activity ctx_, Collection<Schedule.ItemList> groups_) {
		super(ctx_);
		ctx = ctx_;
		app = (Giggity) ctx.getApplication();
		groups = new ArrayList<>(groups_);
		this.setOrientation(LinearLayout.VERTICAL);

		flatten();

		RelativeLayout.LayoutParams lp;

		scroller = new ScheduleListView(ctx);
		scroller.setCompact(true); /* Hide tent + day info, redundant in this view. */
		scroller.setHideEndtime(true);
		scroller.setList(fullList);
		if (!groups.isEmpty() && groups.get(0).getClass() == Schedule.Track.class) {
			scroller.setMultiRoom(true);
		}
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(scroller, lp);

		scroller.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
			}

			@Override
			public void onScroll(AbsListView v, int first, int visible, int total) {
				ScheduleViewActivity.onScroll(ctx);
				/* Find the first real item currently on-screen. */
				while (scroller.getList().get(first).getClass() != Schedule.Item.class && first < total)
					first++;
				if (first == total)
					return; /* Hmm. Just titles, no events? */
			}
		});
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		scroller.setPadding(left, top, right, bottom);
	}

	private void flatten() {
		for (Schedule.ItemList group : groups) {
			if (group.getItems().isEmpty()) {
				continue;
			}

			fullList.add(group);
			if (group.getClass() == Schedule.Track.class) {
				fullList.addAll(trackGrouper(group.getItems()));
			} else {
				fullList.addAll(group.getItems());
			}
			for (Schedule.Item it : group.getItems()) {
				revGroups.put(it, group);
			}
		}
		/* Ugly hack to get some empty space at the bottom of the list for nicer scrolling. */
		fullList.add("\n\n\n\n\n\n\n\n");
	}

	static private ArrayList<Schedule.Item> trackGrouper(Collection<Schedule.Item> in) {
		ArrayList<Schedule.Item> ret = new ArrayList<>();
		final HashMap<Schedule.Line,ArrayList<Schedule.Item>> rooms = new HashMap<>();
		HashSet<Schedule.Line> overlappers = new HashSet<>();
		Schedule.Item last = null;
		int changes = 1;
		for (Schedule.Item it : in) {
			if (!rooms.containsKey(it.getLine())) {
				rooms.put(it.getLine(), new ArrayList<Schedule.Item>());
			}
			rooms.get(it.getLine()).add(it);
			if (last != null && it.overlaps(last)) {
				overlappers.add(last.getLine());
				overlappers.add(it.getLine());
			}
			if (last != null && !it.getLine().equals(last.getLine())) {
				++changes;
			}
			last = it;
		}

		// TODO: Similarly, dumbass Android-- Java-- has no comparingByValue yet API<24.
		ArrayList<Schedule.Line> roomsSorted = new ArrayList<>(rooms.keySet());
		Collections.sort(roomsSorted, new Comparator<Schedule.Line>() {
			@Override
			public int compare(Schedule.Line e0, Schedule.Line e1) {
				// Sort key: start time of track's first talk in given room.
				return rooms.get(e0).get(0).compareTo(rooms.get(e1).get(0));
			}
		});

		// Heuristics so far: When a track uses multiple rooms but not any talk overlaps with another,
		// and if chronologically not too many room changes happen (<150% the number of rooms), just
		// list them chronologically. Otherwise, the grouping code right here kicks in.
		if (!overlappers.isEmpty() || changes > (1.5 * rooms.size())) {
			for (Schedule.Line room : roomsSorted) {
				ret.addAll(rooms.get(room));
			}
		} else {
			ret.addAll(in);
		}

		return ret;
	}

	@Override
	public void refreshContents() {
		scroller.refreshContents();
	}

	@Override
	public void refreshItems() {
		scroller.refreshItems();
	}

	@Override
	public void onShow() {
		app.showKeyboard(false, this);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		scroller.onSaveInstanceState(outState);
	}

	@Override
	public void restoreState(Bundle inState) {
		scroller.restoreState(inState);
	}

	@Override
	public boolean multiDay() {
		return false;
	}

	@Override
	public boolean extendsActionBar() {
		return true;
	}
}
