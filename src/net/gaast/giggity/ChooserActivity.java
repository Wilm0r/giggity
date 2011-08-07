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

import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;

import net.gaast.giggity.Db.DbSchedule;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;

public class ChooserActivity extends Activity {
	private ArrayList<Db.DbSchedule> scheds;
	EditText urlBox;
	Db.Connection db;

	ListView list;
	ScheduleAdapter lista;

	final String BARCODE_SCANNER = "com.google.zxing.client.android.SCAN";
	final String BARCODE_ENCODE = "com.google.zxing.client.android.ENCODE";
	
	private ImageButton addButton, qrButton;
	
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
				DbSchedule item = (DbSchedule) lista.getItem(position);
				if (item != null) {
	    	        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()),
			                   view.getContext(), ScheduleViewActivity.class);
	    	        startActivity(intent);
				}
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
				menu.add(ContextMenu.NONE, 0, 0, R.string.refresh);
				menu.add(ContextMenu.NONE, 1, 0, R.string.remove);
				menu.add(ContextMenu.NONE, 2, 0, R.string.show_url);
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
    	urlBox.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				setButtons();
			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
			}

			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
			}
    		
    	});
    	bottom.addView(urlBox, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));
    	
    	addButton = new ImageButton(this);
    	addButton.setImageResource(R.drawable.ic_input_add);
    	addButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openNewUrl();
			}
    	});
    	bottom.addView(addButton);
    	
    	qrButton = new ImageButton(this);
    	qrButton.setImageResource(R.drawable.qr_scan);
    	qrButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
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
			}
    	});
    	bottom.addView(qrButton);
    	
    	setButtons();
    	cont.addView(bottom);
    	setContentView(cont);
    }
    
    private void setButtons() {
		String url = urlBox.getText().toString();
		boolean gotUrl = false;
		if (url.length() > 7)
			gotUrl = true;
		else if (!"http://".startsWith(url))
			gotUrl = true;
		addButton.setVisibility(gotUrl ? View.VISIBLE : View.GONE);
		qrButton.setVisibility(gotUrl ? View.GONE : View.VISIBLE);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) item.getMenuInfo();
        Giggity app = (Giggity) getApplication();
        if (item.getItemId() == 0) {
        	/* Refresh. */
			app.flushSchedule(scheds.get((int)mi.id).getUrl());
			/* Is this a hack? */
			list.getOnItemClickListener().onItemClick(null, list, mi.position, mi.id);
		} else if (item.getItemId() == 1) {
			/* Delete. */
			db.removeSchedule(scheds.get((int)mi.id).getUrl());
			onResume();
		} else {
			/* Show URL; try a QR code but fall back to a dialog if the app is not installed. */
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
    	lista = new ScheduleAdapter(scheds);
    	list.setAdapter(lista);
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	db.sleep();
    }
    
    private void openNewUrl() {
    	String url = urlBox.getText().toString();
    	if (!url.contains("://"))
    		url = "http://" + url;
    	Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url),
                                   this, ScheduleViewActivity.class);
    	startActivity(intent);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                urlBox.setText(intent.getStringExtra("SCAN_RESULT"));
                openNewUrl();
            }
        }
    }
    
    private class ScheduleAdapter extends BaseAdapter {
    	ArrayList<Element> list;
    	
    	public ScheduleAdapter(AbstractList<DbSchedule> scheds) {
        	ArrayList<Element> now, later, past;
    		now = new ArrayList<Element>();
    		later = new ArrayList<Element>();
    		past = new ArrayList<Element>();
    		for (DbSchedule sched : scheds) {
    			if (sched.getStart().after(new Date()))
    				later.add(new Element(sched));
    			else if (sched.getEnd().before(new Date()))
    				past.add(new Element(sched));
    			else
    				now.add(new Element(sched));
    		}
    		
    		list = new ArrayList<Element>();
			if (now.size() > 0) {
				list.add(new Element(R.string.chooser_now));
				list.addAll(now);
			}
			if (later.size() > 0) {
				list.add(new Element(R.string.chooser_later));
				list.addAll(later);
			}
			if (past.size() > 0) {
				list.add(new Element(R.string.chooser_past));
				list.addAll(past);
			}
    	}
    	
		@Override
		public int getCount() {
			return list.size();
		}

		@Override
		public Object getItem(int position) {
			return list.get(position).item;
		}

		@Override
		public long getItemId(int position) {
			return (long) position;
		}

		@Override
		public boolean isEnabled(int position) {
			return list.get(position).item != null;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			return list.get(position).getView();
		}
		
		private class Element {
			String header;
			DbSchedule item;
			
			public Element(String header_) {
				header = header_;
			}
			
			public Element(int res) {
				header = ChooserActivity.this.getResources().getString(res);
			}
			
			public Element(DbSchedule item_) {
				item = item_;
			}
			
			public View getView() {
				if (item != null) {
					LinearLayout ret = new LinearLayout(ChooserActivity.this);
					TextView title, when;
					
					title = new TextView(ChooserActivity.this);
					title.setText(item.getTitle());
					title.setTextSize(24);
					ret.addView(title);
					
					when = new TextView(ChooserActivity.this);
					when.setText(dateRange(item.getStart(), item.getEnd()));
					when.setTextSize(12);
					ret.addView(when);
					
					ret.setOrientation(LinearLayout.VERTICAL);
					
					return ret;
				} else {
					TextView ret = new TextView(ChooserActivity.this);
					ret.setText("\n" + header);
					ret.setTextSize(18);
					ret.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

					return ret;
				}
			}
			
			private String dateRange(Date start, Date end) {
				String ret = "";
				if (start.getDate() == end.getDate() && start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
					ret = new SimpleDateFormat("d MMMM").format(end);
				else if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
					ret = "" + start.getDate() + "-" + new SimpleDateFormat("d MMMM").format(end);
				else
					ret = new SimpleDateFormat("d MMMM").format(start) + "-" + new SimpleDateFormat("d MMMM").format(end);
				return ret + " " + (1900 + end.getYear());
			}
		}
    }
}