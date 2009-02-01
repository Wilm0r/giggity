package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class NowNext extends ScrollView {
	private Schedule sched;
	private TableLayout table;
	private SimpleDateFormat tf;
	
	public NowNext(Context ctx, Schedule sched_) {
		super(ctx);
		sched = sched_;
		
		Iterator<Schedule.Line> tenti;
		Date now = new Date(); //1218810000000L);
		LinkedList<Schedule.Item> nextList = new LinkedList<Schedule.Item>();
		Iterator<Schedule.Item> itemi;
		Schedule.Item item = null;
		
		tf = new SimpleDateFormat("HH:mm");
		table = new TableLayout(getContext());
		
		table.addView(makeText("Now:"));
		
		tenti = sched.getTents().iterator();
		while (tenti.hasNext()) {
			Schedule.Line tent = tenti.next();
			itemi = tent.getItems().iterator();
			
			while (itemi.hasNext()) {
				item = itemi.next();
				if (item.getStartTime().before(now) && item.getEndTime().after(now)) {
					table.addView(itemRow(item));
				} else if (item.getStartTime().after(now)) {
					nextList.add(item);
					break;
				}
			}
		}
		
		table.addView(makeText("Next:"));
		
		itemi = nextList.iterator();
		while (itemi.hasNext()) {
			item = itemi.next();
			table.addView(itemRow(item));
		}
		
		table.setColumnShrinkable(2, true);
	
		addView(table);
	}
	
	private TextView makeText(String text) {
		TextView ret;
		
		ret = new TextView(getContext());
		ret.setText(text);
		ret.setPadding(2, 2, 2, 2);
		
		return ret;
	}

	private TableRow itemRow(Schedule.Item item) {
		TableRow row = new TableRow(getContext());
		TextView cell;
		
		row.addView(makeText(item.getLine().getTitle()));
		row.addView(makeText(tf.format(item.getStartTime()) + "-" +
		                     tf.format(item.getEndTime())));
		
		cell = makeText(item.getTitle());
		if (item.getRemind()) 
			cell.setTypeface(Typeface.DEFAULT_BOLD);
		row.addView(cell);
		
		row.setTag(item);
		row.setOnClickListener(new TableRow.OnClickListener() {
			@Override
			public void onClick(View v) {
				Schedule.Item item = (Schedule.Item) v.getTag();
				EventDialog evd = new EventDialog(getContext(), item);
				evd.show();
			}
		});
		
		return row;
	}
}
