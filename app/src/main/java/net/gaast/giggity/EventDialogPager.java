package net.gaast.giggity;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class EventDialogPager extends ViewPager {
	private Schedule.Item item_;
	private AbstractList<Schedule.Item> items_;
	private int item_index_;

	public EventDialogPager(Context ctx, Schedule.Item item, AbstractList<Schedule.Item> items) {
		super(ctx);

		item_ = item;
		items_ = items;
		if (items_ != null && items_.size() > 1) {
			int i = 0;
			for (Schedule.Item listed : items) {
				if (listed == item) {
					item_index_ = i;
					break;
				}
				++i;
			}
		} else {
			items_ = new ArrayList<>();
			items_.add(item);
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
