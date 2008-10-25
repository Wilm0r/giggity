package net.gaast.deoxide;

import java.text.SimpleDateFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class BlockScheduleElement extends TextView implements OnClickListener {
	ScheduleDataItem item;
	
	public BlockScheduleElement(Activity ctx) {
		super(ctx);
		setGravity(Gravity.CENTER_HORIZONTAL);
		setHeight(Deoxide.TentHeight);
		setTextColor(0xFFFFFFFF);
		setPadding(0, 3, 0, 0);
		setTextSize(8);
	}
	
	public void setItem(ScheduleDataItem item_) {
		item = item_;
		setOnClickListener(this);
	}
	
	public void onClick(View v) {
    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");

		LinearLayout content = new LinearLayout(getContext());
		content.setOrientation(LinearLayout.VERTICAL);

		TextView desc = new TextView(getContext());
		desc.setText(df.format(item.getStartTime().getTime()) + "-" +
	    		     df.format(item.getEndTime().getTime()) + ": " +
	    		     item.getDescription());

		ScrollView descscr = new ScrollView(getContext());
		descscr.addView(desc);
		content.addView(descscr, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 2));

		CheckBox cb = new CheckBox(getContext());
		cb.setText("Remind me");
		content.addView(cb, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));

    	new AlertDialog.Builder(getContext())
			.setTitle(getText())
    		.setView(content)
    		.show();
	}
}
