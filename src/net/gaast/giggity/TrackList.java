package net.gaast.giggity;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class TrackList extends ListView implements ScheduleViewer {
	private Schedule sched;
	private Context ctx;
	
	private TrackAdapter tracks;

	public TrackList(Context context, Schedule sched_) {
		super(context);
		ctx = context;
		sched = sched_;
		
		tracks = new TrackAdapter(sched.getTracks());
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
    
    private class TrackAdapter extends BaseAdapter {
    	private final AbstractMap<String,TreeSet<Schedule.Item>> tracks;
    	private final ArrayList<String> keys;
    	
    	public TrackAdapter(AbstractMap<String,TreeSet<Schedule.Item>> tracks_) {
    		tracks = tracks_;
    		keys = new ArrayList<String>(tracks.keySet());
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
			title.setId(++n);
			p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			p.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			v.addView(title, p);

			/* If all talks are in the same room, include it in the subtitle. */
			String subtitle_txt = "";
			TreeSet<Schedule.Item> items = tracks.get(keys.get(position));
			if (items.size() > 0) {
				subtitle_txt = items.first().getLine().getTitle();
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
			subtitle_txt += tracks.get(keys.get(position)).size() + " items";
			
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
}
