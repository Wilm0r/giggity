package net.gaast.giggity;

import android.app.Activity;
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
	private int flags;
	
	public static final int DISABLE_DRAG_SHUFFLE = 1;
	
	private final int dragThreshold = 8;
	
	public ShuffleLayout(Activity ctx, int flags_) {
		super(ctx);
		setOrientation(LinearLayout.VERTICAL);
		flags = flags_;
	}
	
	public void setShuffleEventListener(Listener listener_) {
		listener = listener_;
	}
	
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if ((flags & DISABLE_DRAG_SHUFFLE) > 0) {
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
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
		if ((flags & DISABLE_DRAG_SHUFFLE) > 0) {
			return false;
		} else if (ev.getY() < 0 || ev.getY() > getHeight()) {
			return true;
		} else if (ev.getY() < getChildAt(dragStartChild).getTop() - dragThreshold) {
			swapChildren(dragStartChild - 1);
			dragStartChild --;
		} else if (ev.getY() > getChildAt(dragStartChild).getBottom() + dragThreshold) {
			swapChildren(dragStartChild);
			dragStartChild ++;
		}
		return true;
	}
	
	public void swapChildren(int top) {
		View tmpView;
		int a = top, b = top + 1;
		
		tmpView = getChildAt(a);
		removeView(getChildAt(a));
		addView(tmpView, b);
		
		if (listener != null)
			listener.onSwapEvent(top);
	}
	
	/* Other views may want to be informed when we reorder things
	 * (and reorder related views or something like that). */
	public interface Listener {
		public void onSwapEvent(int top);
	}
}
