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
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/* Instead of the old SimpleScroller, I've finally figured out how to nest V and H ScrollView and
   have it work decently! \o/ Or at least I think I did.

   The (or at least my) trick is to have only one of the two classes manage touch events coming in
   from Android. The inner class overrides the two touch event functions to discard everything, and
   has internal-only functions to receive them only from the outer class.

   This way you I can start accepting touch events for both H and V scrolling simultaneously. Other
   solutions don't do this, or do this in a way that touch events can no longer be passed through to
   child elements because the scroller just eats everything.

   Limitations of this class: There's an inner ViewGroup that contains all the user may care about.
   addView() is intercepted, other functions are not. This could maybe be fixed by making the outer
   class the inner view which I should've done from the beginning and it sounds complicated. :-P
   One other issue is that getScrollY() on the outer class won't work, and I can't fix that because
   it's defined as final by View. Oh well.
 */
public class NestedScroller extends HorizontalScrollView {
	private Activity ctx_;

	private InnerScroller vscroll;

	private int flags_;
	private NestedScroller.Listener listener;

	private int initialX, initialY;
	
	private float distStartX, distStartY;
	private boolean isCallingBack;

	public static final int PINCH_TO_ZOOM = 8;

	/* Keep these in class vars for older API compatibility. */
	/* I forgot by now what was the story, with new API apparently I would not need this,, TODO */
	private float scaleX, scaleY;
	private float pivotX, pivotY;

	public NestedScroller(Activity ctx, int flags) {
		super(ctx);
		ctx_ = ctx;
		flags_ = flags;
		scaleX = scaleY = 1;

		vscroll = new InnerScroller();
		super.addView(vscroll, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		vscroll.setVerticalScrollBarEnabled(false);
		setHorizontalScrollBarEnabled(false);

		vscroll.getViewTreeObserver().addOnScrollChangedListener(vscroll);
	}

	private class InnerScroller extends ScrollView implements ViewTreeObserver.OnScrollChangedListener {
		public InnerScroller() {
			super(ctx_);
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
			/* All touch events should come in via the outer horizontal scroller (using the Int
			   functions below). If Android tries to send them here directly, reject. */
			return false;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			/* It will still try to send them anyway if it can't find any interested child elements.
			   Reject it harder (but pretend that we took it). */
			return true;
		}

		public boolean onInterceptTouchEventInt(MotionEvent event) {
			return super.onInterceptTouchEvent(event);
		}

		public boolean onTouchEventInt(MotionEvent event) {
			/*
			Log.d("nsmove1", "x0 " + event.getX(0) + " sx " + NestedScroller.this.getScrollX());
			Log.d("nsmove1", "y0 " + event.getY(0) + " sy " + getScrollY());
			*/

			// Handle scroll events.
			super.onTouchEvent(event);

			float x0, y0, x1 = 0, y1 = 0;
			x0 = event.getX(0) + NestedScroller.this.getScrollX();
			y0 = event.getY(0) + getScrollY();
			if (event.getPointerCount() > 1) {
				x1 = event.getX(1) + NestedScroller.this.getScrollX();
				y1 = event.getY(1) + getScrollY();
			}

			View c = getChildAt(0);
			if (event.getAction() == MotionEvent.ACTION_MOVE) {
				if (event.getPointerCount() > 1 && (flags_ & PINCH_TO_ZOOM) > 0) {
					/*
					Log.d("nspinch", "x0 " + x0 + " x1 " + x1 + " sx " + NestedScroller.this.getScrollX() + " piv " + c.getPivotX() + " " + pivotX);
					Log.d("nspinch", "y0 " + y0 + " y1 " + y1 + " sy " +                this.getScrollY() + " piv " + c.getPivotY() + " " + pivotY);
					*/

					scaleX = Math.abs(x0 - x1) / distStartX;
					scaleY = Math.abs(y0 - y1) / distStartY;

					/* Crappy multitouch support can result in really high/low numbers.
					 * ×10 seems unlikely already IMHO, so just don't resize in that axis. */
					if (scaleX > 10 || scaleX < 0.1)
						scaleX = 1;
					if (scaleY > 10 || scaleY < 0.1)
						scaleY = 1;

					c.setScaleX(scaleX);
					c.setScaleY(scaleY);
				}
			} else if (event.getAction() == MotionEvent.ACTION_POINTER_2_DOWN) {
				distStartX = Math.abs(x0 - x1);
				distStartY = Math.abs(y0 - y1);

				pivotX = x0;
				pivotY = y0;
				c.setPivotX(pivotX);
				c.setPivotY(pivotY);
			} else if (event.getAction() == MotionEvent.ACTION_UP){
				if (scaleX != 1.0 || scaleY != 1.0) {
					float newx, newy;
					newx = Math.max(0, NestedScroller.this.getScrollX() - pivotX + pivotX * scaleX);
					newy = Math.max(0, getScrollY() - pivotY + pivotY * scaleY);
					listener.onResizeEvent(NestedScroller.this, scaleX, scaleY, (int) newx, (int) newy);
				}
				scaleX = scaleY = 1;
			}

			return true;
		}

		@Override
		public void onScrollChanged() {
			if (listener != null)
				listener.onScrollEvent(NestedScroller.this, NestedScroller.this.getScrollX(), getScrollY());
		}
	}

	@Override
	public void addView(View v) {
		vscroll.addView(v);
	}

	public void setScrollEventListener(NestedScroller.Listener list_) {
		listener = list_;
	}

	@Override
	public void scrollTo(int x, int y) {
		/*
		if (x == 0 || y == 0) {
			Log.d("scrollTo", "scroll to " + x + "," + y + " callback " + isCallingBack);
			Log.d("scrollTo", Log.getStackTraceString(new Exception()));
		}
		*/
		if (!isCallingBack) {
			isCallingBack = true;
			super.scrollTo(x, 0);
			vscroll.scrollTo(0, y);
			isCallingBack = false;
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event)
	{
		if (super.onInterceptTouchEvent(event) || vscroll.onInterceptTouchEventInt(event) || event.getPointerCount() >= 2) {
			onTouchEvent(event);
			return true;
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		super.onTouchEvent(event);
		/* Beware: One ugliness of passing on events like this is that normally a ScrollView will
		   do transformation of the event coordinates which we're not doing here, mostly because
		   things work well enough without doing that.

		   For events that we pass through to the child view, transformation *will* happen (because
		   we're completely ignoring those and let the (H)ScrollView do the transformation for us).
		 */
		vscroll.onTouchEventInt(event);
		return true;
	}

	// Convenience function - scrollTo won't work until after layout time.
	public void setInitialXY(int x, int y) {
		initialX = x;
		initialY = y;
	}

	@Override
	public void onLayout(boolean changed, int left, int top, int right, int bottom) {
		// Workaround: We're disabling scrollTo() here because super.onLayout() will call it with
		// what it believes to be our current coordinates, which are correct on x but not y axis.
		boolean icb = isCallingBack;
		isCallingBack = true;
		super.onLayout(changed, left, top, right, bottom);
		isCallingBack = icb;
		if (initialX > 0 || initialY > 0) {
			Log.d("NestedScroller", "initial: " + initialX + "," + initialY);
			scrollTo(initialX, initialY);
			initialX = initialY = 0;
		}
	}

	public interface Listener {
		void onScrollEvent(NestedScroller src, int scrollX, int scrollY);
		void onResizeEvent(NestedScroller src, float scaleX, float scaleY, int scrollX, int scrollY);
	}
}