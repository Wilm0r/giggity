package net.gaast.giggity;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.AbstractList;

public class ItemSearch extends LinearLayout implements ScheduleViewer {
	private Giggity app;
	private Schedule sched;
	private Activity ctx;

	private ScheduleListView scroller;

	public ItemSearch(Activity ctx, Schedule sched) {
		super(ctx);
		this.ctx = ctx;
		app = (Giggity) ctx.getApplication();
		this.sched = sched;
		this.setOrientation(LinearLayout.VERTICAL);

		RelativeLayout.LayoutParams lp;

		SearchQuery query = new SearchQuery();
		query.setBackgroundResource(R.color.primary);
		query.setTextColor(getResources().getColor(R.color.light_text));
		query.setHintTextColor(getResources().getColor(R.color.light_text));
		app.setShadow(query, true);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(query, lp);

		scroller = new ScheduleListView(ctx);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(scroller, lp);

		query.requestFocus();
		/* TODO: Make this work, my guess is it can't work in this constructor yet. :<
		InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
		*/
	}

	private class SearchQuery extends EditText {
		public SearchQuery() {
			super(ctx);
			this.setHint("Type your query");  // TODO: I18N
		}

		@Override
		protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
			if (scroller == null || text.length() == 0) {
				// Method gets called during construction, nope out.
				return;
			}
			// TODO: find the first space/separation from start and add a % or so for the search query?

			AbstractList<Schedule.Item> res = sched.searchItems(text.toString());
			scroller.setList(res);
			scroller.refreshContents();
		}
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
