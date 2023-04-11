package net.gaast.giggity;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.AbstractList;
import java.util.ArrayList;

public class ItemSearch extends LinearLayout implements ScheduleViewer {
	private Giggity app;
	private Schedule sched;
	private Activity ctx;

	private SearchQuery query;
	private ProgressBar progress;
	private ScheduleListView resultList;
	private QueryHistory queryList;

	public ItemSearch(Activity ctx, Schedule sched) {
		super(ctx);
		this.ctx = ctx;
		app = (Giggity) ctx.getApplication();
		this.sched = sched;
		this.setOrientation(LinearLayout.VERTICAL);

		RelativeLayout.LayoutParams lp;

		LinearLayout queryOuter = new LinearLayout(ctx);
		queryOuter.setBackgroundResource(R.color.search_back);
		app.setShadow(queryOuter, true);
		app.setPadding(queryOuter, 16, 0, 16, 16);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(queryOuter, lp);
		queryOuter.setClipToPadding(false);

		LinearLayout queryInner = new LinearLayout(ctx);
		ImageView icon = new ImageView(ctx);
		icon.setImageResource(R.drawable.ic_search_black_24dp);
		app.setPadding(icon, 8, 4, 8, 4);
		icon.setForegroundGravity(Gravity.CENTER);
		queryInner.addView(icon, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

		query = new SearchQuery();
		query.setTextColor(getResources().getColor(R.color.dark_text));
		query.setHintTextColor(getResources().getColor(R.color.light_text_on_white));
		queryInner.setBackgroundResource(R.color.light_back);
		queryInner.setElevation(app.dp2px(4));
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		queryInner.addView(query, lp);

		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		queryOuter.addView(queryInner, lp);

		progress = new ProgressBar(ctx);
		progress.setIndeterminate(true);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(progress, lp);

		queryList = new QueryHistory();
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(queryList, lp);

		resultList = new ScheduleListView(ctx);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		addView(resultList, lp);

		new UpdateIndexTask().execute(true);
	}

	// TODO: Use SearchView here maybe?
	private class SearchQuery extends EditText {
		private String lastQuery = "";

		public SearchQuery() {
			super(ctx);
			setHint(getContext().getString(R.string.type_query_prompt));
			setLines(1);
			setSingleLine();
			setImeOptions(EditorInfo.IME_ACTION_SEARCH);

			// This is only really relevant when running inside the emulator it seems, where hitting
			// Enter on the physical keyboard by default isn't the same like clicking/tapping Go.
			setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(View view, int i, KeyEvent keyEvent) {
					if (keyEvent.getAction() == KeyEvent.ACTION_UP && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
						onEditorAction(EditorInfo.IME_ACTION_SEARCH);
					}
					return false;
				}
			});
		}

		@Override
		protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
			if (resultList == null || queryList == null) {
				// Method gets called during construction, nope out.
				return;
			}
			if (text.length() == 0) {
				// Flush query so we'll display query history list (again)
				lastQuery = "";
			} else {
				int cursor = start + lengthAfter;
				lastQuery = text.subSequence(0, cursor) + "*" + text.subSequence(cursor, text.length());
				Log.d("onTextChanged", lastQuery);
			}

			updateResults();
		}

		@Override
		public void onEditorAction(int actionCode) {
			if (actionCode == EditorInfo.IME_ACTION_SEARCH) {
				// The * will disappear now which could modify the results ... can be annoying. :-(
				lastQuery = getText().toString();
				app.getDb().addSearchQuery(lastQuery);
				updateResults();

				// Keyboard obscures search results so go away!
				View view = ctx.findViewById(android.R.id.content);
				Log.d("hide kb", ""+ view);
				if (view != null) {
					InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
				}
			}
		}

		private void updateResults() {
			if (lastQuery.isEmpty()) {
				queryList.setVisibility(VISIBLE);
				queryList.reload();
				resultList.setVisibility(GONE);
			} else {
				queryList.setVisibility(GONE);
				resultList.setVisibility(VISIBLE);
				AbstractList res = sched.searchItems(lastQuery);
				if (res != null) {
					if (res.size() == 0) {
						res.add(ctx.getString(R.string.search_results_empty));
					}
					resultList.setList(res);
					resultList.refreshContents();
				} else {
					Toast.makeText(ctx, "Database query syntax error", Toast.LENGTH_SHORT).show();  // I18N
				}
			}
		}
	}

	private class QueryHistory extends ListView {
		AbstractList<String> list = new ArrayList<>();

		public QueryHistory() {
			super(ctx);
			setDividerHeight(0);
			setAdapter(new Adapter());
			setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
					query(list.get(i));
				}
			});
			setOnItemLongClickListener(new OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
					query(list.get(i));
					app.getDb().forgetSearchQuery(list.get(i));
					return true;
				}
			});
			reload();
		}

		private void query(String q) {
			query.setText(q);
			query.setSelection(q.length());
			query.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
		}

		public void reload() {
			list = app.getDb().getSearchHistory();
			Adapter a = (Adapter) getAdapter();
			a.notifyDataSetChanged();
			ItemSearch.this.refreshDrawableState();
		}

		private class Adapter extends BaseAdapter {
			@Override
			public int getCount() {
				return list.size();
			}

			@Override
			public Object getItem(int i) {
				return list.get(i);
			}

			@Override
			public long getItemId(int i) {
				return i;
			}

			@Override
			public View getView(int i, View view, ViewGroup viewGroup) {
				LinearLayout ret = new LinearLayout(ctx);

				ImageView icon = new ImageView(ctx);
				icon.setImageResource(R.drawable.ic_history_black_24dp);
				app.setPadding(icon, 4, 4, 12, 4);
				ret.addView(icon, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

				TextView text = new TextView(ctx);
				text.setText(list.get(i));
				text.setGravity(Gravity.CENTER_VERTICAL);
				ret.addView(text, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

				app.setPadding(ret, 8, 8,8 ,8);

				return ret;
			}
		}
	}

	private class UpdateIndexTask extends AsyncTask<Boolean, Boolean, Boolean> {
		@Override
		protected Boolean doInBackground(Boolean... booleans) {
			((ScheduleUI) sched).initSearch();
			return true;
		}

		@Override
		protected void onPreExecute() {
			Log.d("UpdateIndex", "Start");
			// Don't show this for now as hopefully most of the time it's already done, and making
			// the toast conditional is more hassle than it's worth to me now. There's the indicator.
			// Toast.makeText(ctx, "Rebuilding search index", Toast.LENGTH_SHORT).show();  // I18N
		}

		@Override
		protected void onPostExecute(Boolean b) {
			Log.d("UpdateIndex", "Done! Updating search results");
			progress.setVisibility(GONE);
			query.updateResults();
		}
	}

	@Override
	public void onShow() {
		// Focus query field and auto-show keyboard only if there's no query entered yet and/or if
		// the results list is almost empty.
		if (query.getText().toString().isEmpty() ||
		    (resultList != null && resultList.list.size() <= 2)) {
			query.requestFocus();
			app.showKeyboard(getContext(), query);
		} else {
			// If there's something in the query field already then let them browse other results
			// first. Unless there barely were any. :)
			query.clearFocus();
		}
	}

	@Override
	public void refreshContents() {
		resultList.refreshContents();
	}

	@Override
	public void refreshItems() {
		resultList.refreshItems();
	}

	@Override
	public boolean multiDay() {
		return true;
	}

	@Override
	public boolean extendsActionBar() {
		return true;
	}

	public String getQuery() { return query.lastQuery; }
}
