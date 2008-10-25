package net.gaast.deoxide;

public class ScheduleDataLinkType {
	String id;
	String iconUrl;
	String iconLocal;
	
	public ScheduleDataLinkType(String id_) {
		id = id_;
	}
	
	public void setIconUrl(String url_) {
		iconUrl = url_;
	}
	
	public String getIconUrl() {
		return iconUrl;
	}
}
