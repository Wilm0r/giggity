package net.gaast.giggity;

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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

public class Reminder extends Service {
	public static final String ACTION = "net.gaast.giggity.ALARM";
	
	Giggity app;

	/* Vibrator pattern */
	private long[] mario = { 0, 90, 60, 90, 60, 0, 150, 90, 60, 0, 150, 120, 30,
                                 90, 60, 0, 150, 150, 0, 0, 150, 0, 150, 0, 150, 1200 };
	private long[] giggitygoo = { 0, 100, 40, 60, 40, 60, 60, 100, 40, 60, 40, 60, 80, 1200};

	// notification id -> cancellation time
	private HashMap<Integer, Long> cancelAt;

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
				sched = app.getSchedule(url[0], Fetcher.Source.CACHE_ONLINE);
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

			cleanup();
		}
	};

	// Clean up notifications for passed events. Only run if we're sending another notification in
	// the meantime. Which IMHO is the only time when cluttering is annoying.
	private void cleanup() {
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		for (Iterator<Integer> it = cancelAt.keySet().iterator(); it.hasNext();) {
			Integer id = it.next();
			if (System.currentTimeMillis() >= cancelAt.get(id)) {
				nm.cancel(id);
				it.remove();
			}
		}
	}

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

		cancelAt = new HashMap<>();
		
		/*
		registerReceiver(alarmReceiver, new IntentFilter(Intent.ACTION_TIME_CHANGED));
		registerReceiver(alarmReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
		*/
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		/* We're just there to keep the schedule in memory if there are reminders set, and receive
		   alarms when notifications should be shown. */
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(alarmReceiver);
		Log.d("reminder", "onDestroy");
	}
	
	private void sendReminder(Schedule.Item item) {
		/* Prepare the intent that will show the EventDialog. */
		Intent evi = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()),
				app, ScheduleViewActivity.class);
		evi.putExtra("PREFER_CACHED", true);

		/* Generate a notification. */
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder nb = new Notification.Builder(app)
				.setContentTitle(item.getTitle())
				.setContentText(getResources().getString(R.string.soon_in) + " " + item.getLine().getTitle())
				.setWhen(item.getStartTime().getTime())
				.setContentIntent(PendingIntent.getActivity(app, 0, evi, 0))
				.setSmallIcon(R.drawable.ic_schedule_white_48dp)
				.setAutoCancel(true)
				.setDefaults(Notification.DEFAULT_SOUND)
				.setVibrate(((item.getStartTime().getDate() & 1) == 0) ? giggitygoo : mario)
				.setLights(getResources().getColor(R.color.primary), 500, 5000);

		if (Build.VERSION.SDK_INT >= 21) {
			nb.setVisibility(Notification.VISIBILITY_PUBLIC)
			  .setColor(getResources().getColor(R.color.primary));
		}

		Bitmap icon = item.getSchedule().getIconBitmap();
		if (icon != null) {
			nb.setLargeIcon(icon);
		}

		Notification.BigTextStyle extra = new Notification.BigTextStyle();
		extra.setSummaryText(getResources().getString(R.string.soon_in) + " " + item.getLine().getTitle());
		extra.bigText(item.getDescriptionStripped());
		nb.setStyle(extra);

		// necessary? (find builder equiv)  not.defaults |= Notification.DEFAULT_SOUND;

		int id = item.hashCode() | (int) (item.getStartTime().getTime() / 1000);
		nm.notify(id, nb.build());

		// Allow cancellation a little before the actual end (off-by-one)
		cancelAt.put(id, item.getEndTime().getTime() - 60000);
		
		Log.d("reminder", "Generated notification for " + item.getTitle());
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
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
		if (android.os.Build.VERSION.SDK_INT >= 19) {
			// .set is inexact (though not clear how much) from API 19 for powersaving reasons.
			// Giggity uses timers sporadically enough that I think .setExact() is justified.
			am.setExact(AlarmManager.RTC_WAKEUP, tm, PendingIntent.getBroadcast(app, 0, i, 0));
		} else {
			am.set(AlarmManager.RTC_WAKEUP, tm, PendingIntent.getBroadcast(app, 0, i, 0));
		}
		Log.d("reminder", "Alarm set for " + item.getTitle() + " in " +
				(tm - System.currentTimeMillis()) / 1000 + " seconds");
	}
}
