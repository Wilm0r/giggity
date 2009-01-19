package net.gaast.deoxide;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

public class ShuffleLayout extends LinearLayout {
	/* Trying to be like LinearLayout, and the user can drag items 
	 * to reorder them. Only supports vertical layouts (for now?).
	 * As an optimization, all views must have the same height. */
	
	private float dragStartY;
	private int dragStartChild;
	
	private Listener listener;
	
	private final int dragThreshold = 8;
	
	public ShuffleLayout(Activity ctx) {
		super(ctx);
		setOrientation(LinearLayout.VERTICAL);
	}
	
	public void setShuffleEventListener(Listener listener_) {
		listener = listener_;
	}
	
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			dragStartY = ev.getY();
			dragStartChild = (int) ev.getY() / getChildAt(0).getHeight();
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_MOVE &&
				   (Math.abs(dragStartY - ev.getY())) > dragThreshold) {
			return true;
		}
		
		return false;
	}
	
	public boolean onTouchEvent(MotionEvent ev) {
		if (ev.getY() < getChildAt(dragStartChild).getTop() - dragThreshold) {
			Log.d("ote", "Move up");
			swapChildren(dragStartChild - 1);
			dragStartChild --;
		} else if (ev.getY() > getChildAt(dragStartChild).getBottom() + dragThreshold) {
			Log.d("ote", "Move down");
			swapChildren(dragStartChild);
			dragStartChild ++;
		}
		return true;
	}
	
	private void swapChildren(int top) {
		View tmpView;
		int a = top, b = top + 1;
		
		tmpView = getChildAt(a);
		removeView(getChildAt(a));
		addView(tmpView, b);
		requestLayout();
		listener.onSwapEvent(getChildAt(a).getTop(), getChildAt(b).getTop());
	}
	
	public interface Listener {
		/* When this comes in, swap items at y1 and y2. */
		public void onSwapEvent(int y1, int y2);
	}
}
