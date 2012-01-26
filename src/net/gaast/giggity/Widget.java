package net.gaast.giggity;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

/* http://www.vogella.de/articles/AndroidWidgets/article.html helped me a lot here. */

public class Widget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		ComponentName thisWidget = new ComponentName(context, Widget.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
		for (int widgetId : allWidgetIds) {
			RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
			v.setTextViewText(R.id.title, "Boe");
			appWidgetManager.updateAppWidget(widgetId, v);
		}
	}
}
