package net.gaast.deoxide;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ChooserActivity extends ListActivity {
	private ArrayList<DeoxideDb.DbSchedule> scheds;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	Deoxide app = (Deoxide) getApplication();
    	scheds = app.getDb().getScheduleList();
    	String[] listc = new String[scheds.size()];
    	int i;
    	
    	for (i = 0; i < scheds.size(); i ++) {
    		listc[i] = scheds.get(i).getTitle();
    	}
    	setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listc));
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(scheds.get((int)id).getUrl()),
    			                   this, ScheduleViewActivity.class);
    	startActivity(intent);
    }
}
/*
//sched.loadSchedule("http://wilmer.gaast.net/deoxide/test.xml");
//sched.loadSchedule("http://fosdem.org/2009/schedule/xcal");
*/