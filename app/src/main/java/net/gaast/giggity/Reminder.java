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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.threeten.bp.ZonedDateTime;

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

		Notification.Builder nb = new Notification.Builder(app)
				                          .setSmallIcon(R.drawable.ic_schedule_white_48dp)
				                          .setColor(app.getResources().getColor(R.color.primary))
				                          .setContentTitle(item.getTitle())
				                          .setWhen(item.getStartTime().getTime())
										  .setShowWhen(true)
				                          .setVisibility(Notification.VISIBILITY_PUBLIC)
				                          .setContentIntent(PendingIntent.getActivity(app, 0, evi, 0))
				                          .setAutoCancel(true)
				                          .setDefaults(Notification.DEFAULT_SOUND)
				                          .setVibrate(((item.getStartTime().getDate() & 1) == 0) ? giggitygoo : mario)
				                          .setSortKey(Long.toHexString((item.getStartTime().getTime() / 1000)))  // redundant with setWhen()?
				                          .setLights(app.getResources().getColor(R.color.primary), 500, 5000);

		String location = item.getLine().getLocation();
		if (location != null) {
			PendingIntent geoi = PendingIntent.getActivity(app, 0, new Intent(Intent.ACTION_VIEW, Uri.parse(location)), 0);
			nb.addAction(new Notification.Action(R.drawable.ic_place_black_24dp, item.getLine().getTitle(), geoi));
		} else {
			Notification.BigTextStyle extra = new Notification.BigTextStyle();
			extra.setSummaryText(item.getLine().getTitle());
			nb.setStyle(extra);
		}

		if (Build.VERSION.SDK_INT >= 26) {
			nb.setChannelId(Giggity.CHANNEL_ID);
		}


		return nb.build();
	}

	public void poke(Schedule.Item item) {
		if (item.getStartTimeZoned().isBefore(ZonedDateTime.now())) {
			return;
		}

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
		boolean enabled = pref.getBoolean("reminder_enabled", true);
		int period = Integer.parseInt(pref.getString("reminder_period", "5")) * 60000;
		long tm;

		// Use hashCode() (rewritten for better entropy) as id for notifications etc to keep things
		// nicely stateless and be able to cancel alarms and notifications later when necessary.
		// bit 0 == 0 for creation and 1 for deletion alarm (when event has passed)
		int id = item.hashCode() << 1;

		Intent intent = new Intent(NotificationPoster.ACTION);
		intent.putExtra("id", id);
		intent.putExtra("notification", buildNotification(item));
		PendingIntent ntfIntent = PendingIntent.getBroadcast(app, id, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		intent = new Intent(NotificationPoster.ACTION);
		intent.putExtra("id", id);
		PendingIntent endIntent = PendingIntent.getBroadcast(app, id | 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
		if (enabled && item.getRemind()) {
			am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tm = item.getStartTime().getTime() - period, ntfIntent);
			// debugging aid  am.setExact(AlarmManager.RTC_WAKEUP, tm = System.currentTimeMillis() + 5000, ntfIntent);
			am.set(AlarmManager.RTC, item.getEndTime().getTime(), endIntent);

			Log.d("reminder", "Alarm set for " + item.getTitle() + " in " +
					                  (tm - System.currentTimeMillis()) / 1000 + " seconds");
		} else {
			am.cancel(ntfIntent);
			am.cancel(endIntent);
		}
	}
}
