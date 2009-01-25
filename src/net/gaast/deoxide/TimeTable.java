package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class TimeTable extends LinearLayout {
	Deoxide app;
	Schedule sched;
	
	Gallery tents;
	ScrollView scroller;
	TableLayout table;
	
	public TimeTable(Activity ctx, Schedule sched_) {
		super(ctx);
		app = (Deoxide) ctx.getApplication();
    	sched = sched_;

    	setOrientation(LinearLayout.VERTICAL);
    	
    	tents = new Gallery(ctx);
    	tents.setAdapter(new TentListAdapter(ctx, sched.getTents()));
		//tents.setLayoutParams(new Gallery.LayoutParams(
        //        LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
    	addView(tents);
    	
    	tents.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				showTable((Schedule.Line) tents.getItemAtPosition(position));
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				showTable(null);
			}
    	});
    	
    	scroller = new ScrollView(ctx);
    	table = new TableLayout(ctx);
    	scroller.addView(table);
    	addView(scroller);
    	
    	//showTable(sched.getTents().get(0));
	}
	
	private TextView makeText(String text) {
		TextView ret;
		
		ret = new TextView(getContext());
		ret.setText(text);
		ret.setPadding(2, 2, 2, 2);
		//ret.set
		
		return ret;
	}
	
	private void showTable(Schedule.Line line) {
		SimpleDateFormat tf = new SimpleDateFormat("HH:mm");
		TableRow row;
		TextView cell;
		Iterator<Schedule.Item> itemi;
		
		table.removeAllViews();
		
		if (line == null) {
			return;
		}
		
		itemi = line.getItems().iterator();
		
		while (itemi.hasNext()) {
			Schedule.Item item = itemi.next();
			
			row = new TableRow(getContext());
			row.addView(makeText(tf.format(item.getStartTime()) + "-" +
					             tf.format(item.getEndTime())));
			row.addView(makeText(item.getTitle()));
			table.addView(row);
		}
		
		table.setColumnShrinkable(1, true);
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
			//ret.setHeight(42);
			//ret.setWidth(72);
			ret.setTextSize(10);
			
			return ret;
		}
		
		
	}
}
