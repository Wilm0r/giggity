package net.gaast.giggity;

import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class Reminder extends Service {
	Giggity app;
	Handler timer = new Handler();
	Runnable sendReminder = new Runnable() {
		@Override
		public void run() {
	    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
	    	int period = Integer.parseInt(pref.getString("reminder_period", "5")) * 60000;
	    	
	    	/* Keep it dumb and simple: Go through the list of marked events. If it's
	    	 * long ago, drop it. If its reminder time is 30s away from now (either 
	    	 * direction), remind the user now. If it's further away, set a timer and
	    	 * stop looking for now. */
			timer.removeCallbacks(this);
			for (Schedule.Item item : new ArrayList<Schedule.Item>(app.getRemindItems())) {
				long when = item.getStartTime().getTime() - period -
				            System.currentTimeMillis();
				if (when < -30000) {
					/* Hmm, this one's in the past, so too late to remind. */
					Log.d("reminder", "Dropping reminder for " + item.getTitle());
					app.getRemindItems().remove(item);
				} else if (when > -30000 && when < 30000) {
					Log.d("reminder", "Generating reminder for " + item.getTitle());
					sendReminder(item);
					app.getRemindItems().remove(item);
				} else {
					Log.d("reminder", "Next alarm coming up in " + when / 1000 + " seconds");
					timer.postDelayed(this, when);
					break;
					/* List is sorted by time so we're done for this iteration. */
				}
			}
			
			/* Stop the service if there's nothing else left. */
			if (app.getRemindItems().size() == 0) {
				Log.d("reminder", "No reminders left, let's stop the nagging");
				stopSelf();
			}
		}
	};

	/* Vibrator pattern */
	private long[] coolsong = { 0, 90, 60, 90, 60, 0, 150, 90, 60, 0, 150, 90, 60,
                                90, 60, 0, 150, 150, 0, 0, 150, 0, 150, 0, 150, 1000 };

	@Override
	public void onCreate() {
		super.onCreate();
		app = (Giggity) getApplication();
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		Log.d("reminder", "Who disturbs my slumber?");
		sendReminder.run();
	}
	
	private void sendReminder(Schedule.Item item) {
		
    	NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    	Notification not;
    	not = new Notification(R.drawable.deoxide_icon_48x48, item.getTitle(), item.getStartTime().getTime());
    	not.setLatestEventInfo(app, item.getTitle(), "Soon in " + item.getLine().getTitle(),
    			               PendingIntent.getActivity(app, 0, new Intent(app, ChooserActivity.class), 0));
    	not.flags |= Notification.FLAG_AUTO_CANCEL;
    	not.defaults |= Notification.DEFAULT_ALL;
    	not.vibrate = coolsong; 

    	nm.notify(1, not);
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}
