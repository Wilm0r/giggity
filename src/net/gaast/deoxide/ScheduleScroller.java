package net.gaast.deoxide;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ScrollView;

public class ScheduleScroller extends ScrollView {

	public ScheduleScroller(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
		/* TODO Will I have to implement this one as well? */
		Log.d("measureChild", "" + parentWidthMeasureSpec + " " + parentHeightMeasureSpec);
		super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
	}
	
	protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
			int parentHeightMeasureSpec, int heightUsed) {
		final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
		
		child.measure(MeasureSpec.makeMeasureSpec(lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED),
				      MeasureSpec.makeMeasureSpec(lp.leftMargin + lp.rightMargin, MeasureSpec.UNSPECIFIED));
	}
}
