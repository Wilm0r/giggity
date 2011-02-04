package net.gaast.giggity;

import java.util.ArrayList;
import java.util.Date;
import java.util.TreeSet;

import android.content.Context;

public class MyItemsView extends ScheduleListView implements ScheduleViewer {
	private Schedule sched;
	Context ctx;
	
	public MyItemsView(Context ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		sched = sched_;
		
		refreshContents();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void refreshContents() {
		ArrayList fullList = new ArrayList();
		TreeSet<Schedule.Item> seen = new TreeSet<Schedule.Item>();
		TreeSet<Schedule.Item> coming = new TreeSet<Schedule.Item>();
		Date now = new Date();

		for (Schedule.Line tent : sched.getTents()) {
			for (Schedule.Item item : tent.getItems()) {
				if (item.getRemind() || item.getStars() > 0) {
					if (item.compareTo(now) >= 0)
						seen.add(item);
					else
						coming.add(item);
				}
			}
		}
		if (coming.size() > 0) {
			fullList.add("Coming up:");
			fullList.addAll(coming);
		}
		if (seen.size() > 0) {
			fullList.add((coming.size() > 0 ? "\n" : "") + "Seen so far:");
			fullList.addAll(seen);
		}
		if (fullList.isEmpty())
			fullList.add("No items marked yet.");
		setShowRemind(false);
		setList(fullList);
	}
}

