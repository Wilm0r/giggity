package net.gaast.deoxide;

import android.app.Application;

public class Deoxide extends Application {
	public static final int HourHeight = 16;
	public static final int HourWidth = 72;
	public static final int TentHeight = 48;
	public static final int TentWidth = 72;
	
	DeoxideDb db;
	DeoxideDb.Connection dbc;
	
    public void onCreate() {
    	super.onCreate();
    	db = new DeoxideDb(this);
    }
    
    public DeoxideDb.Connection getDb() {
    	return db.getConnection();
    }
}