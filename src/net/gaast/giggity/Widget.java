package net.gaast.giggity;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.Date;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

/* http://www.vogella.de/articles/AndroidWidgets/article.html helped me a lot here. */

public class Widget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		updateWidget(context, appWidgetManager);
	}
	
	public static void updateWidget(Context context) {
		AppWidgetManager awm = AppWidgetManager.getInstance(context);
		updateWidget(context, awm);
	}
	
	public static void updateWidget(Context context, AppWidgetManager appWidgetManager) {
		Giggity app = (Giggity) context.getApplicationContext();
		String title = "", time = "", url = "";
		
		Log.d("WIDGET", "onUpdate");
		
		for (Schedule.Item item : app.getRemindItems()) {
			if (item.getStartTime().after(new Date())) {
				Format df = new SimpleDateFormat("EEEE d MMMM HH:mm");
				
				title = item.getTitle();
				time = df.format(item.getStartTime());
				url = item.getUrl();
				break;
			}
		}
		
		if (title == "") {
			Db.Connection db = app.getDb();
			AbstractList<Db.DbSchedule> scheds = db.getScheduleList();
			
			for (Db.DbSchedule sched : scheds) {
				if (sched.getStart().after(new Date())) {
					title = sched.getTitle();
					time = Giggity.dateRange(sched.getStart(), sched.getEnd());
					url = sched.getUrl();
					break;
				}
			}
			db.sleep();
		}
		
		ComponentName thisWidget = new ComponentName(context, Widget.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
		for (int widgetId : allWidgetIds) {
			RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
			
			Intent evi = new Intent(Intent.ACTION_VIEW, Uri.parse(url), app, ScheduleViewActivity.class);
			evi.putExtra("PREFER_CACHED", true);
			PendingIntent pi = PendingIntent.getBroadcast(app, 0, evi, 0);
			v.setOnClickPendingIntent(R.id.title, pi);

			v.setTextViewText(R.id.title, title);
			v.setTextViewText(R.id.time, time);
			appWidgetManager.updateAppWidget(widgetId, v);
		}
	}
}
