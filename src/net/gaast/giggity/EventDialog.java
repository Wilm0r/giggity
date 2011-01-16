package net.gaast.giggity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

public class EventDialog extends AlertDialog {
	private Schedule.Item item;
	private OnDismissListener dismissPassThru;

	private CheckBox cb;
	private StarsView sv; 

	public EventDialog(Context ctx_, Schedule.Item item_) {
		super(ctx_);

		item = item_;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
    	SimpleDateFormat df = new SimpleDateFormat("HH:mm");
    	Date now = new Date();
		
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
		bottomBox.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

		if (item.getStartTime().after(now)) {
			cb = new CheckBox(getContext());
			cb.setText("Remind me");
			cb.setChecked(item.getRemind());
			bottomBox.addView(cb, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
		} else {
			sv = new StarsView(getContext());
			sv.setNumStars(5);
			sv.setScore(item.getStars());
			/* Bigger surface for easier touching. */
			sv.setMinimumHeight(48);
			bottomBox.addView(sv, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 1));
		}
		
		LinkedList<Schedule.Item.Link> links = item.getLinks();
		if (links != null) {
			Iterator<Schedule.Item.Link> linki = links.listIterator();
			while (linki.hasNext()) {
				Schedule.Item.Link link = linki.next();
				LinkButton btn = new LinkButton(getContext(), link);
				bottomBox.addView(btn);
			}
		}

		content.addView(bottomBox, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));

		setTitle(item.getTitle());
    	setView(content);
    	super.setOnDismissListener(new OnDismissListener() {
   			public void onDismiss(DialogInterface dialog) {
   				if (cb != null)
   					item.setRemind(cb.isChecked());
   				if (sv != null)
   					item.setStars(sv.getScore());
   				if (dismissPassThru != null)
   					dismissPassThru.onDismiss(dialog);
   			}
   		});

		super.onCreate(savedInstanceState);
	}
	
	private class LinkButton extends ImageButton implements ImageButton.OnClickListener {
		Schedule.Item.Link link;
		
		public LinkButton(Context ctx, Schedule.Item.Link link_) {
			super(ctx);
			link = link_;
			setImageDrawable(link.getType().getIcon());
			setOnClickListener(this);
		}
		
		@Override
		public void onClick(View v) {
	    	Uri uri = Uri.parse(link.getUrl());
	    	Intent intent = new Intent(Intent.ACTION_VIEW, uri);
	    	intent.addCategory(Intent.CATEGORY_BROWSABLE);
	    	getContext().startActivity(intent);
		}
	}
	
	@Override
	public void setOnDismissListener(OnDismissListener l) {
		/* Multiple listeners are not supported, but this hack solves
		 * that problem for me. */
		dismissPassThru = l;
	}
}
