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

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;

import java.util.AbstractList;
import java.util.ArrayList;

public class SearchActivity extends ScheduleViewActivity {
	private ScheduleListView lv;
	private AbstractList<Schedule.Item> items;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		wantDrawer = false;
		super.onCreate(savedInstanceState);

		if (!getIntent().getAction().equals(Intent.ACTION_SEARCH)) {
			finish();
			return;
		}
		
		/* Doesn't seem to work..
		TextView tv = new TextView(this);
		tv.setText("No results.");
		this.getListView().setEmptyView(tv);
		*/
		
		app = (Giggity) getApplication();
		sched = app.getLastSchedule();
		if (sched == null) {
			finish(); /* WTF */
			return;
		}
		
		sched.setDay(-1);
		String query;
		
		lv = new ScheduleListView(this);

		if ((query = getIntent().getStringExtra(SearchManager.QUERY)) != null) {
			items = sched.searchItems(query);
			setTitle(String.format(getString(R.string.search_results), query, sched.getTitle()));
		} else if ((query = getIntent().getStringExtra("track")) != null) {
			items = sched.getTrackItems(query);
			setTitle(sched.getTitle() + ": " + query);
		}
		lv.setList(items);
		lv.setMultiDay(true);
		setScheduleView(lv);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return false;
	}
	
	@Override
	protected void onItemHidden() {
		ArrayList<Schedule.Item> newlist = new ArrayList<Schedule.Item>();
		
		for (Schedule.Item item : items) {
			if (!item.isHidden())
				newlist.add(item);
		}
		
		if (newlist.isEmpty())
			finish();
		
		lv.setList(newlist);
		lv.refreshContents();
	}
}
