package net.gaast.deoxide;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.util.Log;
import android.util.Xml;

public class ScheduleData implements ContentHandler {
	String id;
	String title;
	
	LinkedList<ScheduleDataLine> tents;
	ScheduleDataLine curTent;
	ScheduleDataItem curItem;
	String curString;
	
	ScheduleDataItem allitems[];
	
	public ScheduleData(String source) {
		Log.i("ScheduleData", "About to start parsging");
		tents = new LinkedList<ScheduleDataLine>();
		try {
			URL dl = new URL(source);
			BufferedReader in = new BufferedReader(new InputStreamReader(dl.openStream()));
			Xml.parse(in, this);
		} catch (Exception e) {
			Log.i("XML", "Foutje bedankt: " + e);
			//hw = "shit happens:" + e;
		}
		// foo - read stuff from a file when I figure out XML.
	}
	
	public String getId() {
		return id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public String[] getTents() {
		String tents[] = { "ALPHA", "BRAVO", "CHARLIE", "DOMMELSCH", "ECHO", "FOXTROT", "GOLF" };
		
		return tents;
	}
	
	public ScheduleDataItem[] getTentSchedule(String tent) {
		int i;
		if (tent == "BRAVO" || tent == "ECHO") {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem("1", "Killswitch Engage", new Date(108, 7, 15, 14, 20), new Date(108, 7, 15, 15, 20)),
					new ScheduleDataItem("2", "The National", new Date(108, 7, 15, 16, 0), new Date(108, 7, 15, 17, 0)),
					new ScheduleDataItem("3", "Amy MacDonald", new Date(108, 7, 15, 17, 40), new Date(108, 7, 15, 18, 40)),
					new ScheduleDataItem("4", "HIM", new Date(108, 7, 15, 19, 30), new Date(108, 7, 15, 20, 30)),
					new ScheduleDataItem("5", "The Flaming Lips", new Date(108, 7, 15, 21, 15), new Date(108, 7, 15, 22, 15))
			};
			return ret;
		} else if (tent == "CHARLIE" || tent == "FOXTROT") {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem("12", "The Girls", new Date(108, 7, 15, 14, 45), new Date(108, 7, 15, 15, 15)),
					new ScheduleDataItem("13", "Hadouken!", new Date(108, 7, 15, 16, 10), new Date(108, 7, 15, 16, 45)),
					new ScheduleDataItem("14", "Holy Fuck", new Date(108, 7, 15, 17, 45), new Date(108, 7, 15, 18, 30)),
					new ScheduleDataItem("15", "Triggerfinger", new Date(108, 7, 15, 19, 30), new Date(108, 7, 15, 20, 15)),
					new ScheduleDataItem("16", "Late of the Pier", new Date(108, 7, 15, 21, 15), new Date(108, 7, 15, 22, 0))
			};
			for (i = 0; i < ret.length; i ++) {
				ret[i].setDescription("Porcupine Tree is a british rock band formed in Hemel Hempstead, Hertfordshire, England in 1987. During the course of the band’s history, they have at times incorporated psychedelic rock, alternative, ambient, techno, and, most recently, metal and post-rock into their unique style of progressive rock.");
			}
			return ret;
		} else {
			ScheduleDataItem ret[] = {
					new ScheduleDataItem("6", "The Pigeon Detectives", new Date(108, 7, 15, 14, 0), new Date(108, 7, 15, 14, 45)),
					new ScheduleDataItem("7", "The Presidents of the United States", new Date(108, 7, 15, 15, 15), new Date(108, 7, 15, 16, 10)),
					new ScheduleDataItem("8", "The Wombats", new Date(108, 7, 15, 16, 50), new Date(108, 7, 15, 17, 45)),
					new ScheduleDataItem("9", "Dropkick Murphys", new Date(108, 7, 15, 18, 30), new Date(108, 7, 15, 19, 30)),
					new ScheduleDataItem("10", "The Kooks", new Date(108, 7, 15, 20, 15), new Date(108, 7, 15, 22, 15)),
					new ScheduleDataItem("11", "Anouk", new Date(108, 7, 15, 22, 0), new Date(108, 7, 15, 23, 0))
			};
			for (i = 0; i < ret.length; i ++) {
				ret[i].setDescription("Aphex Twin, born Richard David James, August 18, 1971, in Limerick, Ireland to Welsh parents Lorna and Derek James, is an electronic music artist. He grew up in Cornwall, United Kingdom and started producing music around the age of 12.");
			}
			return ret;
		}
	}

	@Override
	public void characters(char[] arg0, int arg1, int arg2) throws SAXException {
		// TODO Auto-generated method stub
		// Log.i("XML", "" + arg2 + " characters " + new String(arg0));
	}

	@Override
	public void endDocument() throws SAXException {
		Log.i("XML", "endDocument");
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if (localName == "item") {
			curTent.addItem(curItem);
			curItem = null;
		} else if (localName == "line") {
			tents.add(curTent);
			curTent = null;
		}
	}

	@Override
	public void endPrefixMapping(String arg0) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
			throws SAXException {
		// TODO Auto-generated method stub
		Log.i("XML", "ignorableWhitespace");
	}

	@Override
	public void processingInstruction(String arg0, String arg1)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDocumentLocator(Locator arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void skippedEntity(String arg0) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startDocument() throws SAXException {
		Log.i("XML", "startDocument");
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		curString = "";
		
		if (localName == "schedule") {
			id = atts.getValue("", "id");
			title = atts.getValue("", "title");
		} else if (localName == "linkType") {
			// Ignore for now.
		} else if (localName == "line") {
			curTent = new ScheduleDataLine(atts.getValue("", "id"),
					                       atts.getValue("", "title"));
		} else if (localName == "item") {
			SimpleDateFormat df;
			Date startTime, endTime;

			Log.i("XMLitem", "" + atts.getValue("", "id") + " " + atts.getValue("", "title") +
				      " " + atts.getValue("", "startTime") + " " + atts.getValue("", "endTime"));

			try {
				df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				df.setTimeZone(TimeZone.getTimeZone("UTC"));
				startTime = df.parse(atts.getValue("", "startTime"));
				endTime = df.parse(atts.getValue("", "endTime"));
				
				Log.i("XMLitem", atts.getValue("", "id") + " " + atts.getValue("", "title") +
				      " " + startTime + " " + endTime);

				curItem = new ScheduleDataItem(atts.getValue("", "id"),
	                       atts.getValue("", "title"),
	                       startTime, endTime);
			} catch (ParseException e) {
				Log.e("XML", "Error while trying to parse a date");
			}
		}
		Log.i("XML", uri + " " + localName);
		Log.i("XML", "uri='" + atts.getURI(0) + "' ln='" + atts.getLocalName(0) + "'");
		Log.i("XML", "id1=" + atts.getValue("", "id"));
		Log.i("XML", "id2=" + atts.getValue(uri, "id"));
	}

	@Override
	public void startPrefixMapping(String arg0, String arg1)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}
}
