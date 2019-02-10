package net.gaast.giggity;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.AbstractList;

public class ItemSearch extends LinearLayout implements ScheduleViewer {
	private Giggity app;
	private Schedule sched;
	private Activity ctx;

	private SearchQuery query;
	private ProgressBar progress;
	private ScheduleListView scroller;

	public ItemSearch(Activity ctx, Schedule sched) {
		super(ctx);
		this.ctx = ctx;
		app = (Giggity) ctx.getApplication();
		this.sched = sched;
		this.setOrientation(LinearLayout.VERTICAL);

		RelativeLayout.LayoutParams lp;

		query = new SearchQuery();
		query.setBackgroundResource(R.color.primary);
		query.setTextColor(getResources().getColor(R.color.light_text));
		query.setHintTextColor(getResources().getColor(R.color.light_text));
		app.setShadow(query, true);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(query, lp);

		progress = new ProgressBar(ctx);
		progress.setIndeterminate(true);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(progress, lp);

		scroller = new ScheduleListView(ctx);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(scroller, lp);

		query.requestFocus();
		new UpdateIndexTask().execute(true);
	}

	// TODO: Use SearchView here probably?
	private class SearchQuery extends EditText {
		private String lastQuery = "";

		public SearchQuery() {
			super(ctx);
			setHint("Type your query");  // TODO: I18N
			setLines(1);
			setSingleLine();
			setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		}

		@Override
		protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
			if (scroller == null || text.length() == 0) {
				// Method gets called during construction, nope out.
				return;
			}

			int cursor = start + lengthAfter;
			lastQuery = text.subSequence(0, cursor) + "*" + text.subSequence(cursor, text.length());
			Log.d("onTextChanged", lastQuery);

			updateResults();
		}

		@Override
		public void onEditorAction(int actionCode) {
			if (actionCode == EditorInfo.IME_ACTION_SEARCH) {
				// The * will disappear now which could modify the results ... can be annoying. :-(
				lastQuery = getText().toString();
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
			AbstractList<Schedule.Item> res = sched.searchItems(lastQuery);
			scroller.setList(res);
			scroller.refreshContents();
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
			Toast.makeText(ctx, "Rebuilding search index", Toast.LENGTH_SHORT).show();  // I18N
		}

		@Override
		protected void onPostExecute(Boolean b) {
			Log.d("UpdateIndex", "Done! Updating search results");
			removeView(progress);
			query.updateResults();
		}
	}

	@Override
	public void onShow() {
		InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
	}

	@Override
	public void refreshContents() {
	}

	@Override
	public void refreshItems() {
	}

	@Override
	public boolean multiDay() {
		return true;
	}

	@Override
	public boolean extendsActionBar() {
		return true;
	}
}
