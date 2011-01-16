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
