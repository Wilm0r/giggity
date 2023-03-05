package net.gaast.giggity;

import android.content.Context;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import java.util.AbstractList;
import java.util.ArrayList;

public class EventDialogPager extends ViewPager {
	private Schedule.Item item_;
	private AbstractList<Schedule.Item> items_;
	private int item_index_ = -1;
	private OnClickListener title_click_;
	private String searchQuery_;

	public EventDialogPager(Context ctx, Schedule.Item item, AbstractList<Schedule.Item> items, String searchQuery) {
		super(ctx);

		item_ = item;
		items_ = items;
		if (items_ != null && items_.size() > 0) {
			int i = 0;
			for (Schedule.Item listed : items) {
				if (listed == item) {
					item_index_ = i;
					break;
				}
				++i;
			}
		}

		if (item_index_ == -1 ) {
			if (items_ == null) {
				items_ = new ArrayList<>();
			}
			items_.add(0, item);
			item_index_ = 0;
		}

		searchQuery_ = searchQuery;

		setAdapter(new Adapter());
		setCurrentItem(item_index_);
	}

	public void setTitleClick(OnClickListener title_click) {
		title_click_ = title_click;
	}

	public Schedule.Item getShownItem() {
		return items_.get(getCurrentItem());
	}

	public View getHeader() {
		ViewGroup v = (ViewGroup) getAdapter().instantiateItem(this, item_index_);
		return v.findViewById(R.id.header);
	}

	private class Adapter extends PagerAdapter {
		@Override
		public int getCount() {
			return items_.size();
		}

		@Override
		public Object instantiateItem(ViewGroup parent, int position) {
			EventDialog d = new EventDialog(getContext(), items_.get(position), searchQuery_);
			if (title_click_ != null) {
				d.setTitleClick(title_click_);
			}
			parent.addView(d);
			return d;
		}

		@Override
		public void destroyItem(ViewGroup parent, int position, Object view) {
			parent.removeView((View) view);
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}
	}

	public void saveScroll() {
		EventDialog ed = (EventDialog) this.getChildAt(0);
		ed.saveScroll();
	}
}
