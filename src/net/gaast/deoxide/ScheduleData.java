package net.gaast.deoxide;

import java.util.Date;

public class ScheduleData {
	ScheduleDataItem allitems[];
	
	public ScheduleData() {
		allitems = new ScheduleDataItem[100];
		// foo - read stuff from a file when I figure out XML.
	}
	
	public String[] getTents() {
		String tents[] = { "ALPHA", "BRAVO", "CHARLIE", "DOMMELSCH", "ECHO", "FOXTROT", "GOLF" };
		
		return tents;
	}
	
	public ScheduleDataItem[] getTentSchedule(String tent) {
		int i;
		if (tent == "BRAVO" || tent == "ECHO") {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem(1, "Killswitch Engage", new Date(108, 7, 15, 14, 20), new Date(108, 7, 15, 15, 20)),
					new ScheduleDataItem(2, "The National", new Date(108, 7, 15, 16, 0), new Date(108, 7, 15, 17, 0)),
					new ScheduleDataItem(3, "Amy MacDonald", new Date(108, 7, 15, 17, 40), new Date(108, 7, 15, 18, 40)),
					new ScheduleDataItem(4, "HIM", new Date(108, 7, 15, 19, 30), new Date(108, 7, 15, 20, 30)),
					new ScheduleDataItem(5, "The Flaming Lips", new Date(108, 7, 15, 21, 15), new Date(108, 7, 15, 22, 15))
			};
			for (i = 0; i < ret.length; i ++) {
				allitems[ret[i].getId()] = ret[i]; 
			}
			return ret;
		} else if (tent == "CHARLIE" || tent == "FOXTROT") {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem(12, "The Girls", new Date(108, 7, 15, 14, 45), new Date(108, 7, 15, 15, 15)),
					new ScheduleDataItem(13, "Hadouken!", new Date(108, 7, 15, 16, 10), new Date(108, 7, 15, 16, 45)),
					new ScheduleDataItem(14, "Holy Fuck", new Date(108, 7, 15, 17, 45), new Date(108, 7, 15, 18, 30)),
					new ScheduleDataItem(15, "Triggerfinger", new Date(108, 7, 15, 19, 30), new Date(108, 7, 15, 20, 15)),
					new ScheduleDataItem(16, "Late of the Pier", new Date(108, 7, 15, 21, 15), new Date(108, 7, 15, 22, 0))
			};
			for (i = 0; i < ret.length; i ++) {
				ret[i].setDescription("Porcupine Tree is a british rock band formed in Hemel Hempstead, Hertfordshire, England in 1987. During the course of the band’s history, they have at times incorporated psychedelic rock, alternative, ambient, techno, and, most recently, metal and post-rock into their unique style of progressive rock.");
				allitems[ret[i].getId()] = ret[i]; 
			}
			return ret;
		} else {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem(6, "The Pigeon Detectives", new Date(108, 7, 15, 14, 0), new Date(108, 7, 15, 14, 45)),
					new ScheduleDataItem(7, "The Presidents of the United States", new Date(108, 7, 15, 15, 15), new Date(108, 7, 15, 16, 10)),
					new ScheduleDataItem(8, "The Wombats", new Date(108, 7, 15, 16, 50), new Date(108, 7, 15, 17, 45)),
					new ScheduleDataItem(9, "Dropkick Murphys", new Date(108, 7, 15, 18, 30), new Date(108, 7, 15, 19, 30)),
					new ScheduleDataItem(10, "The Kooks", new Date(108, 7, 15, 20, 15), new Date(108, 7, 15, 22, 15)),
					new ScheduleDataItem(11, "Anouk", new Date(108, 7, 15, 22, 0), new Date(108, 7, 15, 23, 0))
			};
			for (i = 0; i < ret.length; i ++) {
				ret[i].setDescription("Aphex Twin, born Richard David James, August 18, 1971, in Limerick, Ireland to Welsh parents Lorna and Derek James, is an electronic music artist. He grew up in Cornwall, United Kingdom and started producing music around the age of 12.");
				allitems[ret[i].getId()] = ret[i]; 
			}
			return ret;
		}
	}
	
	public ScheduleDataItem getItem(int id) {
		return allitems[id];
	}
}
