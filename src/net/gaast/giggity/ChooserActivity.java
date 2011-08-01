/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import java.util.ArrayList;
import java.util.LinkedList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;

public class ChooserActivity extends Activity {
	private ArrayList<Db.DbSchedule> scheds;
	ListView list;
	EditText urlBox;
	Db.Connection db;
	
	final String BARCODE_SCANNER = "com.google.zxing.client.android.SCAN";
	final String BARCODE_ENCODE = "com.google.zxing.client.android.ENCODE";
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	/*//test stuff
    	Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    	long[] pattern = {  };
    	v.vibrate(pattern, -1);
    	*/
    	
    	Giggity app = (Giggity) getApplication();
    	db = app.getDb();
    	
    	list = new ListView(this);
    	list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
				if (id == scheds.size()) {
					/* "Scan QR code..." */
					try {
						Intent intent = new Intent(BARCODE_SCANNER);
				        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
				        startActivityForResult(intent, 0);
					} catch (android.content.ActivityNotFoundException e) {
					    new AlertDialog.Builder(ChooserActivity.this)
					      .setMessage("Please install the Barcode Scanner app")
					      .setTitle("Error")
					      .show();
					}
			        return;
				}
    	        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(scheds.get((int)id).getUrl()),
		                   view.getContext(), ScheduleViewActivity.class);
    	        startActivity(intent);
			}
    	});
    	list.setLongClickable(true);
    	list.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
				if (mi.position == scheds.size())
					return; /* "Scan QR code..." */
				menu.setHeaderTitle(scheds.get((int)mi.position).getTitle());
				menu.add("Refresh");
				menu.add("Remove");
				menu.add("Show URL...");
			}
    	});
    	
    	/* Filling in the list in onResume(). */
    	
    	LinearLayout cont = new LinearLayout(this);
    	cont.setOrientation(LinearLayout.VERTICAL);
    	cont.addView(list, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1));

    	LinearLayout bottom = new LinearLayout(this);

    	urlBox = new EditText(this);
    	urlBox.setText("http://");
    	urlBox.setSingleLine();
    	urlBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
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
    public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) item.getMenuInfo();
        Giggity app = (Giggity) getApplication();
        if (mi.id == scheds.size()) {
        	/* QR code item */
        } else if (item.getTitle().equals("Refresh")) {
			app.flushSchedule(scheds.get((int)mi.id).getUrl());
			/* Is this a hack? */
			list.getOnItemClickListener().onItemClick(null, list, mi.position, mi.id);
		} else if (item.getTitle().equals("Remove")) {
			db.removeSchedule(scheds.get((int)mi.id).getUrl());
			onResume();
		} else {
			try {
			    Intent intent = new Intent(BARCODE_ENCODE);
			    intent.putExtra("ENCODE_TYPE", "TEXT_TYPE");
			    intent.putExtra("ENCODE_DATA", scheds.get((int)mi.id).getUrl());
			    startActivity(intent);
			} catch (android.content.ActivityNotFoundException e) {
			    new AlertDialog.Builder(ChooserActivity.this)
			      .setTitle(scheds.get((int)mi.id).getTitle())
			      .setMessage(scheds.get((int)mi.id).getUrl())
			      .show();
			}
		}
		return false;
    }
    
    @Override
    public void onResume() {
    	/* Do this part in onResume so we automatically re-sort the list (and
    	 * pick up new items) when returning to the chooser. */
    	super.onResume();
    	
    	db.resume();
    	scheds = db.getScheduleList();
    	LinkedList<String> listc = new LinkedList<String>();
    	
    	int i;
    	for (i = 0; i < scheds.size(); i ++) {
    		listc.add(scheds.get(i).getTitle());
    	}
    	/* TODO: Figure out how to detect if Barcode Scanner is installed here. */
    	listc.add(getResources().getString(R.string.scan_qr));
    	
    	list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listc));
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	db.sleep();
    }
    
    private void openNewUrl() {
    	Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlBox.getText().toString()),
                                   this, ScheduleViewActivity.class);
    	startActivity(intent);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                urlBox.setText(intent.getStringExtra("SCAN_RESULT"));
                urlBox.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            }
        }
    }
}