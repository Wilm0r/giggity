package net.gaast.giggity;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.transition.Explode;
import android.util.Log;
import android.view.Window;

import java.util.AbstractList;
import java.util.ArrayList;

public class ScheduleItemActivity extends Activity {
	private Giggity app_;

	private EventDialogPager pager_;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Fancy shared-element animations when opening event dialogs.
		getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
		//getWindow().setEnterTransition(new ChangeImageTransform());
		getWindow().setEnterTransition(new Explode());
		//getWindow().setAllowEnterTransitionOverlap(false);

		String id, url = getIntent().getDataString();
		app_ = (Giggity) getApplication();

		app_.setNavBar(getWindow(), R.color.background_material_light);

		if (url.contains("#")) {
			String parts[] = url.split("#", 2);
			url = parts[0];
			id = parts[1];
		} else {
			setResult(RESULT_CANCELED, getIntent());
			finish();
			return;
		}

		Schedule sched;
		if (app_.hasSchedule(url)) {
			try {
				sched = app_.getSchedule(url, Fetcher.Source.CACHE);
			} catch (Exception e) {
				e.printStackTrace();
				setResult(RESULT_CANCELED, getIntent());
				finish();
				return;
			}
		} else {
			setResult(RESULT_CANCELED, getIntent());
			finish();
			return;
		}

		Schedule.Item item = sched.getItem(id);

		AbstractList<Schedule.Item> others = null;
		if (getIntent().hasExtra("others")) {
			others = new ArrayList<Schedule.Item>();
			String[] ids = getIntent().getStringArrayExtra("others");
			for (String oid : ids) {
				Schedule.Item other_item = sched.getItem(oid);
				if (other_item != null) {
					others.add(other_item);
				}
			}
		}

		pager_ = new EventDialogPager(this, item, others);
		//pager_.getHeader().setTransitionName("title");
		setContentView(pager_);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.i("ScheduleItemActivity", "Configuration changed");
		pager_.saveScroll();
	}
}
