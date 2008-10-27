package net.gaast.deoxide;

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.ImageButton;
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

		LinearLayout bottomBox = new LinearLayout(getContext());
		bottomBox.setGravity(Gravity.RIGHT);

		CheckBox cb = new CheckBox(getContext());
		cb.setText("Remind me");
		bottomBox.addView(cb, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
		
		LinkedList<ScheduleDataItemLink> links = item.getLinks();
		if (links != null) {
			Iterator<ScheduleDataItemLink> linki = links.listIterator();
			while (linki.hasNext()) {
				ScheduleDataItemLink link = linki.next();
				LinkButton btn = new LinkButton(getContext(), link);
				bottomBox.addView(btn);
			}
		}

		content.addView(bottomBox, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));

    	new AlertDialog.Builder(getContext())
			.setTitle(getText())
    		.setView(content)
    		.show();
	}
	
	private class LinkButton extends ImageButton implements OnClickListener {
		ScheduleDataItemLink link;
		
		public LinkButton(Context ctx, ScheduleDataItemLink link_) {
			super(ctx);
			link = link_;
			setImageDrawable(link.getType().getIcon());
		}
		
		public void onClick(View v) {
	    	Uri uri = Uri.parse(link.getUrl());
	    	Intent intent = new Intent(Intent.ACTION_VIEW, uri);
	    	intent.addCategory(Intent.CATEGORY_BROWSABLE);
	    	getContext().startActivity(intent);
		}
	}
}
