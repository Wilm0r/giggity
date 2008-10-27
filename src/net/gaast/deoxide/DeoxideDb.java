package net.gaast.deoxide;

import android.database.sqlite.SQLiteDatabase;

public class DeoxideDb {
	SQLiteDatabase db;
	
	public DeoxideDb(SQLiteDatabase db_) {
		db = db_;
	}
}
