package net.gaast.giggity;

import java.util.Iterator;
import java.util.LinkedList;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class SearchActivity extends ListActivity {
	Schedule sched;
	Giggity app;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
        if (!getIntent().getAction().equals(Intent.ACTION_SEARCH)) {
        	finish();
        	return;
        }

    	app = (Giggity) getApplication();
        sched = app.getLastSchedule();
    	if (sched == null) {
    		finish(); /* WTF */
    		return;
    	}
    	sched.setDay(-1);
    	String query = getIntent().getStringExtra(SearchManager.QUERY);

		Iterator<Schedule.Item> res = sched.searchItems(query).iterator();
		LinkedList<String> listc = new LinkedList<String>();
		while (res.hasNext()) {
			Schedule.Item item = res.next();
			listc.add(item.getTitle());
		}
		setListAdapter(new ArrayAdapter<String>(app, android.R.layout.simple_list_item_1, listc));
		setTitle("Results for \"" + query + "\" in " + sched.getTitle());
	}
}
