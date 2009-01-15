package net.gaast.deoxide;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

public class DeoxideDb {
	Application app;
	Helper dbh;
	
	public DeoxideDb(Application app_) {
		dbh = new Helper(app_, "deoxide0", null, 1);
		app = app_;
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
			db.execSQL("Create Table schedule (sch_id Integer Primary Key AutoIncrement Not Null," +
					                          "sch_id_s VarChar(128))");
			db.execSQL("Create Table schedule_item (sci_id Integer Primary Key AutoIncrement Not Null," +
					                               "sci_sch_id Integer Not Null," +
					                               "sci_id_s VarChar(128)," +
					                               "sci_remind Boolean," +
					                               "sci_stars Integer(2) Null)");
		}
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Auto-generated method stub
	
		}
	}
	
	public class Connection {
		private SQLiteDatabase db;
		private Schedule sched;

		//private HashMap<String,Integer> ids2int;
		long schedId;
		
		public Connection() {
			db = dbh.getWritableDatabase();
		}
		
		public void setSchedule(Schedule sched_) {
			Cursor q;
			
			sched = sched_;
			
			q = db.rawQuery("Select sch_id From schedule Where sch_id_s = ?",
					        new String[]{sched.getId()});
			
			if (q.getCount() == 0) {
				/*
				q = db.rawQuery("Insert Into schedule (sch_id_s) Values (?)",
						    new String[]{sched.getId()});
				TODO(wilmer): Sanitize this code after figuring out WTF
				lastInsertRow() is only available via this kiddie
				interface:
				*/
				
				ContentValues row = new ContentValues();
				
				row.put("sch_id_s", sched.getId());
				schedId = db.insert("schedule", null, row);
			} else if (q.getCount() == 1) {
				q.moveToNext();
				schedId = q.getLong(0);
			} else {
				Log.e("DeoxideDb", "Database corrupted");
			}
			Log.i("DeoxideDb", "schedId: " + schedId);
			q.close();
			
			q = db.rawQuery("Select sci_id, sci_id_s, sci_remind, sci_stars " +
					        "From schedule_item Where sci_sch_id = ?",
					        new String[]{"" + schedId});
			while (q.moveToNext()) {
				Log.d("sci", q.toString());
				//sched.
			}
			q.close();
		}
	}

}
