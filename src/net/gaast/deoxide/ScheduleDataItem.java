package net.gaast.deoxide;

import java.util.Date;
import java.util.LinkedList;

public class ScheduleDataItem {
	private String id;
	private String title;
	private String description;
	// private boolean remind;
	private Date startTime, endTime;
	private LinkedList<ScheduleDataItemLink> links;
	
	ScheduleDataItem(String id_, String title_, Date startTime_, Date endTime_) {
		id = id_;
		title = title_;
		startTime = startTime_;
		endTime = endTime_;
	}
	
	public void setDescription(String description_) {
		description = description_;
	}
	
	public void addLink(ScheduleDataLinkType type, String url) {
		ScheduleDataItemLink link = new ScheduleDataItemLink(type, url);
		
		if (links == null) {
			links = new LinkedList<ScheduleDataItemLink>();
		}
		links.add(link);
	}
	
	public String getId() {
		return id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public Date getStartTime() {
		return startTime;
	}
	
	public Date getEndTime() {
		return endTime;
	}
	
	public String getDescription() {
		return description;
	}
	
	public LinkedList<ScheduleDataItemLink> getLinks() {
		return links;
	}
}
