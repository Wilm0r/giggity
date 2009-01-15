package net.gaast.deoxide;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public class SimpleScroller extends FrameLayout {
	private int flags;
	SimpleScrollerListener listener;
	
	private int dragScrollX, dragScrollY;
	private float dragStartX, dragStartY;
	boolean touchDown;
	boolean isCallingBack;
	
	private final int dragThreshold = 8;
	
	public static final int HORIZONTAL = 1;
	public static final int VERTICAL = 2;
	
	public SimpleScroller(Activity ctx, int flags_) {
		super(ctx);
		flags = flags_;
		touchDown = false;
	}
	
	public void setScrollEventListener(SimpleScrollerListener list_) {
		listener = list_;
	}
	
	protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
		/* TODO Will I have to implement this one as well? */
		Log.d("measureChild", "" + parentWidthMeasureSpec + " " + parentHeightMeasureSpec);
		super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
	}
	
	protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
			int parentHeightMeasureSpec, int heightUsed) {
		final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
		
		int w, h;
		
		if ((flags & HORIZONTAL) > 0) {
			w = MeasureSpec.makeMeasureSpec(lp.leftMargin + lp.rightMargin, MeasureSpec.UNSPECIFIED);
		} else {
			w = getChildMeasureSpec(parentWidthMeasureSpec,
					lp.leftMargin + 
					lp.rightMargin + widthUsed, lp.width);
		}
		
		if ((flags & VERTICAL) > 0) {
			h = MeasureSpec.makeMeasureSpec(lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);
		} else {
			h = getChildMeasureSpec(parentHeightMeasureSpec,
					lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
		}
		
		child.measure(w, h);
	}
	
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			touchDown = true;
			dragStartX = ev.getX();
			dragStartY = ev.getY();
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_MOVE &&
				   (Math.abs(dragStartX - ev.getX()) +
				    Math.abs(dragStartY - ev.getY())) > dragThreshold) {
			dragScrollX = getScrollX();
			dragScrollY = getScrollY();
			touchDown = false;
			return true;
		}
		
		touchDown = false;
		return false;
	}
	
	public boolean onTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			dragStartX = ev.getX();
			dragStartY = ev.getY();
			dragScrollX = getScrollX();
			dragScrollY = getScrollY();
		}
		else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
			int newx, newy, maxx, maxy;
			
			maxx = getChildAt(0).getWidth() - getWidth();
			maxy = getChildAt(0).getHeight() - getHeight();
			
			newx = Math.max(0, Math.min(maxx, dragScrollX + (int) (dragStartX - ev.getX())));
			newy = Math.max(0, Math.min(maxy, dragScrollY + (int) (dragStartY - ev.getY()))); 
			
			scrollTo(newx, newy);
		}
		return true;
	}
	
	public boolean onTrackballEvent(MotionEvent ev) {
		Log.d("tbevent", "" + ev);
		return true;
	}
	
	public void scrollTo(int x, int y) {
		if (!isCallingBack) {
			isCallingBack = true;
			super.scrollTo(x, y);
			if (listener != null)
				listener.onScrollEvent(this);
			isCallingBack = false;
		}
	}
}
