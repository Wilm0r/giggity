package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class TimeTable extends RelativeLayout {
	Deoxide app;
	Schedule sched;
	
	Gallery tents;
	ScrollView scroller;
	TableLayout table;
	
	public TimeTable(Activity ctx, Schedule sched_) {
		super(ctx);
		app = (Deoxide) ctx.getApplication();
    	sched = sched_;

    	//setOrientation(LinearLayout.VERTICAL);
    	
    	RelativeLayout.LayoutParams lp;
    	
    	lp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    	//lp.addRule(android.R.attr.layout_alignParentTop, TRUE);
    	//lp.addRule(android.R.attr.layout_alignParentLeft, TRUE);

    	scroller = new ScrollView(ctx);
    	table = new TableLayout(ctx);
    	scroller.addView(table);
    	addView(scroller, lp);
    	
    	tents = new Gallery(ctx);
    	tents.setAdapter(new TentListAdapter(ctx, sched.getTents()));
		//tents.setLayoutParams(new Gallery.LayoutParams(
        //        LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
    	lp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
    	//lp.addRule(android.R.attr.layout_alignParentTop, TRUE);
    	//lp.addRule(android.R.attr.layout_alignParentLeft, TRUE);
    	
    	addView(tents, lp);
    	
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
	}
	
	private void showTable(Schedule.Line line) {
		TableRow row;
		Iterator<Schedule.Item> itemi;
		
		table.removeAllViews();
		
		if (line == null) {
			return;
		}
		
		/* Insert one row as big as the tent chooser so everything is
		 * properly readable. */
		row = new TableRow(getContext());
		row.setMinimumHeight(tents.getHeight());
		table.addView(row);
		
		itemi = line.getItems().iterator();
		while (itemi.hasNext()) {
			Schedule.Item item = itemi.next();
			
			table.addView(new ItemRow(getContext(), item, ItemRow.NO_TENTNAME));
		}
		
		/* Wrap long titles. */
		table.setColumnShrinkable(1, true);
		
		scroller.scrollTo(0, 0);
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
	
	static public TextView makeText(Context ctx, String text) {
		TextView ret;
		
		ret = new TextView(ctx);
		ret.setText(text);
		ret.setPadding(2, 2, 2, 2);
		
		return ret;
	}

	static public class ItemRow extends TableRow implements OnClickListener {
		Schedule.Item item;
		int flags, titlecol;
		
		public final static int NO_TENTNAME = 1;
		
		public ItemRow(Context ctx, Schedule.Item item_, int flags_) {
			super(ctx);
			item = item_;
			flags = flags_;
			
			SimpleDateFormat tf = new SimpleDateFormat("HH:mm");
			
			titlecol = 1;
			
			if ((flags & NO_TENTNAME) == 0) {
				addView(makeText(ctx, item.getLine().getTitle()));
				titlecol ++;
			}
			
			addView(makeText(ctx, tf.format(item.getStartTime()) + "-" +
			                      tf.format(item.getEndTime())));
			
			addView(makeText(ctx, item.getTitle()));
			updateBold();
			
			setOnClickListener(this);
		}
		
		@Override
		public void onClick(View v) {
			EventDialog evd = new EventDialog(getContext(), item);
	    	evd.setOnDismissListener(new OnDismissListener() {
	   			public void onDismiss(DialogInterface dialog) {
	   				updateBold();
	   			}
	   		});
	    	evd.show();
		}
		
		private void updateBold() {
			((TextView)getChildAt(titlecol)).setTypeface(
					item.getRemind() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
		}
	}
}
