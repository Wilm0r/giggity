package net.gaast.giggity;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;

public class TrackList extends ListView implements ScheduleViewer {
	private Schedule sched;
	private Context ctx;
	
	private TrackAdapter tracks;

	public TrackList(Context context, Schedule sched_) {
		super(context);
		ctx = context;
		sched = sched_;
		
		if (sched.getTracks() == null)
			return;
		
		tracks = new TrackAdapter(sched);
		setAdapter(tracks);
		
		setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
				/* Using SearchActivity here may not be that obvious, but it does mostly do what 
				 * I need and gives good back button behaviour. */
				Intent i = new Intent(ctx, SearchActivity.class);
				i.setAction(Intent.ACTION_SEARCH);
				i.putExtra("track", (String) tracks.getItem(pos));
				ctx.startActivity(i);
			}
		});
	}

	@Override
	public void refreshContents() {
		/* No periodic refresh required. */
	}

	@Override
	public void refreshItems() {
		/* Not showing any actual schedule items so again nothing to do. */
	}

	private class TrackAdapter extends BaseAdapter {
		private final Schedule sched;
		private final ArrayList<String> keys;
		
		public TrackAdapter(Schedule sched) {
			this.sched = sched;
			keys = sched.getTracks();
			Collections.sort(keys);
		}
		
		@Override
		public int getCount() {
			return keys.size();
		}

		@Override
		public Object getItem(int position) {
			return keys.get(position);
		}

		@Override
		public long getItemId(int position) {
			return (long) position;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			int n = 0;
			RelativeLayout v = new RelativeLayout(ctx);
			TextView title, subtitle;
			RelativeLayout.LayoutParams p;
			
			title = new TextView(ctx);
			title.setText(keys.get(position));
			title.setTextSize(20);
			title.setTextColor(getResources().getColor(R.color.dark_text));
			title.setId(++n);
			p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			p.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			v.addView(title, p);

			/* If all talks are in the same room, include it in the subtitle. */
			String subtitle_txt = "";
			AbstractList<Schedule.Item> items = (AbstractList<Schedule.Item>) sched.getTrackItems(keys.get(position));
			if (items.size() > 0) {
				subtitle_txt = items.get(0).getLine().getTitle();
				for (Schedule.Item item : items) {
					if (!item.getLine().getTitle().equals(subtitle_txt)) {
						subtitle_txt = "";
						break;
					}
				}
				if (!subtitle_txt.equals("")) {
					subtitle_txt += ", ";
				}
			}
			subtitle_txt += items.size() + " items";
			
			subtitle = new TextView(ctx);
			subtitle.setText(subtitle_txt);
			subtitle.setTextSize(12);
			subtitle.setId(++n);
			p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			p.addRule(RelativeLayout.BELOW, title.getId());
			p.addRule(RelativeLayout.ALIGN_LEFT, title.getId());
			p.addRule(RelativeLayout.ALIGN_RIGHT, title.getId());
			v.addView(subtitle, p);

			return v;
		}
	}
	
	@Override
	public boolean multiDay() {
		return true;
	}

	@Override
	public boolean extendsActionBar() {
		return false;
	}
}
