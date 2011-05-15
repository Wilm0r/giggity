package net.gaast.giggity;

import java.util.LinkedList;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class TrackList extends ListView implements ScheduleViewer {
	private Schedule sched;
	private Context ctx;
	
	private LinkedList<String> tracks;

	public TrackList(Context context, Schedule sched_) {
		super(context);
		ctx = context;
		sched = sched_;
		
		tracks = new LinkedList<String>(sched.getTracks().keySet());
		setAdapter(new ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, tracks));
		setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
				/* Using SearchActivity here may not be that obvious, but it does mostly do what 
				 * I need and gives good back button behaviour. */
				Intent i = new Intent(ctx, SearchActivity.class);
				i.setAction(Intent.ACTION_SEARCH);
				i.putExtra("track", tracks.get(pos));
				ctx.startActivity(i);
			}
		});
	}

	@Override
	public void refreshContents() {
		/* No periodic refresh required. */
	}
}
