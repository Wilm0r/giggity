package net.gaast.giggity;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

public class ScheduleItemActivity extends Activity {
	private Giggity app;
	private Schedule sched;
	private Schedule.Item item;
	private EventDialog dialog;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//getActionBar().hide();
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		String id, url = getIntent().getDataString();
		app = (Giggity) getApplication();
		
		if (url.contains("#")) {
			String parts[] = url.split("#", 2);
			url = parts[0];
			id = parts[1];
		} else {
			setResult(RESULT_CANCELED, getIntent());
			finish();
			return;
		}
		
		if (app.hasSchedule(url)) {
			try {
				sched = app.getSchedule(url, Fetcher.Source.CACHE);
			} catch (Exception e) {
				e.printStackTrace();
				setResult(RESULT_CANCELED, getIntent());
				finish();
				return;
			}
		}
		
		item = sched.getItem(id);
		
		dialog = new EventDialog(this, item);
		setContentView(dialog.genDialog(true));
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		dialog.onDismiss(null);
	}
}
