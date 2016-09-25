package net.gaast.giggity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;

public class ScheduleUI {
	static public void exportSelections(Context ctx, Schedule sched) {
		Schedule.Selections sel = sched.getSelections();
		
		if (sel == null) {
			Toast.makeText(ctx, R.string.no_selections, Toast.LENGTH_SHORT).show();
			return;
		}
		
		byte[] binsel = sel.export();
		Intent intent = new Intent("com.google.zxing.client.android.ENCODE");
		intent.putExtra("ENCODE_TYPE", "TEXT_TYPE");
		intent.putExtra("ENCODE_SHOW_CONTENTS", false);
		try {
			intent.putExtra("ENCODE_DATA", new String(binsel, "iso8859-1"));
		} catch (UnsupportedEncodingException e) {
			/* Fuck off, Java. */
		}
		try {
			ctx.startActivity(intent);
			Toast.makeText(ctx, R.string.qr_tip, Toast.LENGTH_LONG).show();
		} catch (android.content.ActivityNotFoundException e) {
			new AlertDialog.Builder(ctx)
			  .setTitle("Not available")
			  .setMessage("This functionality needs a Barcode Scanner application")
			  .show();
		}

	}
	
	static public class ImportSelections extends Dialog implements OnClickListener {
		Context ctx;
		LinearLayout opts;
		CheckBox[] cbs;
		Schedule mine;
		Schedule.Selections other;
		
		static final int KEEP_REMIND = 0;
		static final int KEEP_HIDDEN = 1;
		static final int IMPORT_REMIND = 2;
		static final int IMPORT_HIDDEN = 3;
		
		public ImportSelections(Context ctx_, Schedule mine_, Schedule.Selections other_) {
			super(ctx_);
			ctx = ctx_;
			mine = mine_;
			other = other_;
			
			setTitle(R.string.import_selections);
			setCanceledOnTouchOutside(false);
			
			opts = new LinearLayout(ctx);
			opts.setOrientation(LinearLayout.VERTICAL);
			
			cbs = new CheckBox[4];
			int i;
			String[] choices = ctx.getResources().getStringArray(R.array.import_selections_options);
			for (i = 0; i < 4; i ++) {
				cbs[i] = new CheckBox(ctx);
				cbs[i].setChecked(i != IMPORT_HIDDEN);
				cbs[i].setText(choices[i]);
				opts.addView(cbs[i]);
			}
			
			Button ok = new Button(ctx);
			ok.setText(R.string.ok);
			ok.setOnClickListener(this);
			opts.addView(ok);
			
			setContentView(opts);
		}

		@Override
		public void onClick(View v) {
			mine.setSelections(other, cbs);
			ScheduleViewActivity act = (ScheduleViewActivity) ctx;
			act.redrawSchedule();
			dismiss();
		}
	}

	/** Click-listener to open geo: URL belonging to a room. */
	public static View.OnClickListener locationClickListener(final Context ctx, final Schedule.Line line) {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Uri uri = Uri.parse(line.getLocation());
				Intent geoi = new Intent(Intent.ACTION_VIEW, uri);
				ctx.startActivity(geoi);
			}
		};
	}
}
