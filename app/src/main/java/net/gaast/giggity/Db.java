/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2021 Wilmer van der Gaast <wilmer@gaast.net>
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

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import static java.lang.Math.log;

public class Db {
	private Giggity app;
	private Helper dbh;
	private static final int dbVersion = 19;
	private int oldDbVer = dbVersion;
	private SharedPreferences pref;

	public Db(Application app_) {
		app = (Giggity) app_;
		pref = PreferenceManager.getDefaultSharedPreferences(app);
		dbh = new Helper(app_, "giggity", null, dbVersion);
	}
	
	public Connection getConnection() {
		return new Connection();
	}
	
	private class Helper extends SQLiteOpenHelper {
		public Helper(Context context, String name, CursorFactory factory,
				int version) {
			super(context, name, factory, version);
		}
	
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.i("DeoxideDb", "Creating new database");
			db.execSQL("Create Table schedule (sch_id Integer Primary Key AutoIncrement Not Null, " +
			                                  "sch_title VarChar(128), " +
			                                  "sch_url VarChar(256), " +
			                                  "sch_atime Integer, " +
			                                  "sch_rtime Integer, " +
			                                  "sch_itime Integer, " +
			                                  "sch_refresh_interval Integer, " +
			                                  "sch_start Integer, " +
			                                  "sch_end Integer, " +
			                                  "sch_timezone VarChar(128), " +
			                                  "sch_id_s VarChar(128), " +
			                                  "sch_metadata VarChar(10240), " +
			                                  "sch_day Integer)");
			db.execSQL("Create Table schedule_item (sci_id Integer Primary Key AutoIncrement Not Null, " +
			                                       "sci_sch_id Integer Not Null, " +
			                                       "sci_id_s VarChar(128), " +
			                                       "sci_remind Boolean, " +
			                                       "sci_hidden Boolean, " +
			                                       "sci_stars Integer(2) Null)");
			db.execSQL("Create Virtual Table item_search Using FTS4" +
			           "(sch_id Unindexed, sci_id_s Unindexed, title, subtitle, description, speakers, track)");
			db.execSQL("Create Table search_history (hst_id Integer Primary Key AutoIncrement Not Null, " +
			           "hst_query VarChar(128), " +
					   "hst_atime Integer)");

			// Immediately populate from in-apk seed file. Otherwise new installs, in case of network
			// issues, may just open up with a blank screen.
			updateData(db, false);

			oldDbVer = 0;
		}
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.i("DeoxideDb", "Upgrading from database version " + oldVersion + " to " + newVersion);

			if (oldVersion < 8) {
				/* Version 8 adds start/end time columns to the db. */
				try {
					db.execSQL("Alter Table schedule Add Column sch_start Integer");
					db.execSQL("Alter Table schedule Add Column sch_end Integer");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
					e.printStackTrace();
				}
			}
			if (oldVersion < 11) {
				/* Version 10 adds rtime column. */
				try {
					db.execSQL("Alter Table schedule Add Column sch_rtime Integer");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
					e.printStackTrace();
				}
			}
			if (oldVersion < 12) {
				/* Version 12 adds hidden column. */
				try {
					db.execSQL("Alter Table schedule_item Add Column sci_hidden Boolean");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
					e.printStackTrace();
				}
			}
			if (oldVersion < 13) {
				/* Version 13 adds big metadata field. */
				try {
					db.execSQL("Alter Table schedule Add Column sch_metadata VarChar(10240)");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
					e.printStackTrace();
				}
			}
			if (oldVersion < 15) {
				/* Version 14 added FTS, 15 adds the itime field to avoid needless reindexing. */
				try {
					db.execSQL("Alter Table schedule Add Column sch_itime Integer");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
					e.printStackTrace();
				}
			}
			if (oldVersion < 16) {
				/* ItemSearch history stored in database. */
				try {
					db.execSQL("Create Table search_history (hst_id Integer Primary Key AutoIncrement Not Null, " +
							   "hst_query VarChar(128), " +
							   "hst_atime Integer)");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe table already exists?");
					e.printStackTrace();
				}
			}
			if (oldVersion < 17) {
				/* This is a little more work so "shell out". */
				mergeDuplicateUrls(db);
			}
			if (oldVersion < 18) {
				/* Version 18 uses menu.json refresh_interval instead of 1d default. */
				try {
					db.execSQL("Alter Table schedule Add Column sch_refresh_interval Integer");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
					e.printStackTrace();
				}
			}
			if (oldVersion < 19) {
				try {
					db.execSQL("Alter Table schedule Add Column sch_timezone VarChar(128)");
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
					e.printStackTrace();
				}
			}

			if (oldVersion < 18) {
				/* Full-text search! FTS4 doesn't exactly do Alter Table anyway so don't try. */
				try {
					db.execSQL("Drop Table If Exists item_search");
					db.execSQL("Create Virtual Table item_search Using FTS4" +
							           "(sch_id Unindexed, sci_id_s Unindexed, title, subtitle, description, speakers, track)");

					// We've just recreated the search index table, so flush all indexing timestamps
					// that have now become lies.
					ContentValues row = new ContentValues();
					row.put("sch_itime", 0);
					db.update("schedule", row, "", null);
				} catch (SQLiteException e) {
					Log.e("DeoxideDb", "SQLite error, maybe FTS support is missing?");
					e.printStackTrace();
				}
			}

			// Don't think the Math.min is necessary (anymore). I wrote this possibly >10y ago
			// assuming maybe that this function gets called multiple times?
			oldDbVer = Math.min(oldDbVer, oldVersion);
			Log.d("deoxideDb", "Schema updated " + oldDbVer + "→" + dbVersion);
			if (oldDbVer < dbVersion && newVersion == dbVersion) {
				updateData(db, false);
			}
		}

		private void mergeDuplicateUrls(SQLiteDatabase db) {
			// https://github.com/Wilm0r/giggity/issues/134
			// That string ID should never have been and may have resulted in duplicate entries
			// in some folks' databases. Clean that up now and try to do so nicely
			// (preserving selections).
			Cursor q = db.rawQuery("Select sch_id, sch_url From schedule", null);
			HashMap<String, Integer> urlId = new HashMap<>();  // URL → db ID
			HashMap<Integer, Integer> idId = new HashMap<>();  // dupe id → preserve ID
			while (q.moveToNext()) {
				if (urlId.containsKey(q.getString(1))) {
					idId.put(q.getInt(0), urlId.get(q.getString(1)));
				} else {
					urlId.put(q.getString(1), q.getInt(0));
				}
			}
			q.close();  // WTF isn't this a garbage-collected language?
			// Update sci_sch_id refs
			for (Map.Entry e : idId.entrySet()) {
				ContentValues row = new ContentValues();
				row.put("sci_sch_id", (Integer) e.getValue());
				db.update("schedule_item", row, "sci_sch_id = ?", new String[]{""+(Integer)e.getKey()});
			}
			// Remove the extra schedule table row
			for (Map.Entry e : idId.entrySet()) {
				db.delete("schedule", "sch_id = ?", new String[]{"" + e.getKey()});
			}
		}

		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// Bogus implementation which I only intend to use during testing.
		}
	}
	
	/* For ease of use, seed the main menu with some known schedules. */
	public boolean updateData(SQLiteDatabase db, boolean online) {
		Seed seed = loadSeed(online ? SeedSource.ONLINE : SeedSource.CACHED);
		Seed localSeed = loadSeed(SeedSource.BUILT_IN);
		/* Pick the best one. localSeed *can* be newer than the cached one. Should not
		 * ever be newer than the online one though. */
		if (seed != null && localSeed != null) {
			if (localSeed.version > seed.version)
				seed = localSeed;
		} else {
			if (seed == null)
				seed = localSeed;
			if (seed == null) {
				Log.w("DeoxideDb.updateData", "Failed to fetch both seeds, uh oh..");
				return false;
			}
		}

		int version = pref.getInt("last_menu_seed_version", 0);
		int newver = seed.version;

		Log.d("DeoxideDb.versions", "" + seed.version + " " + version + " " + oldDbVer + " " + dbVersion);
		if (seed.version <= version && oldDbVer == dbVersion) {
			/* No updates required, both data and structure are up to date. */
			Log.d("DeoxideDb.updateData", "Already up to date: " + version + " " + oldDbVer);
			return true;
		}
		
		for (Seed.Schedule sched : seed.schedules) {
			updateSchedule(db, sched);
		}
		
		if (newver != version) {
			Editor p = pref.edit();
			p.putInt("last_menu_seed_version", newver);
			p.commit();
		}
		return true;
	}

	private void updateSchedule(SQLiteDatabase db, Seed.Schedule sched) {
		if (sched.start.equals(sched.end)) {
			/* If it's one day only, avoid having start == end. Pretend it's from 6:00 'til 18:00 or something. */
			sched.start.setHours(6);
			sched.end.setHours(18);
		} else {
			/* For different days, pretend the even't from noon to noon. In both cases, we'll have exact times
			 * once we load the schedule for the first time. */
			sched.start.setHours(12);
			sched.end.setHours(12);
		}

		Cursor q = db.rawQuery("Select sch_id From schedule Where sch_url = ?", new String[]{sched.url});

		ContentValues row = new ContentValues();
		if (q.getCount() == 0) {
			/* Only needed when creating a brand new entry (don't overwrite atime otherwise!) */
			row.put("sch_url", sched.url);
			row.put("sch_atime", sched.start.getTime() / 1000);
		}
		if (q.getCount() == 0 || oldDbVer < 8) {
			/* This bit also needed if the database version was ridiculously ancient (unlikely) */
			row.put("sch_start", sched.start.getTime() / 1000);
			row.put("sch_end", sched.end.getTime() / 1000);
		}
		row.put("sch_refresh_interval", sched.refresh_interval);
		row.put("sch_title", sched.title);
		row.put("sch_metadata", sched.metadata);
		row.put("sch_timezone", sched.timezone);

		if (q.moveToNext()) {
			db.update("schedule", row, "sch_id = ?", new String[]{q.getString(0)});
		} else {
			db.insert("schedule", null, row);
		}
		q.close();
	}

	private enum SeedSource {
		BUILT_IN,		/* Embedded copy. */
		CACHED,			/* Cached copy, or refetch if missing. */
		ONLINE,			/* Poll for an update. */
	}

	// Auto updated though Github webhooks.
	private final String PROD_SEED_URL = "https://ggt.gaa.st/menu.json";
	// Old location, manually updated, sometimes useful during development.
	private final String DEBUG_SEED_URL = "https://wilmer.gaa.st/deoxide/menu.json";

	private String getSeedUrl() {
		return BuildConfig.DEBUG ? DEBUG_SEED_URL : PROD_SEED_URL;
	}

	public static final long SEED_FETCH_INTERVAL = 86400 * 1000; /* Once a day. */
	
	private Seed loadSeed(SeedSource source) {
		String json = "";
		JSONObject jso;
		Fetcher f = null;
		try {
			if (source == SeedSource.BUILT_IN) {
				InputStreamReader inr = new InputStreamReader(app.getResources().openRawResource(R.raw.menu), "UTF-8");
				StringWriter sw = new StringWriter();
				IOUtils.copy(inr, sw);
				inr.close();
				json = sw.toString();
			} else {
				f = app.fetch(getSeedUrl(),
				              source == SeedSource.ONLINE ? Fetcher.Source.ONLINE : Fetcher.Source.CACHE);
				json = f.slurp();
			}
		} catch (IOException e) {
			Log.e("DeoxideDb.loadSeed", "IO Error");
			e.printStackTrace();
			return null;
		}
		try {
			jso = new JSONObject(json);
			Seed ret = new Seed(jso);
			if (f != null)
				f.keep();
			Log.d("DeoxideDb.loadSeed", "Fetched seed version " + ret.version + " from " + source.toString());
			return ret;
		} catch (JSONException e) {
			Log.e("DeoxideDb.loadSeed", "Parse Error");
			e.printStackTrace();
			if (f != null)
				f.cancel();
			return null;
		}
	}
	
	/**
	 * Instead of using gson, I'm using this little class to contain all the JSON logic.
	 * Feed it a JSONObject and if it's well-formed, it'll contain all the menu seed info
	 * I need. */
	private static class Seed {
		int version;
		LinkedList<Seed.Schedule> schedules;
		
		public Seed(JSONObject jso) throws JSONException {
			version = jso.getInt("version");
			
			schedules = new LinkedList<Seed.Schedule>();
			JSONArray scheds = jso.getJSONArray("schedules");
			int i;
			for (i = 0; i < scheds.length(); i ++) {
				JSONObject sched = scheds.getJSONObject(i);
				schedules.add(new Schedule(sched));
			}
		}
		
		private static class Schedule {
			String url, title;
			int refresh_interval;
			Date start, end;
			String timezone;
			// Raw JSON string, because we'll only start interpreting this data later on. Will contain
			// info like extra links to for example room maps, and other stuff I may think of. Would
			// be even nicer if (some of) this could become part of the Pentabarf spec..
			String metadata;
			
			public Schedule(JSONObject jso) throws JSONException {
				url = jso.getString("url");
				title = jso.getString("title");
				refresh_interval = jso.optInt("refresh_interval", 86400);

				if (jso.has("metadata")) {
					metadata = jso.getJSONObject("metadata").toString();
				} else {
					metadata = "";
				}
				
				SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
				try {
					start = df.parse(jso.getString("start"));
					end = df.parse(jso.getString("end"));
				} catch (ParseException e) {
					Log.e("DeoxideDb.Seed.Schedule", "Corrupted start/end date.");
					start = end = new Date();
				}

				timezone = jso.optString("timezone", "");
			}

			public String toString() {
				return "SCHEDULE(url=" + url + ", title=" + title + ")";
			}
		}
	}
	
	public class Connection {
		private Schedule sched;

		private IdMap sciIdMap = new IdMap();
		private long schId;
		
		private int day;
		private String metadata;
		
		public void setSchedule(Schedule sched_, String url, boolean fresh) {
			ContentValues row;
			Cursor q;
			
			sched = sched_;

			row = new ContentValues();
			row.put("sch_atime", new Date().getTime() / 1000);
			row.put("sch_start", sched.getFirstTime().getTime() / 1000);
			row.put("sch_end", sched.getLastTime().getTime() / 1000);
			if (fresh)
				row.put("sch_rtime", new Date().getTime() / 1000);

			SQLiteDatabase db = dbh.getWritableDatabase();
			q = db.rawQuery("Select sch_id, sch_day, sch_metadata From schedule Where sch_url = ?",
			                new String[]{sched.getUrl()});

			if (q.moveToNext()) {
				/* Pick up additional data from the database. TODO: Pick up title (can't feed it back yet.) */
				schId = q.getLong(0);
				day = (int) q.getLong(1);
				metadata = q.getString(2);

				db.update("schedule", row, "sch_id = ?", new String[]{"" + schId});
			} else if (q.getCount() == 0) {
				// Set defaults, and only use schedule file's title since we didn't already have one.
				row.put("sch_day", 0);
				row.put("sch_title", sched.getTitle());
				row.put("sch_url", url);
				schId = db.insert("schedule", null, row);
				Log.i("DeoxideDb", "Added schedule to database");
			}
			Log.i("DeoxideDb", "schedId: " + schId);
			q.close();
			
			q = db.rawQuery("Select sci_id, sci_id_s, sci_remind, sci_hidden, sci_stars " +
			                "From schedule_item Where sci_sch_id = ?",
			                new String[]{"" + schId});
			while (q.moveToNext()) {
				String id = q.getString(1);
				Schedule.Item item = sched.getItem(id);
				if (item == null) {
					String cId = sched.getCId(id);
					if (cId != null) {
						Log.i("DeoxideDb", "Db has info for stale id=" + id + ", replacing with cId=" + cId);
						sciIdMap.put(cId, (long) q.getInt(0));
						item = sched.getItem(cId);
						if (item == null) {
							Log.e("DeoxideDb", "... except even that ID seems to be missing? That's probably a Giggity bug :(");
							continue;
						}
					} else {
						/* ZOMGWTF D: */
						Log.e("DeoxideDb", "Db has info about missing schedule item id=" +
								                   id + " remind=" + q.getInt(2) + " stars=" + q.getInt(4) + " hidden=" + q.getInt(3));
						continue;
					}
				}

				item.setRemind(q.getInt(2) != 0);
				item.setHidden(q.getInt(3) != 0);
				sciIdMap.put(id, (long) q.getInt(0));
			}
			q.close();
		}
		
		public void saveScheduleItem(Schedule.Item item) {
			ContentValues row = new ContentValues();
			row.put("sci_remind", item.getRemind());
			row.put("sci_hidden", item.isHidden());

			Log.d("DeoxideDb", "Saving item " + item.getTitle() + " remind " + row.getAsString("sci_remind") +
			                   " hidden " + row.getAsString("sci_hidden"));

			SQLiteDatabase db = dbh.getWritableDatabase();
			Long sciId = sciIdMap.get(item.getId());
			db.update("schedule_item", row, "sci_id = " + sciId, null);
		}

		public ArrayList<DbSchedule> getScheduleList() {
			ArrayList<DbSchedule> ret = new ArrayList<DbSchedule>();
			Cursor q;

			SQLiteDatabase db = dbh.getReadableDatabase();
			q = db.rawQuery("Select * From schedule Order By sch_atime == sch_start, sch_atime Desc", null);
			while (q.moveToNext()) {
				ret.add(new DbSchedule(q));
			}
			q.close();

			return ret;
		}

		public DbSchedule getSchedule(String url) {
			DbSchedule ret = null;
			Cursor q;

			SQLiteDatabase db = dbh.getReadableDatabase();
			q = db.rawQuery("Select * From schedule Where sch_url = ?", new String[]{url});
			if (q.moveToNext()) {
				ret = new DbSchedule(q);
			}
			q.close();
			return ret;
		}

		public boolean refreshScheduleList() {
			return updateData(dbh.getWritableDatabase(), true);
		}

		public String refreshSingleSchedule(byte[] blob) {
			String jsons;
			try {
				jsons = new String(blob, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Log.d("Db.refreshSingle", "Not Unicode? " + e.toString());
				jsons = null;
			}
			if (jsons == null || !jsons.matches("(?s)^\\s*\\{.*")) {
				ByteArrayInputStream stream = new ByteArrayInputStream(blob);
				try {
					Log.d("Db.refreshSingle", "Trying gunzip " + blob.length + " bytes");
					GZIPInputStream gz = new GZIPInputStream(stream);
					ByteArrayOutputStream plain = new ByteArrayOutputStream();
					IOUtils.copy(gz, plain);
					return refreshSingleSchedule(plain.toByteArray());
				} catch (IOException e) {
					Log.d("gunzip", e.toString());
					return null;
				}
			}
			Log.d("Db.refreshSingle", "Found something that looks like json");
			Seed.Schedule parsed;
			try {
				JSONObject obj = new JSONObject(jsons);
				Log.d("Db.refreshSingle", "Found something that parsed like json");
				parsed = new Seed.Schedule(obj);
				if (parsed.url == null) {
					Log.d("Db.refreshSingle", "Object didn't even contain a URL?");
					return null;
				}
			} catch (JSONException e) {
				return null;
			}
			Log.d("Db.refreshSingle", "Found something that parsed like my json: " + parsed);
			removeSchedule(parsed.url);
			app.flushSchedule(parsed.url);
			updateSchedule(dbh.getWritableDatabase(), parsed);
			return parsed.url;
		}
		
		public int getDay() {
			return day;
		}
		
		public void setDay(int day_) {
			day = day_;
			ContentValues row;

			if (day >= 0) {
				SQLiteDatabase db = dbh.getWritableDatabase();
				row = new ContentValues();
				row.put("sch_day", day);
				db.update("schedule", row, "sch_id = ?", new String[]{"" + schId});
			}
		}

		public String getMetadata() {
			return metadata;
		}

		public void removeSchedule(String url) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			Cursor q = db.rawQuery("Select sch_id From schedule Where sch_url = ?", new String[]{url});
			while (q.moveToNext()) {
				db.delete("schedule", "sch_id = ?", new String[]{"" + q.getInt(0)});
				db.delete("schedule_item", "sci_sch_id = ?", new String[]{"" + q.getInt(0)});
			}
			q.close();
		}

		public void resetIndex(Collection<Schedule.Item> items) {
			SQLiteDatabase db = dbh.getReadableDatabase();
			Cursor q = db.rawQuery("Select sch_id from schedule Where sch_id = " + schId +
			                       " And (sch_itime <= sch_rtime Or sch_itime Is Null)",
			null, null);
			if (q.getCount() == 0) {
				q.close();
				return;
			}
			q.close();

			db = dbh.getWritableDatabase();
			// schId needs to be passed as an int. Even though docs sound like everything's a string
			// in FTS tables, this one's most definitely not and if you try to select for it as one
			// you'll delete nothing and end up with lots of duplicate results.
			db.delete("item_search", "sch_id = " + schId, null);
			ContentValues row = new ContentValues();
			for (Schedule.Item item : items) {
				row.clear();
				row.put("sch_id", schId);
				row.put("sci_id_s", item.getId());
				row.put("title", item.getTitle());
				row.put("subtitle", item.getSubtitle());
				row.put("description", item.getDescriptionStripped());
				if (item.getSpeakers() != null) {
					row.put("speakers", TextUtils.join(" ", item.getSpeakers()));
				}
				if (item.getTrack() != null) {
					row.put("track", item.getTrack().getTitle());
				}
				db.insert("item_search", null, row);
			}

			row.clear();
			row.put("sch_itime", new Date().getTime() / 1000);
			db.update("schedule", row, "sch_id = " + schId, null);
		}

		public Collection<String> searchItems(String query) {
			final HashMap<String, Double> rank = new HashMap<>();
			TreeSet<String> res = new TreeSet<>(new Comparator<String>() {
				@Override
				public int compare(String s, String t1) {
					int byRank = -rank.get(s).compareTo(rank.get(t1));
					if (byRank != 0) {
						return byRank;
					} else {
						return s.compareTo(t1);
					}
				}
			});
			SQLiteDatabase db = dbh.getReadableDatabase();
			try {
				Cursor q = db.rawQuery("Select item_search.sci_id_s, matchinfo(item_search, \"pcnalx\"), sci_remind, sci_hidden " +
				                       " From item_search Left Join schedule_item On (sci_sch_id = sch_id" +
				                       " And item_search.sci_id_s = schedule_item.sci_id_s) Where sch_id = " + schId +
				                       " And item_search Match ?", new String[]{query});
				while (q.moveToNext()) {
					// columns: 2=title, subtitle, description, speakers, track
					Integer[] mi = toIntArray(q.getBlob(1));
					double score = 8 * OkapiBM25Score(mi, 2) +
					               4 * OkapiBM25Score(mi, 3) +
					               1 * OkapiBM25Score(mi, 4) +
					               4 * OkapiBM25Score(mi, 5) +
					               2 * OkapiBM25Score(mi, 6);
					if (q.getInt(2) > 0) {
						// Bump starred events up to the top.
						score += 1000;
					} else if (q.getInt(3) > 0) {
						// And deleted items to the bottom (if they're even going to be shown).
						score -= 1000;
					}
//					Log.d("search", q.getString(0) + " score: " + score + " remind " + q.getInt(2));
					rank.put(q.getString(0), score);
					res.add(q.getString(0));
				}
				q.close();
			} catch (SQLiteException e) {
				return null;
			}
			return res;
		}

		private void flushHidden(int id) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			db.execSQL("Update schedule_item Set sci_hidden = 0 Where sci_sch_id = ?", new String[] {"" + id});
		}

		private class IdMap extends HashMap<String,Long> {
			@Override
			public Long get(Object key_) {
				String key = (String) key_;  // @Override wasn't accepted with type String directly
				Long sciId;
				if ((sciId = super.get(key)) != null) {
					return sciId;
				} else {
					SQLiteDatabase db = dbh.getWritableDatabase();
					Cursor q = db.rawQuery("Select sci_id From schedule_item" +
					                       " Where sci_sch_id = " + schId + " And sci_id_s = ?",
					                       new String[]{key});
					if (q.moveToNext()) {
						// This was a bug and maybe still is. Guess I'll log it at least. Normally
						// id's should either have been here when we loaded the schedule, or been
						// added to in-mem map in the else below.
						Log.w("Db.IdMap", "Shouldn't have happened: id " + key + " appeared in table behind my back?");
						sciId = q.getLong(0);
					} else {
						ContentValues row = new ContentValues();
						row.put("sci_sch_id", schId);
						row.put("sci_id_s", key);
						super.put(key, sciId = db.insert("schedule_item", null, row));
					}
					q.close();
					return sciId;
				}
			}
		}

		public void addSearchQuery(String query) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			ContentValues row = new ContentValues();
			row.put("hst_query", query);
			row.put("hst_atime", new Date().getTime() / 1000);
			if (db.update("search_history", row, "hst_query = ?", new String[]{query}) == 0) {
				db.insert("search_history", null, row);
				Log.d("addSearchQuery", query + " added");
			} else {
				Log.d("addSearchQuery", query + " updated");
			}
		}

		public AbstractList<String> getSearchHistory() {
			ArrayList<String> ret = new ArrayList<>();
			SQLiteDatabase db = dbh.getReadableDatabase();
			Cursor q = db.rawQuery("Select hst_query From search_history Order By hst_atime Desc", null);
			while (q.moveToNext()) {
				ret.add(q.getString(0));
			}
			q.close();
			return ret;
		}

		public void forgetSearchQuery(String query) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			Log.d("forgetSearchQuery", query + " " + db.delete("search_history", "hst_query = ?", new String[]{query}));
		}
	}

	// Not mine, this was originally Kotlin code from I think https://medium.com/android-news/offline-full-text-search-in-android-ios-b4dd5bed3acd
	// and decompiled back into Java by me.
	private static double OkapiBM25Score(Integer[] matchinfo, int column) {
		double b = 0.75;
		double k1 = 1.2;
		int pOffset = 0;
		int cOffset = 1;
		int nOffset = 2;
		int aOffset = 3;
		int termCount = matchinfo[pOffset];
		int colCount = matchinfo[cOffset];
		int lOffset = aOffset + colCount;
		int xOffset = lOffset + colCount;
		double totalDocs = (double)matchinfo[nOffset];
		double avgLength = (double)matchinfo[aOffset + column];
		double docLength = (double)matchinfo[lOffset + column];
		double score = 0.0;

		for(int i = 0; i < termCount; ++i) {
			int currentX = xOffset + 3 * (column + i * colCount);
			double termFrequency = (double)matchinfo[currentX];
			double docsWithTerm = (double)matchinfo[currentX + 2];
			double p = totalDocs - docsWithTerm + 0.5;
			double q = docsWithTerm + 0.5;
			double idf = log(p) / log(q);
			double r = termFrequency * (k1 + (double)1);
			double s = b * (docLength / avgLength);
			double t = termFrequency + k1 * ((double)1 - b + s);
			double rightSide = r / t;
			score += idf * rightSide;
		}

		return score;
	}

	static private Integer[] toIntArray(byte[] blob) {
		IntBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder()).asIntBuffer();
		Integer[] ret = new Integer[buf.capacity()];
		int i = 0;
		while (buf.hasRemaining()) {
			ret[i++] = buf.get();
		}
		return ret;
	}

	public class DbSchedule {
		private int id;
		private String url, title;
		private Date start, end;
		private String timezone;
		private int refresh_interval;  // Number of seconds before checking server for new schedule info.
		private Date atime;  // Access time, set by setSchedule above, used as sorting key in Chooser.
		private Date rtime;  // Refresh time, last time Fetcher claimed the server sent new data.
		private Date itime;  // Index time, last time it was added to the FTS index.

		public DbSchedule(Cursor q) {
			id = q.getInt(q.getColumnIndexOrThrow("sch_id"));
			url = q.getString(q.getColumnIndexOrThrow("sch_url"));
			title = q.getString(q.getColumnIndexOrThrow("sch_title"));
			start = new Date(q.getLong(q.getColumnIndexOrThrow("sch_start")) * 1000);
			refresh_interval = q.getInt(q.getColumnIndexOrThrow("sch_refresh_interval"));
			end = new Date(q.getLong(q.getColumnIndexOrThrow("sch_end")) * 1000);
			timezone = q.getString(q.getColumnIndexOrThrow("sch_timezone"));
			atime = new Date(q.getLong(q.getColumnIndexOrThrow("sch_atime")) * 1000);
			rtime = new Date(q.getLong(q.getColumnIndexOrThrow("sch_rtime")) * 1000);
			itime = new Date(q.getLong(q.getColumnIndexOrThrow("sch_itime")) * 1000);
		}
		
		public String getUrl() {
			return url;
		}
		
		public String getTitle() {
			if (title != null)
				return title;
			else
				return url;
		}
		
		public Date getStart() {
			return start;
		}
		
		public Date getEnd() {
			return end;
		}

		public String getTimezone() {
			return timezone;
		}

		public Date getAtime() {
			return atime;
		}

		public boolean refreshNow() {
			// TODO: Stop this and all other uses of the decrepit Date API.
			Date now = new Date();
			int interval = (refresh_interval > 0) ? refresh_interval : 86400;
			return now.getTime() > (rtime.getTime() + interval * 1000);
		}

		public void flushHidden() {
			Connection db = getConnection();
			db.flushHidden(id);
		}
	}
}
