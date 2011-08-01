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

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.LinearLayout;

/* There's RatingBar, but it's way too huge and can't be resized.
 * Implementing a simple integer stars view instead. */

public class StarsView extends LinearLayout implements OnTouchListener {
	private int numStars, score;
	private ImageView[] stars;
	
	public StarsView(Context ctx) {
		super(ctx);
		
		setGravity(Gravity.CENTER_VERTICAL);
		
		score = 0;
		setNumStars(5);
		
		setOnTouchListener(this);
	}
	
	@Override
	protected void onLayout (boolean changed, int l, int t, int r, int b) {
		int w, i;
		w = r - l;
		for (i = 0; i < numStars; i ++) {
			stars[i].setAdjustViewBounds(true);
			stars[i].setMaxWidth(w / numStars);
		}
		super.onLayout(changed, l, t, r, b);
	}
	
	public void setNumStars(int numStars_) {
		int i;
		
		removeAllViews();
		numStars = numStars_;
		stars = new ImageView[numStars];
		for (i = 0; i < numStars; i ++) {
			stars[i] = new ImageView(getContext());
			addView(stars[i]);
		}
		
		setScore(score);
	}
	
	public void setScore(int score_) {
		int i;
		
		score = score_;
		for (i = 0; i < numStars; i ++) {
			stars[i].setImageResource(i < score ? R.drawable.star_yellow :
				                                  R.drawable.star_black);
		}
	}
	
	public int getScore() {
		return score;
	}

	@Override
	public boolean onTouch(View v, MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN ||
		    ev.getAction() == MotionEvent.ACTION_MOVE) {
			float score_ = (ev.getX() + stars[0].getWidth() / 2) /
			               stars[0].getWidth();
			setScore(Math.max(0, Math.min(numStars, (int) score_)));
			return true;
		} else {
			return false;
		}
	}
}
