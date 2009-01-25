package net.gaast.deoxide;

import java.util.ArrayList;

import android.app.ListActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ChooserActivity extends ListActivity {
	private ArrayList<DeoxideDb.DbSchedule> scheds;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	Deoxide app = (Deoxide) getApplication();
    	DeoxideDb.Connection db = app.getDb();
    	scheds = db.getScheduleList();
    	String[] listc = new String[scheds.size()];
    	int i;
    	
    	for (i = 0; i < scheds.size(); i ++) {
    		listc[i] = scheds.get(i).getId();
    	}
    	setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listc));
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	Log.d("olic", "" + position + " " + id);
    }
}
