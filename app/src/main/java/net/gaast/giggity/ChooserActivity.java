/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2021 Wilmer van der Gaast <wilmer@gaast.net>
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

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.transition.Explode;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.DisplayCutout;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.gaast.giggity.Db.DbSchedule;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ChooserActivity extends Activity implements SwipeRefreshLayout.OnRefreshListener {
	private Db.Connection db;

	private SwipeRefreshLayout refresher;
	private ListView list;
	private ScheduleAdapter lista;
	private Handler seedRefreshMenu;

	private SharedPreferences pref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// IIRC stuff below was another (abandoned and not terribly useful) attempt at enabling
		// edge-to-edge UI on older devices.
//		if (Build.VERSION.SDK_INT >= 30) {
//			WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
//			WindowInsetsControllerCompat windowInsetsController =
//					ViewCompat.getWindowInsetsController(getWindow().getDecorView());
//			if (windowInsetsController != null) {
//				windowInsetsController.setAppearanceLightNavigationBars(false);
//			}
//		}

		//this.setTheme(android.R.style.Theme_Holo);

		// Fancy shared-element animations when opening event dialogs.
		requestWindowFeature(Window.FEATURE_CONTENT_TRANSITIONS);
		//getWindow().setExitTransition(new ChangeImageTransform());
		getWindow().setExitTransition(new Explode());

		/*//test stuff
		Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		long[] pattern = {  };
		v.vibrate(pattern, -1);
		*/

		Giggity app = (Giggity) getApplication();

		db = app.getDb();
		pref = PreferenceManager.getDefaultSharedPreferences(app);

		list = new ListView(this);
		if (Build.VERSION.SDK_INT >= 30) {
			list.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
				@NonNull
				@Override
				public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
					DisplayCutout cut = null;
					Insets r = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
					list.setPadding(r.left, r.top, r.right, r.bottom);
					list.setClipToPadding(false);

					return insets;
				}
			});
		}
		updateList();  // To make sure there's always something on screen.
		refreshSeed(false);  // Possibly find new data then refresh, asynchronously.

		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
			DbSchedule item = (DbSchedule) lista.getItem(position);
			if (item != null) {
				openSchedule(item.getUrl(), item.refreshNow(), view);
			}
			}
		});
		list.setLongClickable(true);
		list.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
				DbSchedule sched = (DbSchedule) lista.getItem((int) mi.id);
				if (sched != null) {
					menu.setHeaderTitle(sched.getTitle());
					menu.add(ContextMenu.NONE, 0, 0, R.string.refresh);
					menu.add(ContextMenu.NONE, 3, 0, R.string.unhide);
					menu.add(ContextMenu.NONE, 1, 0, R.string.hide);
					menu.add(ContextMenu.NONE, 2, 0, R.string.show_url);
				}
			}
		});
		list.setBackgroundResource(R.color.light);
		list.setDividerHeight(0);
		
		/* Filling in the list in onResume(). */
		refresher = new SwipeRefreshLayout(this);
		refresher.setOnRefreshListener(this);
		refresher.addView(list);

		LinearLayout cont = new LinearLayout(this);
		cont.setOrientation(LinearLayout.VERTICAL);
//		cont.setFitsSystemWindows(true);
		cont.addView(refresher, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));

		cont.setBackgroundResource(R.color.primary_dark);
		setContentView(cont);
	}

	private void refreshSeed(boolean force) {
		long seedAge = System.currentTimeMillis() - pref.getLong("last_menu_seed_ts", 0);
		if (force || seedAge < 0 || seedAge > Db.SEED_FETCH_INTERVAL) {
			Log.d("ChooserActivity", "seedAge " + seedAge);

			final Thread loader;

			seedRefreshMenu = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					if (msg.what == 1) {
						Editor p = pref.edit();
						p.putLong("last_menu_seed_ts", System.currentTimeMillis());
						p.commit();

						updateList();
					} else {
						// TODO: Error never gets reported because the built-in file fallback should just work.
						Toast.makeText(ChooserActivity.this,
								getResources().getString(R.string.refresh_failed),
								Toast.LENGTH_SHORT).show();
					}
					refresher.setRefreshing(false);
				}
			};

			loader = new Thread() {
				@Override
				public void run() {
					boolean ok = db.refreshScheduleList();
					if (seedRefreshMenu != null) {
						seedRefreshMenu.sendEmptyMessage(ok ? 1 : 0);
					} else {
						Log.d("Chooser", "I had a handler but you eated it");
					}
				}
			};

			loader.start();
		}
	}

	@Override
	public void onRefresh() {
		/* I guess it's reasonable to have a main menu refresh include a refresh of any to be opened
		   schedule files.
		 */
		Giggity app = (Giggity) getApplication();
		app.flushSchedules();

		refresher.setRefreshing(true);
		refreshSeed(true);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) item.getMenuInfo();
		Giggity app = (Giggity) getApplication();
		DbSchedule sched = (DbSchedule) lista.getItem((int) mi.id);
		if (sched == null) {
		} else if (item.getItemId() == 0) {
			/* Refresh. */
			app.flushSchedule(sched.getUrl());
			openSchedule(sched.getUrl(), true, null);
		} else if (item.getItemId() == 3) {
			/* Unhide. */
			sched.flushHiddenItems();
			/* Refresh. */
			app.flushSchedule(sched.getUrl());
			openSchedule(sched.getUrl(), sched.refreshNow(), null);
		} else if (item.getItemId() == 1) {
			/* Delete. */
			db.hideSchedule(sched.getUrl());
			onResume();
		} else {
			// Long ago this used to show a QR through a ZXing app intent, but that thing is dead,
			// and meh I'm not going to integrate a QR jar just for this kind of functionality. :(
			TextView selectableUrl = new TextView(this);
			selectableUrl.setText(sched.getUrl());
			selectableUrl.setTextIsSelectable(true);
			app.setPadding(selectableUrl, 16, 8, 8, 16);
			new AlertDialog.Builder(ChooserActivity.this)
					.setTitle(sched.getTitle())
					.setView(selectableUrl)
					.show();
		}
		return false;
	}

	@Override
	public void onResume() {
		/* Do this part in onResume so we automatically re-sort the list (and
		 * pick up new items) when returning to the chooser. */
		super.onResume();
		updateList();
	}

	private void updateList() {
		lista = new ScheduleAdapter(db.getScheduleList());
		list.setAdapter(lista);
	}

	@Override
	public void onPause() {
		super.onPause();
		seedRefreshMenu = null;
	}

	private void openSchedule(String url, boolean prefOnline, View animationOrigin) {
		if (!url.contains("://"))
			url = "https://" + url;
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url),
				this, ScheduleViewActivity.class);
		intent.putExtra("PREFER_ONLINE", prefOnline);

		ActivityOptions options = null;
		if (animationOrigin != null) {
			options = ActivityOptions.makeSceneTransitionAnimation(
		               this, animationOrigin, "title");
		}
		startActivity(intent, options != null ? options.toBundle() : null);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(Menu.NONE, 1, 5, R.string.settings)
				.setShortcut('0', 's')
				.setIcon(R.drawable.ic_settings_white_24dp)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(Menu.NONE, 2, 7, R.string.add_dialog)
				.setShortcut('0', 'a')
				.setIcon(R.drawable.ic_add_white_24dp)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case 1:
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case 2:
				showAddDialog();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showAddDialog() {
		AlertDialog.Builder d = new AlertDialog.Builder(this);
		d.setTitle(R.string.add_dialog);

		final EditText urlBox = new EditText(this);
		urlBox.setHint(R.string.enter_url);
		urlBox.setSingleLine();
		urlBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		d.setView(urlBox);

		d.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				openSchedule(urlBox.getText().toString(), false, null);
			}
		});
		d.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		AlertDialog built = d.show();
		/* Apparently the "Go"/"Done" button still just simulates an ENTER keypress. Neat!...
		   http://stackoverflow.com/questions/5677563/listener-for-done-button-on-edittext
		   This is an event handler on the inner textbox. Since it needs a reference to the
		   built dialog we can only set it up now. */
		urlBox.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN &&
						keyCode == KeyEvent.KEYCODE_ENTER) {
					built.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
					return true;
				} else {
					return false;
				}
			}
		});
	}

	private class ScheduleAdapter extends BaseAdapter {
		ArrayList<Element> list;

		public ScheduleAdapter(AbstractList<DbSchedule> scheds) {
			ArrayList<Element> now, later, past, hidden;
			now = new ArrayList<>();
			later = new ArrayList<>();
			past = new ArrayList<>();
			hidden = new ArrayList<>();
			for (DbSchedule sched : scheds) {
				if (sched.getHidden()) {
					hidden.add(new Element(sched));
				} else if (sched.getStart().after(new Date())) {
					later.add(new Element(sched));
				} else if (sched.getEnd().before(new Date())) {
					Element e = new Element(sched);
					if (sched.getAtime().equals(sched.getStart())) {
						e.setUnused();
					}
					past.add(e);
				} else {
					now.add(new Element(sched));
				}
			}

			list = new ArrayList<>();
			addAll(now, R.string.chooser_now);
			addAll(later, R.string.chooser_later);
			addAll(past, R.string.chooser_past);
			addAll(hidden, R.string.chooser_hidden);
		}

		private void addAll(ArrayList<Element> bunch, int title) {
			if (bunch.size() == 0) {
				return;
			}
			bunch.get(0).setFirst();
			bunch.get(bunch.size() - 1).setLast();
			list.add(new Element(title));
			list.addAll(bunch);
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
			int flags;

			final static int FIRST = 1;
			final static int LAST = 2;
			final static int UNUSED = 4;

			public Element(DbSchedule item_) {
				item = item_;
			}

			public Element(int res) {
				header = ChooserActivity.this.getResources().getString(res);
			}

			public void setFirst() {
				flags |= FIRST;
			}

			public void setLast() {
				flags |= LAST;
			}

			public void setUnused() {
				flags |= UNUSED;
			}

			public View getView() {
				Giggity app = (Giggity) getApplication();
				LinearLayout inner = new LinearLayout(ChooserActivity.this);
				RelativeLayout outer = new RelativeLayout(ChooserActivity.this);

				if (item != null) {
					makeScheduleTitleView(inner, item);
					app.setPadding(inner, 10, (flags & FIRST) > 0 ? 0 : 3, 6, (flags & LAST) > 0 ? 10 : 0);

					if ((flags & LAST) == 0) {
						View div = new View(ChooserActivity.this);
						div.setMinimumHeight(app.dp2px(1));
						div.setBackgroundResource(R.color.light);
						app.setPadding(inner.findViewById(R.id.subtitle), 0, 0, 0, 4);
						inner.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
					}

					app.setPadding(outer, 20, 0, 16, (flags & LAST) > 0 ? 16 : 0);
					outer.addView(inner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
					inner.setElevation(app.dp2px(4));
					outer.setClipToPadding(false);
					inner.setClipToPadding(false);

					if ((flags & UNUSED) != 0) {
						inner.findViewById(R.id.title).setAlpha(0.6F);
						inner.findViewById(R.id.subtitle).setAlpha(0.6F);
					}
				} else {
					TextView ret = new TextView(ChooserActivity.this);
					ret.setText(header);
					ret.setTextSize(18);
					ret.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
					ret.setBackgroundResource(R.color.primary);
					ret.setTextColor(getResources().getColor(R.color.light_text));
					app.setPadding(ret, 6, 3, 6, 3);

					inner.addView(ret);
					app.setPadding(inner, 8, 8, 0, 0);

					RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
					lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
					lp.setMargins(app.dp2px(20), 0, app.dp2px(16), 0);
					View blob = new View(ChooserActivity.this);
					blob.setMinimumHeight(app.dp2px(10));
					blob.setBackgroundResource(R.color.light_back);
					app.setPadding(blob, 20, 20, 20, 20);
					outer.addView(blob, lp);

					outer.addView(inner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
					ret.setElevation(app.dp2px(8));
					blob.setElevation(app.dp2px(4));
					inner.setElevation(app.dp2px(4));
					outer.setClipToPadding(false);
					inner.setClipToPadding(false);
				}

				return outer;
			}
		}
	}

	static void makeScheduleTitleView(LinearLayout inner, DbSchedule item) {
		TextView title, when;

		title = new TextView(inner.getContext());
		title.setText(item.getTitle());
		title.setTextSize(22);
		title.setTextColor(inner.getContext().getResources().getColor(R.color.dark_text));
		title.setId(R.id.title);
		inner.addView(title);

		when = new TextView(inner.getContext());
		when.setText(Giggity.dateRange(item.getStart(), item.getEnd()));
		when.setTextSize(12);
		when.setId(R.id.subtitle);
		inner.addView(when);

		inner.setOrientation(LinearLayout.VERTICAL);
		inner.setBackgroundResource(R.color.light_back);
	}
}
