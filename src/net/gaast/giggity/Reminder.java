package net.gaast.giggity;

import java.util.ArrayList;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class Reminder extends Service {
	public static final String ACTION = "net.gaast.giggity.ALARM";
	
	Giggity app;
	
	int notid;

	/* Vibrator pattern */
	private long[] mario = { 0, 90, 60, 90, 60, 0, 150, 90, 60, 0, 150, 120, 30,
                             90, 60, 0, 150, 150, 0, 0, 150, 0, 150, 0, 150, 1200 };
	private long[] giggitygoo = { 0, 100, 40, 60, 40, 60, 60, 100, 40, 60, 40, 60, 80, 1200};

	private BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("reminder", "Who disturbs my slumber?");
			if (intent.getDataString() == null) {
				Log.e("reminder", "Empty intent. Huh?");
				return;
			}
			String url[] = intent.getDataString().split("#", 2);
			Schedule sched;
			try {
				sched = app.getSchedule(url[0]);
			} catch (Exception e) {
				Log.e("reminder", "Urgh, caught exception while reloading schedule (the OS killed us)");
				e.printStackTrace();
				return;
				/* Hope this won't happen too often.. */
			}
			Schedule.Item item = sched.getItem(url[1]);
			if (item != null && item.getRemind())
				sendReminder(item);
			else
				Log.e("reminder", "Item that I was supposed to notify about disappeared");
			app.getRemindItems().remove(item);
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		app = (Giggity) getApplication();
		
		/* Run our alarm loop if we receive an alarm, or if the current
		 * time(zone) changed. In that case we may have to reschedule alarms. */
		Log.d("reminder", "onCreate");
		IntentFilter filter = new IntentFilter(ACTION);
		try {
			filter.addDataType("text/x-giggity");
			filter.addDataScheme("http");
			filter.addDataScheme("https");
		} catch (MalformedMimeTypeException e) {
			e.printStackTrace();
		}
		registerReceiver(alarmReceiver, filter);
		
		/*
		registerReceiver(alarmReceiver, new IntentFilter(Intent.ACTION_TIME_CHANGED));
		registerReceiver(alarmReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
		*/
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(alarmReceiver);
		Log.d("reminder", "onDestroy");
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		/* Don't do anything, just wait for signals. */
	}
	
	private void sendReminder(Schedule.Item item) {
		/* Generate a notification. */
    	NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    	Notification not;
    	not = new Notification(R.drawable.deoxide_icon_48x48, item.getTitle(), item.getStartTime().getTime());
    	Intent evi = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()),
    			                app, ScheduleViewActivity.class);
    	not.setLatestEventInfo(app, item.getTitle(), "Soon in " + item.getLine().getTitle(),
    			               PendingIntent.getActivity(app, 0, evi, 0));
    	not.flags |= Notification.FLAG_AUTO_CANCEL;
    	not.defaults |= Notification.DEFAULT_SOUND;
    	if ((item.getStartTime().getDate() & 1) == 0)
    		not.vibrate = giggitygoo;
    	else
    		not.vibrate = mario;

    	nm.notify(item.hashCode() | (int) (item.getStartTime().getTime() / 1000), not);
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	static public void poke(Giggity app, Schedule.Item item) {
    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
    	if (pref.getBoolean("reminder_enabled", true)) {
    		/* Doesn't matter if it's already running BTW. */
    		app.startService(new Intent(app, Reminder.class));
    	} else {
    		app.stopService(new Intent(app, Reminder.class));
    		return;
    	}
    	
    	if (item != null)
    		setAlarm(app, item);
    	else {
    		for (Schedule.Item i : app.getRemindItems())
    			setAlarm(app, i);
    	}
	}
	
	static private void setAlarm(Context app, Schedule.Item item) {
    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
    	int period = Integer.parseInt(pref.getString("reminder_period", "5")) * 60000;
    	long tm = item.getStartTime().getTime() - period;
    	
    	if (tm < System.currentTimeMillis()) 
    		return;

    	AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(Reminder.ACTION);
		i.setDataAndType(Uri.parse(item.getUrl()), "text/x-giggity");
		am.set(AlarmManager.RTC_WAKEUP, tm,
			       PendingIntent.getBroadcast(app, 0, i, 0));
		Log.d("reminder", "Alarm set for " + item.getTitle() + " in " +
		      (tm - System.currentTimeMillis()) / 1000 + " seconds");
	}
}
