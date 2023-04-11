package net.gaast.giggity;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

public class Reminder {
	Giggity app;

	/* Vibrator pattern */
	private long[] mario = { 0, 90, 60, 90, 60, 0, 150, 90, 60, 0, 150, 120, 30,
                                 90, 60, 0, 150, 150, 0, 0, 150, 0, 150, 0, 150, 1200 };
	private long[] giggitygoo = { 0, 100, 40, 60, 40, 60, 60, 100, 40, 60, 40, 60, 80, 1200};

	NotificationPoster poster = new NotificationPoster();

	public Reminder(Giggity app) {
		this.app = app;

		Log.d("reminder", "onCreate");
		IntentFilter filter = new IntentFilter(NotificationPoster.ACTION);
		app.registerReceiver(poster, filter);
	}

	public static class NotificationPoster extends BroadcastReceiver {
		public static final String ACTION = "net.gaast.giggity.ALARM";

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("reminder", "Who disturbs my slumber?");
			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

			Notification ntf = intent.getParcelableExtra("notification");
			int id = intent.getIntExtra("id", 0);
			if (ntf != null) {
				nm.notify(id, ntf);
			} else {
				nm.cancel(id);
			}
		}
	};

	private Notification buildNotification(Schedule.Item item) {
		Intent evi = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()), app,
				ScheduleViewActivity.class);
		ArrayList<String> others = new ArrayList<>();
		for (Schedule.Item it : app.getRemindItems()) {
			if (it.getSchedule() == item.getSchedule()) {
				others.add(it.getId());
			}
		}
		evi.putExtra("others", others.toArray(new String[others.size()]));

		Notification.Builder nb = new Notification.Builder(app)
				                          .setSmallIcon(R.drawable.ic_schedule_white_48dp)
				                          .setColor(app.getResources().getColor(R.color.primary))
				                          .setContentTitle(item.getTitle())
				                          .setWhen(item.getStartTime().getTime())
										  .setShowWhen(true)
				                          .setVisibility(Notification.VISIBILITY_PUBLIC)
				                          .setContentIntent(PendingIntent.getActivity(app, 0, evi, PendingIntent.FLAG_IMMUTABLE))
				                          .setAutoCancel(true)
				                          .setDefaults(Notification.DEFAULT_SOUND)
				                          .setVibrate(((item.getStartTime().getDate() & 1) == 0) ? giggitygoo : mario)
				                          .setSortKey(Long.toHexString((item.getStartTime().getTime() / 1000)))  // redundant with setWhen()?
				                          .setLights(app.getResources().getColor(R.color.primary), 500, 5000);

		String location = item.getLine().getLocation();
		if (location != null) {
			PendingIntent geoi = PendingIntent.getActivity(app, 0, new Intent(Intent.ACTION_VIEW, Uri.parse(location)), PendingIntent.FLAG_IMMUTABLE);
			nb.addAction(new Notification.Action(R.drawable.ic_place_black_24dp, item.getLine().getTitle(), geoi));
		} else {
			Notification.BigTextStyle extra = new Notification.BigTextStyle();
			extra.setSummaryText(item.getLine().getTitle());
			nb.setStyle(extra);
		}
		nb.setChannelId(Giggity.CHANNEL_ID);
		return nb.build();
	}

	public void poke(Schedule.Item item) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
		int period = Integer.parseInt(pref.getString("reminder_period", "5"));

		ZonedDateTime tm = item.getStartTimeZoned().minusMinutes(period);
		// debug: tm = ZonedDateTime.now().plusSeconds(5);
		if (tm.isBefore(ZonedDateTime.now()) || tm.isAfter(ZonedDateTime.now().plusDays(1))) {
			return;
		}

		// Use hashCode() (rewritten for better entropy) as id for notifications etc to keep things
		// nicely stateless and be able to cancel alarms and notifications later when necessary.
		// bit 0 == 0 for creation and 1 for deletion alarm (when event has passed)
		int id = item.hashCode() << 1;

		Intent intent = new Intent(NotificationPoster.ACTION);
		intent.putExtra("id", id);
		intent.putExtra("notification", buildNotification(item));
		PendingIntent ntfIntent = PendingIntent.getBroadcast(app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		intent = new Intent(NotificationPoster.ACTION);
		intent.putExtra("id", id);
		PendingIntent endIntent = PendingIntent.getBroadcast(app, id | 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
		boolean enabled = pref.getBoolean("reminder_enabled", true);
		am.cancel(ntfIntent);
		am.cancel(endIntent);
		if (enabled && item.getRemind()) {
			try {
				am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tm.toEpochSecond() * 1000, ntfIntent);
				am.set(AlarmManager.RTC, item.getEndTime().getTime(), endIntent);

				Log.d("reminder", "Alarm set for " + item.getTitle() + " in " +
						                  ChronoUnit.SECONDS.between(ZonedDateTime.now(), tm) + " seconds");
			} catch (SecurityException e) {
				// https://github.com/Wilm0r/giggity/issues/147
				// I don't really know what's going on there, nor all do I understand all the intricacies of this API.
				// But apparently Samsung don't either. :-P I hope I've worked around that now while keeping this functionality reliable..
				e.printStackTrace();
				if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
					Toast.makeText(app, "Warning: Caught SecurityException while setting reminder. Please report on #147 on github.", Toast.LENGTH_LONG);
				}
			}
		}
	}
}
