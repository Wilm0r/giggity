package net.gaast.deoxide;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
import android.widget.ScrollView;
import android.widget.TableLayout;

public class NowNext extends ScrollView {
	private Schedule sched;
	private TableLayout table;
	
	public NowNext(Context ctx, Schedule sched_) {
		super(ctx);
		sched = sched_;
		
		Iterator<Schedule.Line> tenti;
		Date now = new Date(); //1218810000000L);
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
	
		addView(table);
	}
}
