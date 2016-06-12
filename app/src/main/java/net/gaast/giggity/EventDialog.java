/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.xml.sax.XMLReader;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/* Mind you, one day this was an actual Dialog, but not anymore technically. It's just a pretty
   densely populated view used in two different ways (depending on whether we're on a tablet. */
@SuppressLint({"SimpleDateFormat", "SetTextI18n"})
public class EventDialog extends FrameLayout {
	private Context ctx_;
	private Giggity app_;

	private Schedule.Item item_;

	private CheckBox cb_;

	public EventDialog(Context ctx, Schedule.Item item) {
		super(ctx);

		ctx_ = ctx;
		item_ = item;
		app_ = (Giggity) ctx_.getApplicationContext();

		View v;

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View c = inflater.inflate(R.layout.event_dialog, null);
		TextView t;
		Format tf = new SimpleDateFormat("HH:mm");
		
		t = (TextView) c.findViewById(R.id.title);
		t.setText(item_.getTitle());

		t = (TextView) c.findViewById(R.id.subtitle);
		if (item_.getSubtitle() != null) {
			t.setText(item_.getSubtitle());
		} else {
			t.setVisibility(View.GONE);
		}
		
		t = (TextView) c.findViewById(R.id.room);
		t.setText(item_.getLine().getTitle());

		if (item_.getLine().getLocation() != null) {
			t = (TextView) c.findViewById(R.id.room);
			t.setPaintFlags(t.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
			t.setOnClickListener(ScheduleUI.locationClickListener(getContext(), item_.getLine()));
		}
		
		t = (TextView) c.findViewById(R.id.time);
		t.setText(item_.getSchedule().getDayFormat().format(item_.getStartTime()) + " " +
		          tf.format(item_.getStartTime()) + "-" + tf.format(item_.getEndTime()));
		
		t = (TextView) c.findViewById(R.id.track);
		if (item_.getTrack() != null) {
			t.setText(item_.getTrack());
		} else {
			t.setVisibility(View.GONE);
			v = c.findViewById(R.id.headTrack);
			v.setVisibility(View.GONE);
		}
		
		t = (TextView) c.findViewById(R.id.speaker);
		if (item_.getSpeakers() != null) {
			String list = "";
			
			for (String i : item_.getSpeakers())
				list += i + ", ";
			list = list.replaceAll(", $", "");
			
			t.setText(list);
			
			if (item_.getSpeakers().size() > 1) {
				t = (TextView) c.findViewById(R.id.headSpeaker);
				t.setText(R.string.speakers);
			}
		} else {
			t.setVisibility(View.GONE);
			v = c.findViewById(R.id.headSpeaker);
			v.setVisibility(View.GONE);
		}

		String overlaps = null;
		
		for (Schedule.Item other : app_.getRemindItems()) {
			if (item_ != other && other.overlaps(item_)) {
				if (overlaps == null)
					overlaps = ctx_.getResources().getString(R.string.overlap) + " ";
				overlaps += other.getTitle() +
				         " (" + tf.format(other.getStartTime()) + "-" + tf.format(other.getEndTime()) + "), ";
			} else if (other.getStartTime().after(item_.getEndTime())){
				break;
			}
		}
		
		t = (TextView) c.findViewById(R.id.alert);
		if (overlaps != null) {
			t.setText(overlaps.replaceAll(", $", ""));
		} else {
			t.setVisibility(View.GONE);
			v = c.findViewById(R.id.headAlert);
			v.setVisibility(View.GONE);
		}
		
		t = (TextView) c.findViewById(R.id.description);
		String text = item_.getDescription();
		if (text.startsWith("<") || text.contains("<p>")) {
			/* This parser is VERY limited, results aren't great, but let's give it a shot.
			   I'd really like to avoid using a full-blown WebView.. */
			Html.TagHandler th = new Html.TagHandler() {
				@Override
				public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
					if (tag.equals("li")) {
						if (opening) {
							output.append(" • ");
						} else {
							output.append("\n");
						}
					} else if (tag.equals("ul") || tag.equals("ol")) {
						/* For both opening and closing */
						output.append("\n");
					}
				}
			};
			Spanned formatted = Html.fromHtml(text, null, th);
			t.setText(formatted);
			t.setMovementMethod(LinkMovementMethod.getInstance());
		} else {
			t.setText(text);
		}

		if (item_.getLinks() != null) {
			ViewGroup g = (ViewGroup) c.findViewById(R.id.links);
			for (Schedule.Link link : item_.getLinks()) {
				LinkButton btn = new LinkButton(ctx_, link);
				g.addView(btn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));
			}
			g.setVisibility(View.VISIBLE);
		}

		if (android.os.Build.VERSION.SDK_INT >= 21) {
			/* Lollipop+. I think owners of older devs will survive without drop shadows, right? :> */
			/* TODO: Remove check above if I find a way to do drop shadows pre-L. */
			final ScrollView scr = (ScrollView) c.findViewById(R.id.scrollDescription);
			scr.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
				@Override
				public void onScrollChanged() {
					Rect scrollBounds = new Rect();
					scr.getHitRect(scrollBounds);
					View subHeader = c.findViewById(R.id.subHeader);
					View header = c.findViewById(R.id.header);

					app_.setShadow(header, !subHeader.getLocalVisibleRect(scrollBounds));
				}
			});
	}

		/* Bottom box used to be a bunch of things but now just the remind checkbox + delete icon. */
		LinearLayout bottomBox = (LinearLayout) c.findViewById(R.id.bottomBox);

		cb_ = new CheckBox(ctx_);
		cb_.setText(R.string.remind_me);
		cb_.setChecked(item_.getRemind());
		bottomBox.addView(cb_, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));

		cb_.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				item_.setRemind(isChecked);
				try {
					ScheduleViewActivity sva = (ScheduleViewActivity) getContext();
					sva.refreshItems();
				} catch (ClassCastException e) {
					/* We're our own activity, we can safely skip this operation (will be done after
					   finish instead). */
				}
			}
		});

		ImageButton delButton;
		if (!item_.isHidden()) {
			delButton = new HideButton(ctx_);
		} else {
			delButton = new UnhideButton(ctx_);
		}
		bottomBox.addView(delButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0));

		addView(c);
	}

	/* The old Giggity-native format supported image buttons. Not doing that anymore, or if I do
	* I'll refactor it. For now it's links with at most a title using the same Schedule.Link class
	* used by conference metadata. */
	private class LinkButton extends FrameLayout implements ImageButton.OnClickListener {
		Schedule.Link link;
		
		public LinkButton(Context ctx, Schedule.Link link_) {
			super(ctx);
			link = link_;
			TextView url = new TextView(ctx);
			url.setText(link.getTitle());
			url.setOnClickListener(this);
			url.setPaintFlags(url.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
			url.setTextColor(getResources().getColor(R.color.accent));
			app_.setPadding(url, 0, 3, 0, 3);
			addView(url);
		}

		@Override
		public void onClick(View v) {
			Uri uri = Uri.parse(link.getUrl());
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			intent.addCategory(Intent.CATEGORY_BROWSABLE);
			ctx_.startActivity(intent);
		}
	}
	
	private class HideButton extends ImageButton implements ImageButton.OnClickListener {
		protected int title = R.string.hide_what;
		protected boolean newValue = true;

		public HideButton(Context context) {
			super(context);
			setImageResource(android.R.drawable.ic_menu_delete);
			app_.setPadding(this, 0, 0, 0, 0);
			setOnClickListener(this);
			setBackgroundResource(android.R.color.transparent);
		}

		@Override
		public void onClick(View moi) {
			ArrayList<CharSequence> delWhat = new ArrayList<>();
			delWhat.add(ctx_.getResources().getString(R.string.hide_item));
			delWhat.add(ctx_.getResources().getString(R.string.hide_room));
			if (item_.getTrack() != null) {
				delWhat.add(ctx_.getResources().getString(R.string.hide_track));
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(ctx_);
			builder.setTitle(title);
			builder.setItems(delWhat.toArray(new CharSequence[delWhat.size()]), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int what) {
					Schedule sched = item_.getSchedule();
					boolean showh = sched.getShowHidden();
					sched.setShowHidden(true);  // Needed for option 1 and 2 below to work.
					switch (what) {
					case 0:
						item_.setHidden(newValue);
						break;
					case 1:
						for (Schedule.Item other : item_.getLine().getItems())
							other.setHidden(newValue);
						break;
					case 2:
						for (Schedule.Item other : sched.getTrackItems(item_.getTrack()))
							other.setHidden(newValue);
						break;
					}
					sched.setShowHidden(showh);

					/* Following is a bit odd. If we're on a tablet, current activity is SVA in
					   which case UI needs to be updated. If not, in case of deletion the user will
					   probably want to return to the schedule viewer instead of the thing they
					   just opted to delete. */
					if (ctx_.getClass() == ScheduleViewActivity.class) {
						ScheduleViewActivity sva = (ScheduleViewActivity) ctx_;
						sva.onItemHidden();
						/* TODO: With tablets and at least BlockSchedule this scrolls back to the
						   top. That's annoying. */
					} else if (ctx_.getClass() == ScheduleItemActivity.class) {
						if (newValue) {
							Activity sia = (Activity) ctx_;
							sia.finish();
						}
					}
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
		}
	}

	private class UnhideButton extends HideButton {
		public UnhideButton(Context context) {
			super(context);
			setImageResource(android.R.drawable.ic_menu_revert);
			title = R.string.unhide_what;
			newValue = false;
		}
	}
}
