/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2021 Wilmer van der Gaast <wilmer@gaast.net>
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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
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

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.gaast.giggity.Schedule.RoomStatus.FULL;

/* Mind you, one day this was an actual Dialog, but not anymore technically. It's just a pretty
   densely populated view used in two different ways (depending on whether we're on a tablet. */
@SuppressLint({"SimpleDateFormat", "SetTextI18n"})
public class EventDialog extends FrameLayout {
	private Context ctx_;
	private Giggity app_;
	private View root;

	private Schedule.Item item_;

	private CheckBox cb_;

	public EventDialog(Context ctx, Schedule.Item item, String searchQuery) {
		super(ctx);

		ctx_ = ctx;
		item_ = item;
		app_ = (Giggity) ctx_.getApplicationContext();

		View v;

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		root = inflater.inflate(R.layout.event_dialog, null);
		TextView t;
		Format tf = new SimpleDateFormat("HH:mm");
		
		t = root.findViewById(R.id.title);
		t.setText(item_.getTitle());

		t = root.findViewById(R.id.subtitle);
		if (item_.getSubtitle() != null) {
			t.setText(item_.getSubtitle());
		} else {
			t.setVisibility(View.GONE);
		}
		
		t = root.findViewById(R.id.room);
		t.setText(item_.getLine().getTitle());

		if (item_.getLine().getLocation() != null) {
			t = root.findViewById(R.id.room);
			t.setPaintFlags(t.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
			t.setOnClickListener(ScheduleUI.locationClickListener(getContext(), item_.getLine()));
		}

		if (item_.getLine().getRoomStatus().compareTo(FULL) >= 0) {
			t.setTextColor(getResources().getColor(R.color.evd_room_busy));
		}

		if (item_.getLanguage() != null) {
			t = root.findViewById(R.id.language);
			t.setText(" (" + item_.getLanguage() + ")");
		} else {
			root.findViewById(R.id.language).setVisibility(View.GONE);
		}

		t = root.findViewById(R.id.time);
		t.setText(item_.getSchedule().getDayFormat().format(item_.getStartTimeZoned()) + " " +
		          tf.format(item_.getStartTime()) + "–" + tf.format(item_.getEndTime()));
		
		t = root.findViewById(R.id.track);
		if (item_.getTrack() != null) {
			t.setText(item_.getTrack().getTitle());
		} else {
			t.setVisibility(View.GONE);
			v = root.findViewById(R.id.headTrack);
			v.setVisibility(View.GONE);
		}
		
		t = root.findViewById(R.id.speaker);
		if (item_.getSpeakers() != null) {
			t.setText(TextUtils.join(", ", item_.getSpeakers()));
			
			if (item_.getSpeakers().size() > 1) {
				t = root.findViewById(R.id.headSpeaker);
				t.setText(R.string.speakers);
			}
		} else {
			t.setVisibility(View.GONE);
			v = root.findViewById(R.id.headSpeaker);
			v.setVisibility(View.GONE);
		}

		String overlaps = null;
		
		for (Schedule.Item other : app_.getRemindItems()) {
			if (item_ != other && other.overlaps(item_)) {
				if (overlaps == null)
					overlaps = ctx_.getResources().getString(R.string.overlap) + " ";
				overlaps += other.getTitle() +
				         " (" + tf.format(other.getStartTime()) + "–" + tf.format(other.getEndTime()) + "), ";
			} else if (other.getStartTime().after(item_.getEndTime())){
				break;
			}
		}
		
		t = root.findViewById(R.id.alert);
		if (overlaps != null) {
			t.setText(overlaps.replaceAll(", $", ""));
		} else {
			t.setVisibility(View.GONE);
			v = root.findViewById(R.id.headAlert);
			v.setVisibility(View.GONE);
		}
		
		Spannable desc = new SpannableString(item.getDescriptionSpanned(ctx));
		if (searchQuery != null && !searchQuery.isEmpty()) {
			String raw = desc.toString().toLowerCase();
			Matcher m = Pattern.compile("(\"([^\"]*)\"|'([^']*)'|(\\S+))").matcher(searchQuery.toLowerCase());
			while (m.find()) {
				String term = m.group();
				// Couldn't figure out how to get the inner alternate capture groups to all go into m.group(SAMENUMBER), PCRE does that with (?|...) ?
				if (term.length() > 2 && "\"'".contains(term.substring(0, 1)) &&
				    term.substring(0, 1).equals(term.substring(term.length() - 1))) {
					term = term.substring(1, term.length() - 1);
				}

				if (term.isEmpty()) continue;
				int start = -1;
				while (true) {
					start = raw.indexOf(term, start + 1);
					if (start == -1) {
						break;
					}
					desc.setSpan(new BackgroundColorSpan(app_.getColor(R.color.evd_highlight_bg)), start, start + term.length(), 0);
					desc.setSpan(new ForegroundColorSpan(app_.getColor(R.color.evd_highlight_fg)), start, start + term.length(), 0);
				}
			}
		}

		t = root.findViewById(R.id.description);
		t.setText(desc);
		t.setMovementMethod(LinkMovementMethod.getInstance());

		/* This is frustrating: a TextView cannot support text selection and clickable links at the
		 * same time except if you do horrible things like reimplementing your own MovementMethod.
		 * If you try to do it anyway things will behave strangely and eventually crash with a stack
		 * trace entirely within the Android framework.
		 *
		 * I'm working around this by switching off ability to click on links as soon as the user
		 * long-presses anywhere. I think this is a reasonable compromise.. */
		t.setLongClickable(true);
		t.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				v.setFocusable(true);
				((TextView) v).setTextIsSelectable(true);
				((TextView) v).setMovementMethod(ArrowKeyMovementMethod.getInstance());
				return false;
			}
		});

		if (item_.getLinks() != null) {
			ViewGroup g = root.findViewById(R.id.links);
			for (Schedule.Link link : item_.getLinks()) {
				LinkButton btn = new LinkButton(ctx_, link);
				g.addView(btn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));
			}
			g.setVisibility(View.VISIBLE);
		}

		final ScrollView scr = root.findViewById(R.id.scrollDescription);
		scr.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
			@Override
			public void onScrollChanged() {
				Rect scrollBounds = new Rect();
				scr.getHitRect(scrollBounds);
				View subHeader = root.findViewById(R.id.subHeader);
				View header = root.findViewById(R.id.header);

				app_.setShadow(header, !subHeader.getLocalVisibleRect(scrollBounds));
				app_.setShadow(subHeader, subHeader.getLocalVisibleRect(scrollBounds));
			}
		});

		/* Bottom box used to be a bunch of things but now just the remind checkbox + delete icon. */
		LinearLayout bottomBox = root.findViewById(R.id.bottomBox);

		cb_ = new CheckBox(ctx_);
		cb_.setText(R.string.remind_me);
		cb_.setChecked(item_.getRemind());
		bottomBox.addView(cb_, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));

		cb_.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				((Giggity) app_).checkReminderPermissions(ctx, isChecked);
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

		ImageButton shareButton= new ShareButton(ctx_);
		bottomBox.addView(shareButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0));

		ImageButton delButton;
		if (!item_.isHidden()) {
			delButton = new HideButton(ctx_);
		} else {
			delButton = new UnhideButton(ctx_);
		}
		bottomBox.addView(delButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0));

		addView(root);
	}

	/* Used in tablet view at least to switch from split to fullscreen. */
	public void setTitleClick(OnClickListener title_click) {
		View v;
		getChildAt(0).findViewById(R.id.title).setOnClickListener(title_click);
		getChildAt(0).findViewById(R.id.subtitle).setOnClickListener(title_click);
	}

	public void saveScroll() {
		final ScrollView sv = root.findViewById(R.id.scrollDescription);
		final View inner = sv.getChildAt(0);
		if (sv.getScrollY() == 0 || inner.getHeight() <= sv.getHeight()) {
			return;
		}
		final double ratio = Math.max(0, Math.min(1, (double) sv.getScrollY() / (inner.getHeight() - sv.getHeight())));

		/* Grmbl. There's no great way to do this. I tried throwing this into an onLayout listener
		   but that's not the right time either. So just set a timer to the moment the animation
		   finishes (doing it earlier won't work).. */
		sv.postDelayed(new Runnable() {
			@Override
			public void run() {
				sv.scrollTo(0, (int) (ratio * (inner.getHeight() - sv.getHeight())));
			}
		}, 500);
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

	private class ShareButton extends ImageButton implements ImageButton.OnClickListener {
		public ShareButton(Context context) {
			super(context);
			setImageResource(android.R.drawable.ic_menu_share);
			app_.setPadding(this, 0, 0, 0, 0);
			setOnClickListener(this);
			setBackgroundResource(android.R.color.transparent);
		}

		@Override
		public void onClick(View v) {
			Intent t = new Intent(android.content.Intent.ACTION_SEND);
			t.setType("text/plain");
			t.putExtra(android.content.Intent.EXTRA_SUBJECT, item_.getTitle());
			java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx_);
			String time = android.text.format.DateUtils.formatDateRange(
				ctx_, item_.getStartTime().getTime(), item_.getEndTime().getTime(),
				DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);

			String text = item_.getSchedule().getTitle() + ": " + item_.getTitle() + "\n" +
			              item_.getLine().getTitle() + ", " + time + "\n";
			if (item_.getSpeakers() != null && item_.getSpeakers().size() > 0) {
				text += TextUtils.join(", ", item_.getSpeakers()) + "\n";
			}
			if (item_.getWebLink() != null) {
				text += item_.getWebLink() + "\n";
			}
			text += "\n" + item_.getDescriptionStripped();

			t.putExtra(android.content.Intent.EXTRA_TEXT, text);

			ctx_.startActivity(Intent.createChooser(t, "Share via"));


			/* Hrmm, I thought I saw formatted stuff getting exported once, guess not?
			t.setType("text/html");
			t.putExtra(android.content.Intent.EXTRA_TEXT,
				"<h2>" + escapeHtml(item_.getSchedule().getTitle() + ": " + escapeHtml(item_.getTitle())) + "</h2>" +
				"<p>" + escapeHtml(item_.getLine().getTitle()) + ", " + time + "</p>" +
				"<p>" + item_.getDescription() + "</p>");
			*/
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
			if (item_.getLanguage() != null && item_.getSchedule().getLanguages().size() > 1) {
				delWhat.add(String.format(ctx_.getResources().getString(R.string.hide_lang), item_.getLanguage()));
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(ctx_);
			builder.setTitle(title);
			builder.setItems(delWhat.toArray(new CharSequence[delWhat.size()]), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int what) {
					Schedule sched = item_.getSchedule();
					boolean showh = sched.getShowHidden();
					sched.setShowHidden(true);  // Needed for option 1 and 2 below to work.
					if (what == 0) {
						item_.setHidden(newValue);
					} else if (what == 1) {
						for (Schedule.Item other : item_.getLine().getItems())
							other.setHidden(newValue);
					} else if (what == 2 && item_.getTrack() != null) {
						for (Schedule.Item other : item_.getTrack().getItems())
							other.setHidden(newValue);
					} else {
						for (Schedule.Item other : sched.getByLanguage(item_.getLanguage())) {
							other.setHidden(newValue);
						}
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
