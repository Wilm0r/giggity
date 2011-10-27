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

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import android.util.Log;

public class Db {
	private Giggity app;
	private Helper dbh;
	private static final int dbVersion = 11;
	private int oldDbVer = dbVersion;
	private SharedPreferences pref;

	public Db(Application app_) {
		app = (Giggity) app_;
		pref = PreferenceManager.getDefaultSharedPreferences(app);
		dbh = new Helper(app_, "giggity", null, dbVersion);
	}
	
	public Connection getConnection() {
		Log.i("DeoxideDb", "Created database connection");
		return new Connection();
	}
	
	private class Helper extends SQLiteOpenHelper {
		public Helper(Context context, String name, CursorFactory factory,
				int version) {
			super(context, name, factory, version);
			
			if (oldDbVer < dbVersion)
				updateData(this.getWritableDatabase(), false);
		}
	
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.i("DeoxideDb", "Creating new database");
			db.execSQL("Create Table schedule (sch_id Integer Primary Key AutoIncrement Not Null, " +
					                          "sch_title VarChar(128), " +
					                          "sch_url VarChar(256), " +
					                          "sch_atime Integer, " +
					                          "sch_rtime Integer, " +
					                          "sch_start Integer, " +
					                          "sch_end Integer, " +
					                          "sch_id_s VarChar(128)," +
					                          "sch_day Integer)");
			db.execSQL("Create Table schedule_item (sci_id Integer Primary Key AutoIncrement Not Null, " +
					                               "sci_sch_id Integer Not Null, " +
					                               "sci_id_s VarChar(128), " +
					                               "sci_remind Boolean, " +
					                               "sci_stars Integer(2) Null)");
		}
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			int v = oldVersion;
			Log.i("DeoxideDb", "Upgrading from database version " + oldVersion + " to " + newVersion);
			while (v < newVersion) {
				v++;
				if (v == 8) {
					/* Version 8 adds start/end time columns to the db. */
					try {
						db.execSQL("Alter Table schedule Add Column sch_start Integer");
						db.execSQL("Alter Table schedule Add Column sch_end Integer");
					} catch (SQLiteException e) {
						Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
						e.printStackTrace();
					}
				} else if (v == 11) {
					/* Version 10 adds rtime column. */
					try {
						db.execSQL("Alter Table schedule Add Column sch_rtime Integer");
					} catch (SQLiteException e) {
						Log.e("DeoxideDb", "SQLite error, maybe column already exists?");
						e.printStackTrace();
					}
				}
			}
			
			oldDbVer = Math.min(oldDbVer, oldVersion);
		}
	}
	
	/* For ease of use, seed the main menu with some known schedules. */
	public void updateData(SQLiteDatabase db, boolean online) {
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
				return;
			}
		}

		int version = pref.getInt("last_menu_seed_version", oldDbVer), newver = version;
		
		if (seed.version <= version && oldDbVer == dbVersion) {
			/* No updates required, both data and structure are up to date. */
			return;
		}
		
		long ts = new Date().getTime() / 1000;
		for (Seed.Schedule sched : seed.schedules) {
			newver = Math.max(newver, sched.version);
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
			if (sched.version > version && q.getCount() == 0) {
				ContentValues row = new ContentValues();
				if (sched.id != null)
					row.put("sch_id_s", sched.id);
				else
					row.put("sch_id_s", Schedule.hashify(sched.url));
				row.put("sch_url", sched.url);
				row.put("sch_title", sched.title);
				row.put("sch_atime", ts--);
				row.put("sch_start", sched.start.getTime() / 1000);
				row.put("sch_end", sched.end.getTime() / 1000);
				db.insert("schedule", null, row);
			} else if(oldDbVer < 8 && q.getCount() == 1) {
				/* We're upgrading from < 8 so we have to backfill the start/end columns. */
				ContentValues row = new ContentValues();
				q.moveToNext();
				row.put("sch_start", sched.start.getTime() / 1000);
				row.put("sch_end", sched.end.getTime() / 1000);
				db.update("schedule", row, "sch_id = ?", new String[]{q.getString(0)});
			}
		}
		
		if (newver != version) {
			Editor p = pref.edit();
			p.putInt("last_menu_seed_version", newver);
			p.commit();
		}
	}

	private enum SeedSource {
		BUILT_IN,		/* Embedded copy. */
		CACHED,			/* Cached copy, or refetch if missing. */
		ONLINE,			/* Poll for an update. */
	}
	private final String SEED_URL = "http://wilmer.gaa.st/deoxide/menu.json";
	private final long SEED_FETCH_INTERVAL = 86400 * 1000; /* Once a day. */
	
	private Seed loadSeed(SeedSource source) {
		String json = "";
		JSONObject jso;
		Fetcher f = null;
		try {
			if (source == SeedSource.BUILT_IN) {
				InputStream inp = app.getResources().openRawResource(R.raw.menu);
				byte[] buf = new byte[1024];
				int n;
				while ((n = inp.read(buf, 0, buf.length)) > 0)
					json += new String(buf, 0, n, "utf-8");
			} else {
				f = app.fetch(SEED_URL, source == SeedSource.ONLINE ?
				                        Fetcher.Source.ONLINE : Fetcher.Source.CACHE_ONLINE);
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
			int version;
			String id, url, title;
			Date start, end;
			
			public Schedule(JSONObject jso) throws JSONException {
				version = jso.getInt("version");
				if (jso.has("id"))
					id = jso.getString("id");
				url = jso.getString("url");
				title = jso.getString("title");
				
				SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
				try {
					start = df.parse(jso.getString("start"));
					end = df.parse(jso.getString("end"));
				} catch (ParseException e) {
					Log.e("DeoxideDb.Seed.Schedule", "Corrupted start/end date.");
					start = end = new Date();
				}
			}
		}
	}
	
	public class Connection {
		private SQLiteDatabase db;
		private Schedule sched;

		private HashMap<String,Long> sciIdMap;
		private long schId;
		
		private int day;
		
		public Connection() {
			resume();
		}
		
		protected void finalize() {
			sleep();
		}
		
		public void sleep() {
			Log.d("DeoxideDb", "sleep()" + db);
			if (db != null) {
				db.close();
				db = null;
			}
		}
		
		public void resume() {
			if (db == null) {
				db = dbh.getWritableDatabase();
				Log.d("DeoxideDb", "open: " + db.isOpen());
			}
			Log.d("DeoxideDb", "resume()" + db);
		}
		
		public void setSchedule(Schedule sched_, String url, boolean fresh) {
			ContentValues row;
			Cursor q;
			
			sched = sched_;
			sciIdMap = new HashMap<String,Long>();

			row = new ContentValues();
			row.put("sch_id_s", sched.getId());
			row.put("sch_title", sched.getTitle());
			row.put("sch_url", url);
			row.put("sch_atime", new Date().getTime() / 1000);
			row.put("sch_start", sched.getFirstTime().getTime() / 1000);
			row.put("sch_end", sched.getLastTime().getTime() / 1000);
			if (fresh)
				row.put("sch_rtime", new Date().getTime() / 1000);
			
			q = db.rawQuery("Select sch_id, sch_day From schedule Where sch_id_s = ?",
					        new String[]{sched.getId()});
			
			if (q.getCount() == 0) {
				row.put("sch_day", 0);
				schId = db.insert("schedule", null, row);
				Log.i("DeoxideDb", "Adding schedule to database");
			} else if (q.getCount() == 1) {
				q.moveToNext();
				schId = q.getLong(0);
				day = (int) q.getLong(1);
				
				db.update("schedule", row, "sch_id = ?", new String[]{"" + schId});
			} else {
				Log.e("DeoxideDb", "Database corrupted");
			}
			Log.i("DeoxideDb", "schedId: " + schId);
			q.close();
			
			q = db.rawQuery("Select sci_id, sci_id_s, sci_remind, sci_stars " +
					        "From schedule_item Where sci_sch_id = ?",
					        new String[]{"" + schId});
			while (q.moveToNext()) {
				Schedule.Item item = sched.getItem(q.getString(1));
				if (item == null) {
					/* ZOMGWTF D: */
					Log.e("DeoxideDb", "Db has info about deleted schedule item " +
					      q.getString(1) + " remind " + q.getInt(2) + " stars " + q.getInt(3));
					continue;
				}

				item.setRemind(q.getInt(2) != 0);
				item.setStars(q.getInt(3));
				sciIdMap.put(q.getString(1), new Long(q.getInt(0)));
				
				Log.d("DeoxideDb", "Item from db " + item.getTitle() + " remind " + q.getInt(2) + " stars " + q.getInt(3));
			}
			q.close();
		}
		
		public void saveScheduleItem(Schedule.Item item) {
			ContentValues row = new ContentValues();
			Long sciId;
			
			row.put("sci_sch_id", schId);
			row.put("sci_id_s", item.getId());
			row.put("sci_remind", item.getRemind());
			row.put("sci_stars", item.getStars());
			
			Log.d("DeoxideDb", "Saving item " + item.getTitle() + " remind " + row.getAsString("sci_remind") + " stars " + row.getAsString("sci_stars"));
			
			if ((sciId = sciIdMap.get(item.getId())) != null) {
				db.update("schedule_item", row,
						  "sci_id = ?", new String[]{"" + sciId.longValue()});
			} else {
				sciIdMap.put(item.getId(),
						     new Long(db.insert("schedule_item", null, row)));
			}
		}
		
		public ArrayList<DbSchedule> getScheduleList() {
			ArrayList<DbSchedule> ret = new ArrayList<DbSchedule>();
			Cursor q;
			long seedAge = System.currentTimeMillis() - pref.getLong("last_menu_seed_ts", 0);
			boolean online = seedAge < 0 || seedAge > SEED_FETCH_INTERVAL;
			
			Log.d("DeoxideDb.getScheduleList", "seedAge " + seedAge + " online " + online);
			
			/* Check if there are updates first. */
			updateData(db, online);
			if (online) {
				
				Editor p = pref.edit();
				p.putLong("last_menu_seed_ts", System.currentTimeMillis());
				p.commit();
			}
			
			q = db.rawQuery("Select * From schedule Order By sch_atime Desc", null);
			while (q.moveToNext()) {
				ret.add(new DbSchedule(q));
			}
			q.close();
			
			return ret;
		}
		
		public int getDay() {
			return day;
		}
		
		public void setDay(int day_) {
			day = day_;
			ContentValues row;
			
			row = new ContentValues();
			row.put("sch_day", day);
			db.update("schedule", row, "sch_id = ?", new String[]{"" + schId});
		}
		
		public void removeSchedule(String url) {
			Cursor q = db.rawQuery("Select sch_id From schedule Where sch_url = ?", new String[]{url});
			while (q.moveToNext()) {
				db.delete("schedule", "sch_id = ?", new String[]{"" + q.getInt(0)});
				db.delete("schedule_item", "sci_sch_id = ?", new String[]{"" + q.getInt(0)});
			}
		}
	}
	
	public class DbSchedule {
		private String url, id, title;
		private Date start, end, atime, rtime;
		
		public DbSchedule(Cursor q) {
			url = q.getString(q.getColumnIndexOrThrow("sch_url"));
			id = q.getString(q.getColumnIndexOrThrow("sch_id_s"));
			title = q.getString(q.getColumnIndexOrThrow("sch_title"));
			start = new Date(q.getLong(q.getColumnIndexOrThrow("sch_start")) * 1000);
			end = new Date(q.getLong(q.getColumnIndexOrThrow("sch_end")) * 1000);
			atime = new Date(q.getLong(q.getColumnIndexOrThrow("sch_atime")) * 1000);
			rtime = new Date(q.getLong(q.getColumnIndexOrThrow("sch_rtime")) * 1000);
		}
		
		public String getUrl() {
			return url;
		}
		
		public String getId() {
			return id;
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
		
		public Date getAtime() {
			return atime;
		}
		
		public Date getRtime() {
			return rtime;
		}
	}
}
