package net.gaast.deoxide;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout.LayoutParams;

public class ChooserActivity extends Activity {
	private ArrayList<DeoxideDb.DbSchedule> scheds;
	ListView list;
	EditText urlBox;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	list = new ListView(this);
    	
    	list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
    	        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(scheds.get((int)id).getUrl()),
		                   view.getContext(), ScheduleViewActivity.class);
    	        startActivity(intent);
			}
    	});
    	
    	LinearLayout cont = new LinearLayout(this);
    	cont.setOrientation(LinearLayout.VERTICAL);
    	cont.addView(list, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1));

    	LinearLayout bottom = new LinearLayout(this);

    	urlBox = new EditText(this);
    	urlBox.setText("http://");
    	urlBox.setSingleLine();
    	urlBox.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN &&
				    keyCode == KeyEvent.KEYCODE_ENTER) {
					openNewUrl();
					return true;
				} else {
					return false;
				}
			}
    	});
    	bottom.addView(urlBox, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
    	
    	ImageButton addButton = new ImageButton(this);
    	addButton.setImageResource(android.R.drawable.ic_input_add);
    	addButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openNewUrl();
			}
    	});
    	bottom.addView(addButton);
    	
    	cont.addView(bottom);
    	setContentView(cont);
    }
    
    @Override
    public void onResume() {
    	/* Do this part in onResume so we automatically re-sort the list (and
    	 * pick up new items) when returning to the chooser. */
    	super.onResume();
    	
    	Deoxide app = (Deoxide) getApplication();
    	scheds = app.getDb().getScheduleList();
    	String[] listc = new String[scheds.size()];
    	
    	int i;
    	for (i = 0; i < scheds.size(); i ++) {
    		listc[i] = scheds.get(i).getTitle();
    	}
    	
    	list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listc));
    }
    
    private void openNewUrl() {
    	Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlBox.getText().toString()),
                                   this, ScheduleViewActivity.class);
    	startActivity(intent);
    }
}