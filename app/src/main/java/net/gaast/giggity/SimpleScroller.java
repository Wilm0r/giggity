/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/* I ended up writing this one long ago because Android did not yet have any horizontal scroller.
   It does now and maybe nesting HSV and SV would do the trick except this thing implements some
   other stuff including pinch to zoom (no double-tap to zoom yet :-/).

   Although the view is a bit annoying (for example not the usual smooth scrolling effects after
   up events which feels unnatural) I'm not terribly interested in redoing it.. :-(
 */
public class SimpleScroller extends FrameLayout {
	private int flags;
	SimpleScroller.Listener listener;

	private int initialX, initialY;
	
	private int dragScrollX, dragScrollY;
	private float dragStartX, dragStartY;
	private float distStartX, distStartY;
	boolean isCallingBack;
	
	private final int dragThreshold = 8;
	
	public static final int HORIZONTAL = 1;
	public static final int VERTICAL = 2;
	public static final int DISABLE_DRAG_SCROLL = 4;
	public static final int PINCH_TO_ZOOM = 8;
	
	public SimpleScroller(Activity ctx, int flags_) {
		super(ctx);
		flags = flags_;
		scaleX = scaleY = 1;
	}

	public void setScrollEventListener(SimpleScroller.Listener list_) {
		listener = list_;
	}

	@Override
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

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if ((flags & DISABLE_DRAG_SCROLL) > 0) {
			/* Pass the events to the "child". */
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			dragStartX = ev.getX();
			dragStartY = ev.getY();
			dragScrollX = getScrollX();
			dragScrollY = getScrollY();
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_MOVE &&
				   (Math.abs(dragStartX - ev.getX()) +
				    Math.abs(dragStartY - ev.getY())) > dragThreshold) {
			return true;
		} else if (ev.getAction() == MotionEvent.ACTION_POINTER_2_DOWN) {
			return onTouchEvent(ev);
		}
		
		return false;
	}
	
	/* Keep these in class vars for older API compatibility. */ 
	private float scaleX, scaleY;
	private float pivotX, pivotY;

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		ViewGroup c = (ViewGroup) this.getChildAt(0);
		if ((flags & DISABLE_DRAG_SCROLL) > 0) {
			/* Pass the events to the "child". */
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
			int newx, newy, maxx, maxy;
			
			maxx = getChildAt(0).getWidth() - getWidth();
			maxy = getChildAt(0).getHeight() - getHeight();
			
			newx = Math.max(0, Math.min(maxx, dragScrollX + (int) (dragStartX - ev.getX())));
			newy = Math.max(0, Math.min(maxy, dragScrollY + (int) (dragStartY - ev.getY()))); 
			
			scrollTo(newx, newy);
			if (ev.getPointerCount() > 1 && (flags & PINCH_TO_ZOOM) > 0) {
				scaleX = Math.abs(ev.getX(1) - ev.getX(0)) / distStartX;
				scaleY = Math.abs(ev.getY(1) - ev.getY(0)) / distStartY;
				pivotX = Math.min(ev.getX(0), ev.getX(1)) + getScrollX();
				pivotY = Math.min(ev.getY(0), ev.getY(1)) + getScrollY();
				/* Crappy multitouch support can result in really high/low numbers.
				 * ×10 seems unlikely already IMHO, so just don't resize in that axis. */
				if (scaleX > 10 || scaleX < 0.1)
					scaleX = 1;
				if (scaleY > 10 || scaleY < 0.1)
					scaleY = 1;

				c.setScaleX(Math.abs(ev.getX(1) - ev.getX(0)) / distStartX);
				c.setScaleY(Math.abs(ev.getY(1) - ev.getY(0)) / distStartY);
				c.setPivotX(ev.getX(0) + getScrollX());
				c.setPivotY(ev.getY(0) + getScrollY());
			}
		} else if (ev.getAction() == MotionEvent.ACTION_POINTER_2_DOWN) {
			distStartX = Math.abs(ev.getX(1) - ev.getX(0));
			distStartY = Math.abs(ev.getY(1) - ev.getY(0));
		} else if (ev.getAction() == MotionEvent.ACTION_UP){
			if (scaleX != 1.0 || scaleY != 1.0) {
				float newx, newy;
				newx = Math.max(0, getScrollX() - pivotX + pivotX * scaleX); 
				newy = Math.max(0, getScrollY() - pivotY + pivotY * scaleY);
				listener.onResizeEvent(this, scaleX, scaleY, (int) newx, (int) newy);
			}
			scaleX = scaleY = 1;
		}
		return true;
	}

	@Override
	public void scrollTo(int x, int y) {
		if (!isCallingBack) {
			isCallingBack = true;
			super.scrollTo(x, y);
			if (listener != null)
				listener.onScrollEvent(this);
			isCallingBack = false;
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		int newx, newy, maxx, maxy;
		
		if (getScrollX() == 0 && getScrollY() == 0) {
			/* If we're already at the top left (possibly still 
			 * starting up?) then we don't have to care. */
			return;
		}
		
		maxx = getChildAt(0).getWidth() - w;
		maxy = getChildAt(0).getHeight() - h;
		
		newx = Math.max(0, Math.min(maxx, getScrollX()));
		newy = Math.max(0, Math.min(maxy, getScrollY())); 
		
		scrollTo(newx, newy);
	}

	@Override
	public void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		Log.d("SimpleScroller", "onLayout");
		if (initialX > 0 || initialY > 0) {
			Log.d("ScrollTo", "" + initialX + "," + initialY);
			scrollTo(initialX, initialY);
		}
	}

	public void setInitialXY(int x, int y) {
		initialX = x;
		initialY = y;
	}
	
	public interface Listener {
		void onScrollEvent(SimpleScroller src);
		void onResizeEvent(SimpleScroller src, float scaleX, float scaleY, int scrollX, int scrollY);
	}
}
