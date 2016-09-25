package net.gaast.giggity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;

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
		String title = "", time = "", room = "", url = "";
		Schedule.Item show_item = null;
		ArrayList<String> others = new ArrayList<>();
		
		Log.d("WIDGET", "onUpdate " + app.getRemindItems().size() + " items");
		
		for (Schedule.Item item : app.getRemindItems()) {
			if (show_item != null) {
				if (item.getSchedule() == show_item.getSchedule()) {
					others.add(item.getId());
				}
			} else if (item.getStartTime().after(new Date())) {
				Format df;
				
				if ((item.getSchedule().getLastTime().getTime() -
				     item.getSchedule().getFirstTime().getTime()) > (6 * 86400000))
					df = new SimpleDateFormat(ctx.getResources().getString(R.string.widg_longdate));
				else
					/* If the event takes <6*24h, show just a weekday, no full date. */
					df = new SimpleDateFormat(ctx.getResources().getString(R.string.widg_shortdate));

				show_item = item;
				others.add(item.getId());
				time = df.format(item.getStartTime());
				title = item.getTitle();
				room = item.getLine().getTitle();
				url = item.getUrl();
			}
		}
		
		if (title.equals("")) {
			Db.Connection db = app.getDb();
			AbstractList<Db.DbSchedule> scheds = db.getScheduleList();
			
			for (Db.DbSchedule sched : scheds) {
				if (sched.getStart().after(new Date())) {
					time = ctx.getResources().getString(R.string.widg_soon);
					title = sched.getTitle();
					room = Giggity.dateRange(sched.getStart(), sched.getEnd());
					url = sched.getUrl();
					break;
				}
			}
		}
		
		if (title.equals("")) {
			title = ctx.getString(R.string.widg_no_upcoming);
		}
		
		ComponentName thisWidget = new ComponentName(ctx, Widget.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
		for (int widgetId : allWidgetIds) {
			RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);

			/* Note that we don't call Schedule*Item*Activity directly here because we may need to
			   re-load the schedule if we got killed in the meantime. SIA can't do that. */
			Intent evi = new Intent(Intent.ACTION_VIEW, Uri.parse(url), app, ScheduleViewActivity.class);
			evi.putExtra("PREFER_CACHED", true);
			if (!others.isEmpty()) {
				evi.putExtra("others", others.toArray(new String[others.size()]));
			}
			PendingIntent pi = PendingIntent.getActivity(app, 0, evi, PendingIntent.FLAG_UPDATE_CURRENT);
			v.setOnClickPendingIntent(R.id.title, pi);

			v.setTextViewText(R.id.title, title);
			v.setTextViewText(R.id.bottom, time + " · " + room);
			appWidgetManager.updateAppWidget(widgetId, v);
		}
	}
}
