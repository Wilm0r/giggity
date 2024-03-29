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

import java.time.ZonedDateTime;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.TreeSet;

public class NowNext extends ScheduleListView implements ScheduleViewer {
	private Schedule sched;
	Context ctx;

	public NowNext(Context ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		sched = sched_;

		setHideDate(true);
		refreshContents();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void refreshContents() {
		ZonedDateTime now = ZonedDateTime.now();
		AbstractCollection<Schedule.Item> nextList;
		ArrayList fullList = new ArrayList();

		boolean byTime = true;

		/* Set the schedule's day to today so we don't show tomorrow's 
		 * stuff as "next". */
		sched.setDay(now);

		if (byTime) {
			nextList = new TreeSet<>();
		} else {
			nextList = new ArrayList<>();
		}

		if (sched.getDayNum() == -1) {
			fullList.add(this.getResources().getString(R.string.no_events_today));
		} else {
			fullList.add(this.getResources().getString(R.string.now));

			ZonedDateTime nextHour = now.plusHours(1);

			for (Schedule.Line tent : sched.getTents()) {
				boolean haveNext = false;  // Found at least one next item?
				for (Schedule.Item item : tent.getItems()) {
					if (item.getStartTimeZoned().isBefore(now) && item.getEndTimeZoned().isAfter(now)) {
						fullList.add(item);
					} else if (item.getStartTimeZoned().isAfter(now)) {
						if (byTime && haveNext && item.getStartTimeZoned().isAfter(nextHour)) {
							break;
						}
						nextList.add(item);
						haveNext = true;
						if (!byTime) {
							break;
						}
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
