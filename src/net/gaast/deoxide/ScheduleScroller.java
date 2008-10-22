package net.gaast.deoxide;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public class ScheduleScroller extends FrameLayout {

	private int dragScrollX, dragScrollY;
	private float dragStartX, dragStartY;
	boolean touchDown;
	
	public ScheduleScroller(Context context) {
		super(context);
		touchDown = false;
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
	
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			touchDown = true;
			dragStartX = ev.getX();
			dragStartY = ev.getY();
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_MOVE &&
				   (Math.abs(dragStartX - ev.getX()) +
				    Math.abs(dragStartY - ev.getY())) > 8) {
			dragScrollX = getScrollX();
			dragScrollY = getScrollY();
			touchDown = false;
			return true;
		}
		
		touchDown = false;
		return false;
	}
	
	public boolean onTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_MOVE) {
			int newx, newy;
			
			newx = dragScrollX + (int) (dragStartX - ev.getX());
			newy = dragScrollY + (int) (dragStartY - ev.getY()); 
			
			scrollTo(Math.max(newx, 0), Math.max(newy, 0));
		}
		Log.d("touchevent", "" + ev);
		return true;
	}
	
	public boolean onTrackballEvent(MotionEvent ev) {
		Log.d("tbevent", "" + ev);
		return true;
	}
}
