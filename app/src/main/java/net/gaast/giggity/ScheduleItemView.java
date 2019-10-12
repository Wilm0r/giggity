package net.gaast.giggity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressLint({"SimpleDateFormat", "SetTextI18n"})
public class ScheduleItemView extends LinearLayout {
	public static final int COMPACT = 1;
	public static final int SHOW_REMIND = 2;
	public static final int SHOW_NOW = 4;
	public static final int SHORT_TITLE = 8;
	public static final int HIDE_ENDTIME = 16;
	public static final int HIDE_DATE = 32;

	public ScheduleItemView(Context ctx, Schedule.Item item, int flags) {
		super(ctx);

		inflate(ctx, R.layout.schedule_item, this);
		
		int n = 0;
		Format df = new SimpleDateFormat("EE d MMM");
		Format tf = new SimpleDateFormat("HH:mm");
		
		TextView title, room, time, date;
		RelativeLayout.LayoutParams p;
		
		time = findViewById(R.id.time);
		String timeText = tf.format(item.getStartTime());
		if ((flags & HIDE_ENDTIME) == 0) {
			timeText += "–" +  // en-dash
			            tf.format(item.getEndTime());
		}
		time.setText(timeText);

		title = findViewById(R.id.title);
		title.setText(item.getTitle());
		if ((flags & SHORT_TITLE) > 0) {
			title.setLines(1);
			title.setEllipsize(TextUtils.TruncateAt.END);
		}

		date = findViewById(R.id.date);
		room = findViewById(R.id.room);

		if ((flags & COMPACT) == 0) {
			time.setTextColor(getResources().getColor(R.color.dark_text));
			date.setText(df.format(item.getStartTime()) + "  ");
			room.setText(item.getLine().getTitle());
		} else {
			date.setVisibility(GONE);
			room.setVisibility(GONE);
		}

		if ((flags & HIDE_DATE) != 0) {
			date.setVisibility(GONE);
		}

		if ((flags & SHOW_REMIND) != 0 && item.getRemind()) {
			//setBackgroundColor(0x3300FF00);
			View v = findViewById(R.id.titlecolumn);
			v.setBackgroundResource(R.drawable.schedule_item_remind_background);
			title.setTextColor(getResources().getColor(R.color.light_text));
			room.setTextColor(getResources().getColor(R.color.light_text));
		} else if ((flags & SHOW_NOW) != 0 && item.compareTo(new Date()) == 0) {
			setBackgroundColor(0x11FFFFFF);
		} else {
			setBackgroundColor(0x00000000);
		}

		if (item.isHidden()) {
			setAlpha(.5F);
		} else {
			setAlpha(1F);
		}
	}
}
