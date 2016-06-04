package net.gaast.giggity;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import java.util.AbstractList;
import java.util.ArrayList;

public class EventDialogPager extends ViewPager {
	private Schedule.Item item_;
	private AbstractList<Schedule.Item> items_;
	private int item_index_ = -1;

	public EventDialogPager(Context ctx, Schedule.Item item, AbstractList<Schedule.Item> items) {
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

		setAdapter(new Adapter());
		setCurrentItem(item_index_);
	}


	private class Adapter extends PagerAdapter {
		@Override
		public int getCount() {
			return items_.size();
		}

		@Override
		public Object instantiateItem(ViewGroup parent, int position) {
			EventDialog d = new EventDialog(getContext(), items_.get(position));
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
}
