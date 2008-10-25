package net.gaast.deoxide;

public class ScheduleDataItemLink {
	ScheduleDataLinkType type;
	String url;
	
	public ScheduleDataItemLink(ScheduleDataLinkType type_, String url_) {
		type = type_;
		url = url_;
	}
	
	public ScheduleDataLinkType getType() {
		return type;
	}
	
	public String getUrl() {
		return url;
	}
}
