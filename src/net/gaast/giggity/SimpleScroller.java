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
import android.widget.FrameLayout;

public class SimpleScroller extends FrameLayout {
	private int flags;
	SimpleScroller.Listener listener;
	
	private int dragScrollX, dragScrollY;
	private float dragStartX, dragStartY;
	boolean isCallingBack;
	
	private final int dragThreshold = 8;
	
	public static final int HORIZONTAL = 1;
	public static final int VERTICAL = 2;
	public static final int DISABLE_DRAG_SCROLL = 4;
	
	public SimpleScroller(Activity ctx, int flags_) {
		super(ctx);
		flags = flags_;
	}
	
	public void setScrollEventListener(SimpleScroller.Listener list_) {
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
		if ((flags & DISABLE_DRAG_SCROLL) > 0) {
			/* Pass the events to the "child". */
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			dragStartX = ev.getX();
			dragStartY = ev.getY();
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_MOVE &&
				   (Math.abs(dragStartX - ev.getX()) +
				    Math.abs(dragStartY - ev.getY())) > dragThreshold) {
			dragScrollX = getScrollX();
			dragScrollY = getScrollY();
			return true;
		}
		
		return false;
	}
	
	public boolean onTouchEvent(MotionEvent ev) {
		if ((flags & DISABLE_DRAG_SCROLL) > 0) {
			/* Pass the events to the "child". */
			return false;
		} else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
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
	
	public interface Listener {
		public void onScrollEvent(SimpleScroller src);
	}
}
