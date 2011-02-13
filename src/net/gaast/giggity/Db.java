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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.TextView;

public class Db {
	Helper dbh;
	
	public Db(Application app_) {
		dbh = new Helper(app_, "giggity", null, 1);
	}
	
	public Connection getConnection() {
		Log.i("DeoxideDb", "Created database connection");
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
					                          "sch_id_s VarChar(128)," +
					                          "sch_day Integer)");
			db.execSQL("Create Table schedule_item (sci_id Integer Primary Key AutoIncrement Not Null, " +
					                               "sci_sch_id Integer Not Null, " +
					                               "sci_id_s VarChar(128), " +
					                               "sci_remind Boolean, " +
					                               "sci_stars Integer(2) Null)");
			
			/* Seed it with FOSDEM 2011. At some point I should make this more
			 * fancy, but no time now. :-/ */
			ContentValues row = new ContentValues();
			row.put("sch_id_s", "e036bee3470d06c83601446025102bd6a58d6d99");
			row.put("sch_title", "FOSDEM 2011");
			row.put("sch_url", "http://fosdem.org/2011/schedule/xml");
			row.put("sch_atime", new Date().getTime() / 1000);
			db.insert("schedule", null, row);
		}
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.i("DeoxideDb", "Upgrading from database version " + oldVersion + " to " + newVersion);

			switch (oldVersion) {
			case 1:
			/*
				db.execSQL("Alter Table schedule Add Column sch_url VarChar(256)");
				db.execSQL("Alter Table schedule Add Column sch_atime Integer");
			case 2:
				db.execSQL("Alter Table schedule Add Column sch_title VarChar(128)");
			case 5:
				db.execSQL("Alter Table schedule Add Column sch_day Integer");
			*/
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
		
		public void setSchedule(Schedule sched_, String url) {
			ContentValues row;
			Cursor q;
			
			sched = sched_;
			sciIdMap = new HashMap<String,Long>();

			row = new ContentValues();
			row.put("sch_id_s", sched.getId());
			row.put("sch_title", sched.getTitle());
			row.put("sch_url", url);
			row.put("sch_atime", new Date().getTime() / 1000);
			
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
			
			q = db.rawQuery("Select sch_url, sch_id_s, sch_title From schedule Order By sch_atime Desc", null);
			while (q.moveToNext()) {
				ret.add(new DbSchedule(q.getString(0), q.getString(1), q.getString(2)));
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
		
		public DbSchedule(String url_, String id_, String title_) {
			url = url_;
			id = id_;
			title = title_;
		}
		
		public String getUrl() {
			return url;
		}
		
		public String getId() {
			return id;
		}
		
		public String getTitle() {
			return title;
		}
	}
}
