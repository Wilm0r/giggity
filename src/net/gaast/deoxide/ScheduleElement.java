package net.gaast.deoxide;

import java.text.SimpleDateFormat;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ScheduleElement extends TextView {
	ScheduleDataItem item;
	
	public ScheduleElement(Activity ctx) {
		super(ctx);
		setGravity(Gravity.CENTER);
		setHeight(Deoxide.TentHeight);
		setTextColor(0xFFFFFFFF);
		setTextSize(8);
	}
	
	public void setItem(ScheduleDataItem item_) {
		item = item_;
	}
	
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			Dialog dlg = new Dialog(getContext());
			TextView desc = new TextView(getContext());
			CheckBox cb = new CheckBox(getContext());
			LinearLayout content = new LinearLayout(getContext());
			if (item == null) {
				desc.setText("Je hebt geklikt op " + getText());
			} else {
		    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");
		    	desc.setText(df.format(item.getStartTime().getTime()) + "-" +
		    			     df.format(item.getEndTime().getTime()) + ": " +
		    			     item.getDescription());
			}
			cb.setText("Remind me");
			dlg.setTitle(getText());
			content.setOrientation(LinearLayout.VERTICAL);
			content.addView(desc);
			content.addView(cb);
			dlg.setContentView(content);
			dlg.show();
			return true;
		}
		
		return false;
	}
}
