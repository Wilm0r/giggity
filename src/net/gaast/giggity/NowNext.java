package net.gaast.giggity;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
import android.widget.ScrollView;
import android.widget.TableLayout;

public class NowNext extends ScrollView implements ScheduleViewer {
	private Schedule sched;
	private TableLayout table;
	Context ctx;
	
	public NowNext(Context ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		sched = sched_;
		
		refreshContents();
	}

	@Override
	public void refreshContents() {
		Iterator<Schedule.Line> tenti;
		Date now = new Date();
		LinkedList<Schedule.Item> nextList = new LinkedList<Schedule.Item>();
		Iterator<Schedule.Item> itemi;
		Schedule.Item item = null;

		table = new TableLayout(getContext());
		
		table.addView(TimeTable.makeText(ctx, "Now:"));
		
		tenti = sched.getTents().iterator();
		while (tenti.hasNext()) {
			Schedule.Line tent = tenti.next();
			itemi = tent.getItems().iterator();
			
			while (itemi.hasNext()) {
				item = itemi.next();
				if (item.getStartTime().before(now) && item.getEndTime().after(now)) {
					table.addView(new TimeTable.ItemRow(getContext(), item, 0));
				} else if (item.getStartTime().after(now)) {
					nextList.add(item);
					break;
				}
			}
		}
		
		table.addView(TimeTable.makeText(ctx, "Next:"));
		
		itemi = nextList.iterator();
		while (itemi.hasNext()) {
			item = itemi.next();
			table.addView(new TimeTable.ItemRow(getContext(), item, 0));
		}
		
		table.setColumnShrinkable(2, true);
	
		this.removeAllViews();
		addView(table);
	}
}
