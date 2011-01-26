package net.gaast.giggity;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class SearchActivity extends ScheduleListActivity {
	Schedule sched;
	Giggity app;

	@Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
        if (!getIntent().getAction().equals(Intent.ACTION_SEARCH)) {
        	finish();
        	return;
        }
		
		/* Doesn't seem to work..
        TextView tv = new TextView(this);
		tv.setText("No results.");
		this.getListView().setEmptyView(tv);
		*/
        
    	app = (Giggity) getApplication();
        sched = app.getLastSchedule();
    	if (sched == null) {
    		finish(); /* WTF */
    		return;
    	}
    	
    	sched.setDay(-1);
    	String query = getIntent().getStringExtra(SearchManager.QUERY);
		setList(sched.searchItems(query));
		setTitle("Results for \"" + query + "\" in " + sched.getTitle());
	}
}
