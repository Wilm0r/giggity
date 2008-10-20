package net.gaast.deoxide;

import java.util.Date;

public class ScheduleDataItem {
	private String id;
	private String title;
	private String description;
	// private boolean remind;
	private Date startTime, endTime;
	
	ScheduleDataItem(String id_, String title_, Date startTime_, Date endTime_) {
		id = id_;
		title = title_;
		startTime = startTime_;
		endTime = endTime_;
	}
	
	public String getId() {
		return id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public void setDescription(String description_) {
		description = description_;
	}
	
	public String getDescription() {
		return description;
	}
	
	public Date getStartTime() {
		return startTime;
	}
	
	public Date getEndTime() {
		return endTime;
	}
}
