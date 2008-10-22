package net.gaast.deoxide;

import java.text.SimpleDateFormat;

import android.app.Activity;
import android.app.Dialog;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ScheduleElement extends TextView implements OnClickListener {
	ScheduleDataItem item;
	
	public ScheduleElement(Activity ctx) {
		super(ctx);
		setGravity(Gravity.CENTER_HORIZONTAL);
		setHeight(Deoxide.TentHeight);
		setTextColor(0xFFFFFFFF);
		setPadding(0, 3, 0, 0);
		setTextSize(8);
		setOnClickListener(this);
	}
	
	public void setItem(ScheduleDataItem item_) {
		item = item_;
	}
	
	public void onClick(View v) {
		if (item != null) {
			Dialog dlg = new Dialog(getContext());
			LinearLayout content = new LinearLayout(getContext());
			TextView desc = new TextView(getContext());
			CheckBox cb = new CheckBox(getContext());
	    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");
	    	
			dlg.setTitle(getText());
	    	desc.setText(df.format(item.getStartTime().getTime()) + "-" +
	    			     df.format(item.getEndTime().getTime()) + ": " +
	    			     item.getDescription());
			cb.setText("Remind me" + getTotalPaddingTop());
	
			content.setOrientation(LinearLayout.VERTICAL);
			content.addView(desc);
			content.addView(cb);
			dlg.setContentView(content);
			dlg.show();
		}
	}
}
