package net.gaast.deoxide;

import java.io.InputStream;
import java.net.URL;

import android.graphics.drawable.Drawable;
import android.util.Log;

public class ScheduleDataLinkType {
	private String id;
	private Drawable iconDrawable;
	
	public ScheduleDataLinkType(String id_) {
		id = id_;
	}
	
	public void setIconUrl(String url_) {
		try {
			URL dl = new URL(url_);
			InputStream in = dl.openStream();
			iconDrawable = Drawable.createFromStream(in, id);
		} catch (Exception e) {
			Log.e("setIconUrl", "Error while dowloading icon " + url_);
			e.printStackTrace();
		}
	}
	
	public Drawable getIcon() {
		return iconDrawable;
	}
}
