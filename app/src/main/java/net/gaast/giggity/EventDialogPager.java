package net.gaast.giggity;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import java.util.AbstractList;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

public class EventDialogPager extends ViewPager {
	private Schedule.Item item_;
	private AbstractList<Schedule.Item> items_;
	private int item_index_ = -1;
	private OnClickListener title_click_;
	private String searchQuery_;
	private Insets padding_ = null;

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

		if (Build.VERSION.SDK_INT >= 30) {
			setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
				@NonNull
				@Override
				public WindowInsets onApplyWindowInsets(@NonNull View meh, @NonNull WindowInsets insets) {
					DisplayCutout cut = null;
					Insets r = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
					padding_ = r;
					for (int i = 0; i < getChildCount(); ++i) {
						ViewGroup v = (ViewGroup) getChildAt(i);
						if (v != null) {
							applyPadding((EventDialog) v);
						}
					}
					setClipToPadding(false);

					return insets;
				}
			});
		}
	}

	private void applyPadding(EventDialog d) {
		if (Build.VERSION.SDK_INT >= 30 && padding_ != null) {
			d.setPadding(padding_.left, padding_.top, padding_.right, padding_.bottom);
		}
	}

	public void setTitleClick(OnClickListener title_click) {
		title_click_ = title_click;
	}

	public Schedule.Item getShownItem() {
		return items_.get(getCurrentItem());
	}

	private class Adapter extends PagerAdapter {
		@Override
		public int getCount() {
			return items_.size();
		}

		@Override
		public Object instantiateItem(ViewGroup parent, int position) {
			Schedule.Item item = items_.get(position);
			EventDialog d = new EventDialog(getContext(), item, searchQuery_);
			if (title_click_ != null) {
				d.setTitleClick(title_click_);
			}
			applyPadding(d);
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
