package net.gaast.deoxide;

import java.util.HashMap;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class DeoxideDb {
	private SQLiteDatabase db;
	private ScheduleData sched;

	//private HashMap<String,Integer> ids2int;
	long schedId;
	
	public DeoxideDb(SQLiteDatabase db_) {
		db = db_;
	}
	
	public void setSchedule(ScheduleData sched_) {
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
