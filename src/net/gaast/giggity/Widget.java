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
	
	public static void updateWidget(Context ctx, AppWidgetManager appWidgetManager) {
		Giggity app = (Giggity) ctx.getApplicationContext();
		String top = "", title = "", bottom = "", url = "";
		
		Log.d("WIDGET", "onUpdate");
		
		for (Schedule.Item item : app.getRemindItems()) {
			if (item.getStartTime().after(new Date())) {
				Format df;
				
				if ((item.getSchedule().getLastTime().getTime() -
				     item.getSchedule().getFirstTime().getTime()) > (6 * 86400000))
					df = new SimpleDateFormat(ctx.getResources().getString(R.string.widg_longdate));
				else
					/* If the event takes <6*24h, show just a weekday, no full date. */
					df = new SimpleDateFormat(ctx.getResources().getString(R.string.widg_shortdate));
				
				top = df.format(item.getStartTime());
				title = item.getTitle();
				if (item.getSpeakers().size() > 0)
					bottom = item.getSpeakers().get(0) + " " + ctx.getResources().getString(R.string.widg_in_room) + " ";
				bottom += item.getLine().getTitle();
				url = item.getUrl();
				break;
			}
		}
		
		if (title.equals("")) {
			Db.Connection db = app.getDb();
			AbstractList<Db.DbSchedule> scheds = db.getScheduleList();
			
			for (Db.DbSchedule sched : scheds) {
				if (sched.getStart().after(new Date())) {
					top = ctx.getResources().getString(R.string.widg_soon);
					title = sched.getTitle();
					bottom = Giggity.dateRange(sched.getStart(), sched.getEnd());
					url = sched.getUrl();
					break;
				}
			}
			db.sleep();
		}
		
		if (title.equals("")) {
			title = "No upcoming events :-(";
		}
		
		ComponentName thisWidget = new ComponentName(ctx, Widget.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
		for (int widgetId : allWidgetIds) {
			RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);
			
			Intent evi = new Intent(Intent.ACTION_VIEW, Uri.parse(url), app, ScheduleViewActivity.class);
			evi.putExtra("PREFER_CACHED", true);
			PendingIntent pi = PendingIntent.getActivity(app, 0, evi, 0);
			v.setOnClickPendingIntent(R.id.title, pi);

			v.setTextViewText(R.id.top, top);
			v.setTextViewText(R.id.title, title);
			v.setTextViewText(R.id.bottom, bottom);
			appWidgetManager.updateAppWidget(widgetId, v);
		}
	}
}
