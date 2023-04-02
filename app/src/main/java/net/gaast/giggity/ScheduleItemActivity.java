package net.gaast.giggity;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.transition.Explode;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

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

		if (url.contains("#")) {
			String[] parts = url.split("#", 2);
			url = parts[0];
			id = parts[1];
		} else {
			setResult(RESULT_CANCELED, getIntent());
			finish();
			return;
		}

		Schedule sched;
		if (app_.hasSchedule(url)) {
			sched = app_.getCachedSchedule(url);
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

		pager_ = new EventDialogPager(this, item, others, getIntent().getStringExtra("search_query"));
		//pager_.getHeader().setTransitionName("title");
		setContentView(pager_);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.i("ScheduleItemActivity", "Configuration changed");
		pager_.saveScroll();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (grantResults.length != 1) {
			return;
		}
		if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(this, "Note that event reminders won't show up if notifications aren't granted.", Toast.LENGTH_LONG).show();
		}
		// Nothing to do if it was granted: Alarms are already set, and this permission is only needed once those go off.
	}
}
