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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;

public class NowNext extends ScheduleListView implements ScheduleViewer {
	private Schedule sched;
	Context ctx;
	
	public NowNext(Context ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		sched = sched_;
		
		refreshContents();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void refreshContents() {
		Date now = new Date();
		LinkedList<Schedule.Item> nextList = new LinkedList<>();
		ArrayList fullList = new ArrayList();

		/* Set the schedule's day to today so we don't show tomorrow's 
		 * stuff as "next". */
		int i = 0;
		for (Date day : sched.getDays()) {
			long d = now.getTime() - day.getTime();
			if (d > 0 && d < 86400000) {
				sched.setDay(i);
				break;
			}
			i ++;
		}
		
		if (sched.getDay() == null) {
			fullList.add(this.getResources().getString(R.string.no_events_today));
		} else {
			fullList.add(this.getResources().getString(R.string.now));
			
			for (Schedule.Line tent : sched.getTents()) {
				for (Schedule.Item item : tent.getItems()) {
					if (item.getStartTime().before(now) && item.getEndTime().after(now)) {
						fullList.add(item);
					} else if (item.getStartTime().after(now)) {
						nextList.add(item);
						break;
					}
				}
			}
			
			fullList.add("\n\n" + this.getResources().getString(R.string.next));
			fullList.addAll(nextList);
			setShowNow(false);
		}
		
		setList(fullList);
	}
	
	@Override
	public boolean multiDay() {
		return true;
	}
}
