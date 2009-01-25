package net.gaast.deoxide;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

public class DeoxideDb {
	Helper dbh;
	
	public DeoxideDb(Application app_) {
		dbh = new Helper(app_, "deoxide0", null, 3);
	}
	
	public Connection getConnection() {
		Log.i("DeoxideDb", "Created database connection");
		return new Connection();
	}
	
	public class Helper extends SQLiteOpenHelper {
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
					                          "sch_id_s VarChar(128))");
			db.execSQL("Create Table schedule_item (sci_id Integer Primary Key AutoIncrement Not Null, " +
					                               "sci_sch_id Integer Not Null, " +
					                               "sci_id_s VarChar(128), " +
					                               "sci_remind Boolean, " +
					                               "sci_stars Integer(2) Null)");
		}
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.i("DeoxideDb", "Upgrading from database version " + oldVersion + " to " + newVersion);

			while (oldVersion < newVersion) {
				switch (oldVersion) {
				case 1:
					db.execSQL("Alter Table schedule Add Column sch_url VarChar(256)");
					db.execSQL("Alter Table schedule Add Column sch_atime Integer");
					break;
				case 2:
					db.execSQL("Alter Table schedule Add Column sch_title VarChar(128)");
					break;
				}
				oldVersion++;
			}
		}
	}
	
	public class Connection {
		private SQLiteDatabase db;
		private Schedule sched;

		private HashMap<String,Long> sciIdMap;
		private long schId;
		
		public Connection() {
			db = dbh.getWritableDatabase();
		}
		
		protected void finalize() {
			db.close();
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
			
			q = db.rawQuery("Select sch_id From schedule Where sch_id_s = ?",
					        new String[]{sched.getId()});
			
			if (q.getCount() == 0) {
				schId = db.insert("schedule", null, row);
				Log.i("DeoxideDb", "Adding schedule to database");
			} else if (q.getCount() == 1) {
				q.moveToNext();
				schId = q.getLong(0);
				
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
				item.setRemind(q.getInt(2) != 0);
				sciIdMap.put(q.getString(1), new Long(q.getInt(0)));
			}
			q.close();
		}
		
		public void saveScheduleItem(Schedule.Item item) {
			ContentValues row = new ContentValues();
			Long sciId;
			
			row.put("sci_sch_id", schId);
			row.put("sci_id_s", item.getId());
			row.put("sci_remind", item.getRemind());
			
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
			
			return ret;
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
