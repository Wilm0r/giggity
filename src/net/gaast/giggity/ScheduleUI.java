package net.gaast.giggity;

import java.io.UnsupportedEncodingException;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

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
		ctx.startActivity(intent);
	}
	
	static public class ImportSelections extends Dialog {
		Context ctx;
		LinearLayout opts;
		CheckBox[] cbs;
		
		static final int KEEP_REMIND = 0;
		static final int KEEP_HIDDEN = 1;
		static final int IMPORT_REMIND = 2;
		static final int IMPORT_HIDDEN = 3;
		
		public ImportSelections(Context ctx_, Schedule mine, Schedule.Selections other) {
			super(ctx_);
			ctx = ctx_;
			
			opts = new LinearLayout(ctx);
			opts.setOrientation(LinearLayout.VERTICAL);
			
			cbs = new CheckBox[4];
			int i;
			for (i = 0; i < 4; i ++) {
				cbs[i] = new CheckBox(ctx);
				if (i != IMPORT_HIDDEN)
					cbs[i].setChecked(true);
			}
			
			
		}
	}
}
