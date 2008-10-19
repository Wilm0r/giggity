package net.gaast.deoxide;

import java.util.Date;

public class ScheduleData {
	public ScheduleData() {
		// foo - read stuff from a file when I figure out XML.
	}
	
	public String[] getTents() {
		String tents[] = { "ALPHA", "BRAVO", "CHARLIE", "DOMMELSCH", "ECHO", "FOXTROT", "GOLF" };
		
		return tents;
	}
	
	public ScheduleDataItem[] getTentSchedule(String tent) {
		if (tent == "BRAVO") {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem(1, "Killswitch Engage", new Date(2008, 8, 15, 14, 20), new Date(2008, 8, 15, 15, 20)),
					new ScheduleDataItem(2, "The National", new Date(2008, 8, 15, 16, 0), new Date(2008, 8, 15, 17, 0)),
					new ScheduleDataItem(3, "Amy MacDonald", new Date(2008, 8, 15, 17, 40), new Date(2008, 8, 15, 18, 40)),
					new ScheduleDataItem(4, "HIM", new Date(2008, 8, 15, 19, 30), new Date(2008, 8, 15, 20, 30)),
					new ScheduleDataItem(5, "The Flaming Lips", new Date(2008, 8, 15, 21, 15), new Date(2008, 8, 15, 22, 15))
			};
			return ret;
		} else {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem(6, "The Pigeon Detectives", new Date(2008, 8, 15, 14, 0), new Date(2008, 8, 15, 14, 45)),
					new ScheduleDataItem(7, "The Presidents of the United States", new Date(2008, 8, 15, 15, 15), new Date(2008, 8, 15, 16, 10)),
					new ScheduleDataItem(8, "The Wombats", new Date(2008, 8, 15, 16, 50), new Date(2008, 8, 15, 17, 45)),
					new ScheduleDataItem(9, "Dropkick Murphys", new Date(2008, 8, 15, 18, 30), new Date(2008, 8, 15, 19, 30)),
					new ScheduleDataItem(10, "The Kooks", new Date(2008, 8, 15, 20, 15), new Date(2008, 8, 15, 22, 15)),
					new ScheduleDataItem(11, "Anouk", new Date(2008, 8, 15, 22, 0), new Date(2008, 8, 15, 23, 0)),
			}
		}
	}
}
