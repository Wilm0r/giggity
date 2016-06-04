package net.gaast.giggity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class ScheduleItemActivity extends Activity {
	private Giggity app_;
	private Schedule sched_;
	private Schedule.Item item_;
	private AbstractList<Schedule.Item> others_;

	private EventDialogPager pager_;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		String id, url = getIntent().getDataString();
		app_ = (Giggity) getApplication();
		
		if (url.contains("#")) {
			String parts[] = url.split("#", 2);
			url = parts[0];
			id = parts[1];
		} else {
			setResult(RESULT_CANCELED, getIntent());
			finish();
			return;
		}
		
		if (app_.hasSchedule(url)) {
			try {
				sched_ = app_.getSchedule(url, Fetcher.Source.CACHE);
			} catch (Exception e) {
				e.printStackTrace();
				setResult(RESULT_CANCELED, getIntent());
				finish();
				return;
			}
		} else {
			finish();
			return;
		}

		item_ = sched_.getItem(id);

		if (getIntent().hasExtra("others")) {
			others_ = new ArrayList<Schedule.Item>();
			String[] others = getIntent().getStringArrayExtra("others");
			for (String oid : others) {
				others_.add(sched_.getItem(oid));
			}
		}

		pager_ = new EventDialogPager(this, item_, others_);
		setContentView(pager_);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
}
