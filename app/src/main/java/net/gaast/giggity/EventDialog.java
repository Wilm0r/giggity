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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import org.xml.sax.XMLReader;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class EventDialog extends Dialog implements OnDismissListener {
	private Context ctx;
	private Schedule.Item item;
	private OnDismissListener dismissPassThru;

	private CheckBox cb;
	private StarsView sv;

	private Giggity app;

	public EventDialog(Context ctx_, Schedule.Item item_) {
		super(ctx_);

		ctx = ctx_;
		item = item_;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		/* Rendering our own title (ScheduleItemView) */
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(genDialog(false));
		
		super.setOnDismissListener(this);

		super.onCreate(savedInstanceState);
	}
	
	public View genDialog(boolean big) {
		app = (Giggity) ctx.getApplicationContext();
		View v;
		final View c = getLayoutInflater().inflate(R.layout.event_dialog, null);
		TextView t;
		Format tf = new SimpleDateFormat("HH:mm");
		
		t = (TextView) c.findViewById(R.id.title);
		t.setText(item.getTitle());

		t = (TextView) c.findViewById(R.id.subtitle);
		if (item.getSubtitle() != null) {
			t.setText(item.getSubtitle());
		} else {
			t.setVisibility(View.GONE);
		}
		
		t = (TextView) c.findViewById(R.id.room);
		t.setText(item.getLine().getTitle());
		
		t = (TextView) c.findViewById(R.id.time);
		t.setText(item.getSchedule().getDayFormat().format(item.getStartTime()) + " " +
		          tf.format(item.getStartTime()) + "-" + tf.format(item.getEndTime()));
		
		t = (TextView) c.findViewById(R.id.track);
		if (item.getTrack() != null) {
			t.setText(item.getTrack());
		} else {
			t.setVisibility(View.GONE);
			v = c.findViewById(R.id.headTrack);
			v.setVisibility(View.GONE);
		}
		
		t = (TextView) c.findViewById(R.id.speaker);
		if (item.getSpeakers() != null) {
			String list = "";
			
			for (String i : item.getSpeakers())
				list += i + ", ";
			list = list.replaceAll(", $", "");
			
			t.setText(list);
			
			if (item.getSpeakers().size() > 1) {
				t = (TextView) c.findViewById(R.id.headSpeaker);
				t.setText(R.string.speakers);
			}
		} else {
			t.setVisibility(View.GONE);
			v = c.findViewById(R.id.headSpeaker);
			v.setVisibility(View.GONE);
		}

		String overlaps = null;
		
		for (Schedule.Item other : app.getRemindItems()) {
			if (item != other && other.overlaps(item)) {
				if (overlaps == null)
					overlaps = ctx.getResources().getString(R.string.overlap) + " "; 
				overlaps += other.getTitle() +
				         " (" + tf.format(other.getStartTime()) + "-" + tf.format(other.getEndTime()) + "), ";
			} else if (other.getStartTime().after(item.getEndTime())){
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
		String text = item.getDescription();
		if (text.contains("<p>")) {
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
		} else {
			t.setText(text);
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

					app.setShadow(header, !subHeader.getLocalVisibleRect(scrollBounds));
				}
			});
	}

		/* Bottom box is some stuff next to each other: Remind checkbox/stars, web buttons. */
		LinearLayout bottomBox = (LinearLayout) c.findViewById(R.id.bottomBox);

		if (item.getStartTime().after(new Date())) {
			/* Show "Remind me" only if the event is in the future. */
			cb = new CheckBox(ctx);
			cb.setText(R.string.remind_me);
			cb.setChecked(item.getRemind());
			bottomBox.addView(cb, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));
		} else {
			/* Otherwise, stars to rate the event. Using my own StarsView because the stock one's too huge. */
			sv = new StarsView(ctx);
			sv.setNumStars(5);
			sv.setScore(item.getStars());
			/* Bigger surface for easier touching. */
			sv.setMinimumHeight(48);
			bottomBox.addView(sv, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));
		}
		
		/* Web buttons on the right, if we have types. */
		if (item.getSchedule().hasLinkTypes()) {
			LinkedList<Schedule.Item.Link> links = item.getLinks();
			if (links != null) {
				LinearLayout webButtons = new LinearLayout(ctx);
				for (Schedule.Item.Link link : links) {
					LinkButton btn = new LinkButton(ctx, link);
					webButtons.addView(btn);
				}
				bottomBox.addView(webButtons, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0));
			}
		} else if (item.getLinks() != null) {
			ViewGroup g = (ViewGroup) c.findViewById(R.id.links);
			for (Schedule.Item.Link link : item.getLinks()) {
				LinkButton btn = new LinkButton(ctx, link);
				btn.showUrl(true);
				g.addView(btn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));
			}
			g.setVisibility(View.VISIBLE);
		}
		
		ImageButton delButton;
		if (!item.isHidden()) {
			delButton = new HideButton(ctx);
		} else {
			delButton = new UnhideButton(ctx);
		}
		bottomBox.addView(delButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0));

		return c;
	}
	
	private class LinkButton extends FrameLayout implements ImageButton.OnClickListener {
		Schedule.Item.Link link;
		
		public LinkButton(Context ctx, Schedule.Item.Link link_) {
			super(ctx);
			link = link_;
			showUrl(false);
		}
		
		public void showUrl(boolean show) {
			this.removeAllViews();
			if (show) {
				TextView url = new TextView(ctx);
				url.setText("• " + link.getUrl());
				url.setEllipsize(TextUtils.TruncateAt.END);
				url.setOnClickListener(this);
				url.setSingleLine();
				url.setPadding(0, 3, 0, 3);
				addView(url);
			} else {
				ImageButton btn = new ImageButton(ctx);
				btn.setImageDrawable(link.getType().getIcon());
				btn.setOnClickListener(this);
				addView(btn);
			}
		}
		
		@Override
		public void onClick(View v) {
			Uri uri = Uri.parse(link.getUrl());
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			intent.addCategory(Intent.CATEGORY_BROWSABLE);
			ctx.startActivity(intent);
		}
	}
	
	private class HideButton extends ImageButton implements ImageButton.OnClickListener {
		protected int title = R.string.hide_what;
		protected boolean newValue = true;

		public HideButton(Context context) {
			super(context);
			setImageResource(android.R.drawable.ic_menu_delete);
			setPadding(0, 0, 0, 0);
			setOnClickListener(this);
			setBackgroundResource(android.R.color.transparent);
		}

		@Override
		public void onClick(View moi) {
			CharSequence[] delWhat;
			
			/* BOO to setItems() for not supporting something more flexible than a static array. */
			if (item.getTrack() != null) {
				delWhat = new CharSequence[3];
				delWhat[2] = ctx.getResources().getString(R.string.hide_track);
			} else {
				delWhat = new CharSequence[2];
			}
			delWhat[0] = ctx.getResources().getString(R.string.hide_item);
			delWhat[1] = ctx.getResources().getString(R.string.hide_room);

			AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
			builder.setTitle(title);
			builder.setItems(delWhat, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int what) {
				switch (what) {
				case 0:
					item.setHidden(newValue);
					break;
				case 1:
					for (Schedule.Item other : item.getLine().getItems())
						other.setHidden(newValue);
					break;
				case 2:
					for (Schedule.Line line : item.getSchedule().getTents())
						for (Schedule.Item other : line.getItems())
							if (other.getTrack() != null && other.getTrack().equals(item.getTrack()))
								other.setHidden(newValue);
					break;
				}
				try {
					ScheduleViewActivity sva = (ScheduleViewActivity) ctx;
					sva.onItemHidden();
				} catch (ClassCastException e) {
				}
				EventDialog.this.dismiss();
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
	
	@Override
	public void setOnDismissListener(OnDismissListener l) {
		/* Multiple listeners are not supported, but this hack solves
		 * that problem for me. */
		dismissPassThru = l;
	}

	public void onDismiss(DialogInterface dialog) {
		if (cb != null) {
			item.setRemind(cb.isChecked());
		}
		if (sv != null)
			item.setStars(sv.getScore());
		if (dismissPassThru != null)
			dismissPassThru.onDismiss(dialog);
	}

	@Override
	public void show() {
		ScheduleViewActivity sva = (ScheduleViewActivity) ctx;
		sva.setEventDialog(this, item);
	}
}
