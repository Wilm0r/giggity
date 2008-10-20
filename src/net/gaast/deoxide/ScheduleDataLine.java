package net.gaast.deoxide;

import java.util.LinkedList;

public class ScheduleDataLine {
	private String id;
	private String title;
	private LinkedList<ScheduleDataItem> items;
	
	public ScheduleDataLine(String id_, String title_) {
		id = id_;
		title = title_;
		items = new LinkedList<ScheduleDataItem>();
	}
	
	public String getId() {
		return id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public void addItem(ScheduleDataItem item) {
		items.add(item);
	}
	
	public LinkedList<ScheduleDataItem> getItems() {
		return items;
	}
}
