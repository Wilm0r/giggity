package net.gaast.giggity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.time.ZoneId;
import java.util.AbstractList;
import java.util.Collection;
import java.util.LinkedList;

public class ScheduleUI extends Schedule {
	/* Schedule subclass which should carry, among other things, elements that depend on Android.
	 * The Base class should be plain-ish Java so that I can use it externally as well. This is
	 * in fact not just UI stuff but also for example database stuff (user reminders/deletions/etc
	 * persistence) */

	public Giggity app;
	public Db.Connection db;

	private Handler progressHandler;

	private ScheduleUI(Giggity ctx) {
		app = ctx;
	}

	public static ScheduleUI loadSchedule(Giggity ctx, String url, Fetcher.Source source, Handler progressHandler) throws LoadException {
		ScheduleUI ret = new ScheduleUI(ctx);
		ret.progressHandler = progressHandler;

		if (ret.progressHandler == null) {
			// TODO: Test this uncommon codepath to ensure noop handlers are noop and not crashop.
			// (Can't test this now because it looks like reminders are kinda broken... :< )
			ret.progressHandler = new Handler();
		}

		Db.DbSchedule ds = ctx.getDb().getSchedule(url);
		if (ds != null) {
			String tz = ds.getTimezone();
			if (tz != null && !tz.isEmpty()) {
				ret.setInTZ(ZoneId.of(tz));
			}
		}

		Fetcher f = null;

		try {
			f = ctx.fetch(url, source);
			f.setProgressHandler(ret.progressHandler);
			Log.d("Fetcher", "source=" + f.getSource());
			if (f.getSource() == Fetcher.Source.CACHE) {
				ret.progressHandler.sendEmptyMessage(ScheduleViewActivity.LoadProgress.FROM_CACHE);
			}
			ret.loadSchedule(f.getReader(), url);
		} catch (LoadException | IOException e) {
			if (f != null)
				f.cancel();

			Log.e("Schedule.loadSchedule", "Exception while downloading schedule: " + e);
			e.printStackTrace();
			throw new LoadException("Network I/O problem: " + e);
		}
		f.keep();

		// Disable the "fall back to cache" button at this stage if it's even shown, since we're
		// nearly done, only need to apply user/dynamic data.
		ret.progressHandler.sendEmptyMessage(ScheduleViewActivity.LoadProgress.STATIC_DONE);
		if (ret.app.hasSchedule(url)) {
			// TODO: Theoretically, online could still win the race over cached..
			throw new Schedule.LateException();
		}

		ret.db = ret.app.getDb();
		ret.db.setSchedule(ret, url, f.isFresh());
		String md_json = ret.db.getMetadata();
		if (md_json != null) {
			ret.addMetadata(md_json);
		}

		/* From now, changes should be marked to go back into the db. */
		ret.fullyLoaded = true;

		return ret;
	}

	public String getString(int id) {
		return app.getString(id);
	}

	public void setProgressHandler(Handler handler) {
		progressHandler = handler;
	}

	/** Would like to kill this, but still used for remembering currently
	 * viewed day for a schedule. */
	public Db.Connection getDb() {
		return db;
	}

	private InputStream getIconStream() {
		if (getIconUrl() == null || getIconUrl().isEmpty()) {
			return null;
		}

		try {
			Fetcher f = new Fetcher(app, getIconUrl(), Fetcher.Source.CACHE);
			return f.getStream();
		} catch (IOException e) {
			// This probably means it's not in cache. :-( So we'll fetch it in the background and
			// will hopefully succeed on the next call.
		}
		/* For fetching the icon file in the background. */
		Thread iconFetcher = new Thread() {
			@Override
			public void run() {
				Fetcher f;
				try {
					f = new Fetcher(app, getIconUrl(), Fetcher.Source.ONLINE);
				} catch (IOException e) {
					Log.e("getIconStream", "Fetch error: " + e);
					return;
				}
				if (BitmapFactory.decodeStream(f.getStream()) != null) {
					/* Throw-away decode seems to have worked so instruct Fetcher to keep cached. */
					f.keep();
				}
			}
		};
		iconFetcher.start();
		return null;
	}

	public Bitmap getIconBitmap() {
		InputStream stream = getIconStream();
		Bitmap ret = null;
		if (stream != null) {
			ret = BitmapFactory.decodeStream(stream);
			if (ret == null) {
				Log.w("getIconBitmap", "Discarding unparseable file");
				return null;
			}
			if (ret.getHeight() > 512 || ret.getHeight() != ret.getWidth()) {
				Log.w("getIconBitmap", "Discarding, icon not square or >512 pixels");
				return null;
			}
			if (!ret.hasAlpha()) {
				Log.w("getIconBitmap", "Discarding, no alpha layer");
				return null;
			}
		}
		return ret;
	}

	/* Returns true if any of the statuses has changed. */
	public boolean updateRoomStatus() {
		try {
			Fetcher f = new Fetcher(app, roomStatusUrl, Fetcher.Source.ONLINE_NOCACHE);
			return updateRoomStatus(f.slurp());
		} catch (IOException e) {
			Log.d("updateRoomStatus", "Fetch setup failure");
			e.printStackTrace();
			return false;
		}
	}

	protected void applyItem(Item item) {
		if (fullyLoaded) {
			db.saveScheduleItem(item);
		}
		app.updateRemind(item);
	}

	public void initSearch() {
		db.resetIndex(allItems.values());
	}

	public AbstractList<Item> searchItems(String q_) {
		Collection<String> ids = db.searchItems(q_);
		if (ids == null) {
			return null;
		}
		LinkedList<Item> ret = new LinkedList<Item>();
		Log.d("searchItems", "" + ids.size() + " items");
		for (String id : ids) {
//			Log.d("searchItems", "id=" + id + " " + allItems.containsKey(id));
			ret.add(allItems.get(id));
		}
		return ret;
	}


	// Bunch of static utility/UI functions/etc that I originally created this class for.
	static public void exportSelections(Activity ctx, Schedule sched) {
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
		} catch (ActivityNotFoundException | SecurityException e) {
			Giggity.zxingError(ctx);
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
