package net.gaast.deoxide;

import android.app.Application;
import android.preference.PreferenceManager;

public class Deoxide extends Application {
	DeoxideDb db;
	DeoxideDb.Connection dbc;
	
    public void onCreate() {
    	super.onCreate();
    	db = new DeoxideDb(this);
    	
    	PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
    }
    
    public DeoxideDb.Connection getDb() {
    	return db.getConnection();
    }
}