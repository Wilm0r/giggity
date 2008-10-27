package net.gaast.deoxide;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

public class DeoxideDbHelper extends SQLiteOpenHelper {

	public DeoxideDbHelper(Context context, String name, CursorFactory factory,
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
