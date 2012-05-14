/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.util.Xml;

public class Schedule {
	private final int detectHeaderSize = 1024;
	
	private Giggity app;
	private Db.Connection db;
	
	private String id, url;
	private String title;

	private LinkedList<Schedule.Line> tents;
	private HashMap<String,Schedule.LinkType> linkTypes;
	private TreeMap<String,Schedule.Item> allItems;
	private HashMap<String,TreeSet<Schedule.Item>> trackMap;

	private Date firstTime, lastTime;
	private Date curDay, curDayEnd;
	private Date dayChange;
	LinkedList<Date> dayList;

	private boolean fullyLoaded;
	private Handler progressHandler;
	
	public Schedule(Giggity ctx) {
		app = ctx;
	}

	public static String hashify(String url) {
		String ret = "";
		try {
			MessageDigest md5 = MessageDigest.getInstance("SHA-1");
			md5.update(url.getBytes());
			byte raw[] = md5.digest();
			for (int i = 0; i < raw.length; i ++)
				ret += String.format("%02x", raw[i]);
		} catch (NoSuchAlgorithmException e) {
			// WTF
		}
		return ret;
	}
	
	public LinkedList<Date> getDays() {
		if (dayList == null) {
			Calendar day = new GregorianCalendar();
			day.setTime(firstTime);
			day.set(Calendar.HOUR_OF_DAY, dayChange.getHours());
			day.set(Calendar.MINUTE, dayChange.getMinutes());
			/* Add a day 0 (maybe there's an event before the first day officially
			 * starts?). Saw this in the CCC Fahrplan for example. */
			if (day.getTime().after(firstTime))
				day.add(Calendar.DATE, -1);

			Calendar dayEnd = new GregorianCalendar();
			dayEnd.setTime(day.getTime());
			dayEnd.add(Calendar.DATE, 1);
			
			dayList = new LinkedList<Date>();
			while (day.getTime().before(lastTime)) {
				/* Some schedules have empty days in between. :-/ Skip those. */
				for (Schedule.Item item : allItems.values()) {
					if (item.getStartTime().compareTo(day.getTime()) >= 0 &&
						item.getEndTime().compareTo(dayEnd.getTime()) <= 0) {
						dayList.add(day.getTime());
						break;
					}
				}
				day.add(Calendar.DATE, 1);
				dayEnd.add(Calendar.DATE, 1);
			}
		}
		return dayList;
	}
	
	public Date getDay() {
		return curDay;
	}
	
	public void setDay(int day) {
		if (day == -1) {
			curDay = curDayEnd = null;
			return;
		}
		
		if (day >= getDays().size())
			day = 0;
		curDay = getDays().get(day);
		
		Calendar dayEnd = new GregorianCalendar();
		dayEnd.setTime(curDay);
		dayEnd.add(Calendar.DAY_OF_MONTH, 1);
		curDayEnd = dayEnd.getTime();
	}
	
	/** Get earliest item.startTime */
	public Date getFirstTime() {
		if (curDay == null)
			return firstTime;
		
		Date ret = null;
		for (Schedule.Item item : allItems.values()) {
			if (item.getStartTime().compareTo(curDay) >= 0 &&
				item.getEndTime().compareTo(curDayEnd) <= 0) {
				if (ret == null || item.getStartTime().before(ret))
					ret = item.getStartTime();
			}
		}
		
		return ret;
	}
	
	/** Get highest item.endTime */
	public Date getLastTime() {
		if (curDay == null)
			return lastTime;
		
		Date ret = null;
		for (Schedule.Item item : allItems.values()) {
			if (item.getStartTime().compareTo(curDay) >= 0 &&
				item.getEndTime().compareTo(curDayEnd) <= 0) {
				if (ret == null || item.getEndTime().after(ret))
					ret = item.getEndTime();
			}
		}
		
		return ret;
	}
	
	/* If true, this schedule defines link types so icons should suffice.
	 * If false, we have no types and should show full URLs.
	 */
	public boolean hasLinkTypes() {
		return linkTypes.size() > 1;
	}
	
	public void setProgressHandler(Handler handler) {
		progressHandler = handler;
	}

	public void loadSchedule(String url_, Fetcher.Source source) throws IOException {
		url = url_;
		
		id = null;
		title = null;
		
		allItems = new TreeMap<String,Schedule.Item>();
		linkTypes = new HashMap<String,Schedule.LinkType>();
		tents = new LinkedList<Schedule.Line>();
		trackMap = null; /* Only assign if we have track info. */
		
		firstTime = null;
		lastTime = null;
		
		dayList = null;
		curDay = null;
		curDayEnd = null;
		dayChange = new Date();
		dayChange.setHours(6);
		
		fullyLoaded = false;

		String head;
		Fetcher f = null;
		BufferedReader in;
		
		try {
			f = app.fetch(url, source);
			f.setProgressHandler(progressHandler);
			in = f.getReader();
			char[] headc = new char[detectHeaderSize];
			
			/* Read the first KByte (but keep it buffered) to try to detect the format. */
			in.mark(detectHeaderSize);
			in.read(headc, 0, detectHeaderSize);
			in.reset();

			head = new String(headc).toLowerCase();
		} catch (Exception e) {
			if (f != null)
				f.cancel();
			
			Log.e("Schedule.loadSchedule", "Exception while downloading schedule: " + e);
			e.printStackTrace();
			throw new RuntimeException("Network I/O problem: " + e);
		}

		/* Yeah, I know this is ugly, and actually reasonably fragile. For now it
		 * just seems somewhat more efficient than doing something smarter, and
		 * I want to avoid doing XML-specific stuff here already. */
		try {
			if (head.contains("<icalendar") && head.contains("<vcalendar")) {
				loadXcal(in);
			} else if (head.contains("<schedule") && head.contains("<conference")) {
				loadPentabarf(in);
			} else if (head.contains("<response") && head.contains("<grouped-summary")) {
				loadVerdi(in);
			} else if (head.contains("<schedule") && head.contains("<line")) {
				loadDeox(in);
			} else if (head.contains("begin:vcalendar")) {
				loadIcal(in);
			} else {
				Log.d("head", head);
				throw new RuntimeException("File format not recognized");
			}
		} catch (RuntimeException e) {
			f.cancel();
			throw e;
		}
		
		f.keep();
		
		if (title == null)
			if (id != null)
				title = id;
			else
				title = url;

		if (id == null)
			id = hashify(url);
		
		db = app.getDb();
		db.setSchedule(this, url, f.getSource() == Fetcher.Source.ONLINE);
		
		/* From now, changes should be marked to go back into the db. */
		fullyLoaded = true;
	}
	
	private void loadDeox(BufferedReader in) {
		loadXml(in, new DeoxParser());
	}
	
	private void loadXcal(BufferedReader in) {
		loadXml(in, new XcalParser());
	}
	
	private void loadPentabarf(BufferedReader in) {
		loadXml(in, new PentabarfParser());
	}
	
	private void loadVerdi(BufferedReader in) {
		loadXml(in, new VerdiParser());
	}
	
	private void loadXml(BufferedReader in, ContentHandler parser) {
		try {
			Xml.parse(in, parser);
			in.close();
		} catch (Exception e) {
			Log.e("Schedule.loadXml", "XML parse exception: " + e);
			e.printStackTrace();
			throw new RuntimeException("XML parsing problem: " + e);
		}
	}
	
	private void loadIcal(BufferedReader in) {
		/* Luckily the structure of iCal maps pretty well to its xCal counterpart.
		 * That's what this function does.
		 * Tested against http://yapceurope.lv/ye2011/timetable.ics and the FOSDEM
		 * 2011 iCal export (but please don't use this unless the event offers
		 * nothing else). */ 
		XcalParser p = new XcalParser();
		String line, s;
		try {
			line = "";
			while (true) {
				s = in.readLine();
				if (s != null && s.startsWith(" ")) {
					line += s.substring(1);
					/* Line continuation. Get the rest before we process anything. */
					continue;
				} else if (line.contains(":")) {
					String split[] = line.split(":", 2);
					String key, value;
					key = split[0].toLowerCase();
					value = split[1];
					if (key.equals("begin")) {
						/* Some blocks (including vevent, the only one we need)
						 * have proper begin:vevent and end:vevent dividers. */
						p.startElement("", value.toLowerCase(), "", null);
					} else if (key.equals("end")) {
						p.endElement("", value.toLowerCase(), "");
					} else {
						/* Chop off attributes. Could pass them but not reading them anyway. */
						if (key.contains(";"))
							key = key.substring(0, key.indexOf(";"));
						value = value.replace("\\n", "\n").replace("\\,", ",")
						             .replace("\\;", ";").replace("\\\\", "\\");
						/* Fake <key>value</key> */
						p.startElement("", key, "", null);
						p.characters(value.toCharArray(), 0, value.length());
						p.endElement("", key, "");
					}
				}
				if (s != null)
					line = s;
				else
					break;
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Read error: " + e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new RuntimeException("Parse error: " + e);
		}
	}

	public void commit() {
		Log.d("Schedule", "Saving all changes to the database");
		for (Schedule.Item item : allItems.values()) {
			item.commit();
		}
	}
	
	public void sleep() {
		db.sleep();
	}
	
	public void resume() {
		db.resume();
	}
	
	public Db.Connection getDb() {
		return db;
	}
	
	public String getId() {
		return id;
	}
	
	public String getUrl() {
		return url;
	}
	
	public String getTitle() {
		return title;
	}
	
	public LinkedList<Schedule.Line> getTents() {
		LinkedList<Line> ret = new LinkedList<Line>();
		for (Line line : tents) {
			if (line.getItems().size() > 0)
				ret.add(line);
		}
		
		return ret;
	}
	
	public Schedule.LinkType getLinkType(String id) {
		return linkTypes.get(id);
	}
	
	public Item getItem(String id) {
		return allItems.get(id);
	}
	
	public ArrayList<String> getTracks() {
		if (trackMap == null)
			return null;
		
		ArrayList<String> ret = new ArrayList<String>();
		for (String name : trackMap.keySet()) {
			for (Item item : trackMap.get(name)) {
				if (!item.getHidden()) {
					ret.add(name);
					break;
				}
			}
		}
		
		return ret;
	}
	
	public ArrayList<Item> getTrackItems(String track) {
		if (trackMap == null)
			return null;
		
		ArrayList<Item> ret = new ArrayList<Item>();
		for (Item item : trackMap.get(track)) {
			if (!item.getHidden())
				ret.add(item);
		}
		
		return ret;
	}
	
	public AbstractList<Item> searchItems(String q_) {
		/* No, sorry, this is no full text search. It's ugly and amateuristic,
		 * but hopefully sufficient. Full text search would probably require
		 * me to keep full copies of all schedules in SQLite (or find some
		 * other FTS solution). And we have the whole thing in RAM anyway so
		 * this search is pretty fast.
		 */
		LinkedList<Item> ret = new LinkedList<Item>();
		String[] q = q_.toLowerCase().split("\\s+");
		for (Line line : getTents()) {
			for (Item item : line.getItems()) {
				String d = item.getTitle() + " ";
				if (item.getDescription() != null)
					d += item.getDescription() + " ";
				if (item.getTrack() != null)
					d += item.getTrack() + " ";
				if (item.getSpeakers() != null)
					for (String i : item.getSpeakers())
						d += i + " ";
				d = d.toLowerCase();
				int i;
				for (i = 0; i < q.length; i ++) {
					if (!d.contains(q[i]))
						break;
				}
				if (i == q.length)
					ret.add(item);
			}
		}
		
		return (AbstractList<Item>) ret;
	}
	
	/* Some "proprietary" file format I started with. Actually the most suitable when I
	 * generate my own schedules so I'll definitely *not* deprecate it. */
	private class DeoxParser implements ContentHandler {
		private Schedule.Line curTent;
		private Schedule.Item curItem;
		private String curString;
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			
			if (localName == "schedule") {
				id = atts.getValue("", "id");
				title = atts.getValue("", "title");
			} else if (localName == "linkType") {
				String id = atts.getValue("", "id");
				String icon = atts.getValue("", "icon");
				
				Schedule.LinkType lt = new Schedule.LinkType(id);
				if (icon != null)
					lt.setIconUrl(icon);
				
				linkTypes.put(id, lt);
			} else if (localName == "line") {
				curTent = new Schedule.Line(atts.getValue("", "id"),
				                            atts.getValue("", "title"));
			} else if (localName == "item") {
				Date startTime, endTime;
	
				try {
					startTime = new Date(Long.parseLong(atts.getValue("", "startTime")) * 1000);
					endTime = new Date(Long.parseLong(atts.getValue("", "endTime")) * 1000);
					
					curItem = new Schedule.Item(atts.getValue("", "id"),
					                            atts.getValue("", "title"),
					                            startTime, endTime);
				} catch (NumberFormatException e) {
					Log.w("Schedule.loadDeox", "Error while parsing date: " + e);
				}
			} else if (localName == "itemLink") {
				Schedule.LinkType lt = linkTypes.get(atts.getValue("", "type"));
				curItem.addLink(lt, atts.getValue("", "href"));
			}
		}
	
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length); 
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
			} else if (localName == "itemDescription") {
				if (curItem != null)
					curItem.setDescription(curString);
			}
		}
		
		@Override
		public void startDocument() throws SAXException {
		}
	
		@Override
		public void endDocument() throws SAXException {
		}
		
		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {
			
		}
	
		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}
	
		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {
		}
	
		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}
	
		@Override
		public void setDocumentLocator(Locator arg0) {
		}
	
		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}
	}
	
	private class XcalParser implements ContentHandler {
		//private Schedule.Line curTent;
		private HashMap<String,Schedule.Line> tentMap;
		private HashMap<String,String> eventData;
		private String curString;
		private Schedule.LinkType lt;

		SimpleDateFormat df;

		public XcalParser() {
			tentMap = new HashMap<String,Schedule.Line>();
			df = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
			//df.setTimeZone(TimeZone.getTimeZone("UTC"));
			lt = new Schedule.LinkType("link");
		}
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("vevent")) {
				eventData = new HashMap<String,String>();
			}
		}
	
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length); 
		}
	
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equals("vevent")) {
				String uid, name, location, startTimeS, endTimeS, s;
				Date startTime, endTime;
				Schedule.Item item;
				Schedule.Line line;
				
				if ((uid = eventData.get("uid")) == null ||
				    (name = eventData.get("summary")) == null ||
				    (location = eventData.get("location")) == null ||
				    (startTimeS = eventData.get("dtstart")) == null ||
				    (endTimeS = eventData.get("dtend")) == null) {
					Log.w("Schedule.loadXcal", "Invalid event, some attributes are missing.");
					return;
				}
				
				try {
					startTime = df.parse(startTimeS);
					endTime = df.parse(endTimeS);
				} catch (ParseException e) {
					Log.w("Schedule.loadXcal", "Can't parse date: " + e);
					return;
				}

				item = new Schedule.Item(uid, name, startTime, endTime);
				
				if ((s = eventData.get("description")) != null) {
					item.setDescription(s);
				}
				
				if ((s = eventData.get("url")) != null) {
					item.addLink(lt, s);
				}

				if ((line = tentMap.get(location)) == null) {
					line = new Schedule.Line(location, location);
					tents.add(line);
					tentMap.put(location, line);
				}
				line.addItem(item);
				
				eventData = null;
			} else if (localName.equals("x-wr-calname")) {
				id = curString;
			} else if (localName.equals("x-wr-caldesc")) {
				title = curString;
			} else if (eventData != null) {
				eventData.put(localName, curString);
			}
		}
		
		@Override
		public void startDocument() throws SAXException {
		}
	
		@Override
		public void endDocument() throws SAXException {
		}
		
		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {
			
		}
	
		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}
	
		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {
		}
	
		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}
	
		@Override
		public void setDocumentLocator(Locator arg0) {
		}
	
		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}
	}
	
	private class PentabarfParser implements ContentHandler {
		private Schedule.Line curTent;
		private HashMap<String,Schedule.Line> tentMap;
		private HashMap<String,String> propMap;
		private String curString;
		private LinkedList<String> links, persons;
		private Schedule.LinkType lt;
		private Date curDay;
		private HashSet<String> trackSet;

		SimpleDateFormat df, tf;

		public PentabarfParser() {
			tentMap = new HashMap<String,Schedule.Line>();
			trackSet = new HashSet<String>();
			
			df = new SimpleDateFormat("yyyy-MM-dd");
			tf = new SimpleDateFormat("HH:mm");
			
			lt = new Schedule.LinkType("link");
		}
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("conference") || localName.equals("event")) {
				propMap = new HashMap<String,String>();
				propMap.put("id", atts.getValue("id"));
				
				links = new LinkedList<String>();
				persons = new LinkedList<String>();
			} else if (localName.equals("day")) {
				try {
					curDay = df.parse(atts.getValue("date"));
				} catch (ParseException e) {
					Log.w("Schedule.loadPentabarf", "Can't parse date: " + e);
					return;
				}
			} else if (localName.equals("room")) {
				String name = atts.getValue("name");
				Schedule.Line line;
				
				if (name == null)
					return;
				
				if ((line = tentMap.get(name)) == null) {
					line = new Schedule.Line(name, name);
					tents.add(line);
					tentMap.put(name, line);
				}
				curTent = line;
			} else if (localName.equals("link") && links != null) {
				String href = atts.getValue("href");
				if (href != null)
					links.add(href);
			}
		}
	
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length); 
		}
	
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equals("conference")) {
				title = propMap.get("title");
				if (propMap.get("day_change") != null) {
					try {
						dayChange = tf.parse(propMap.get("day_change"));
					} catch (ParseException e) {
						Log.w("Schedule.loadPentabarf", "Couldn't parse day_change: " + propMap.get("day_change"));
					}
				}
			} else if (localName.equals("event")) {
				String id, title, startTimeS, durationS, s, desc;
				Calendar startTime, endTime;
				Schedule.Item item;
				
				if ((id = propMap.get("id")) == null ||
				    (title = propMap.get("title")) == null ||
				    (startTimeS = propMap.get("start")) == null ||
				    (durationS = propMap.get("duration")) == null) {
					Log.w("Schedule.loadPentabarf", "Invalid event, some attributes are missing.");
					return;
				}
				
				try {
					Date tmp;
					
					startTime = new GregorianCalendar();
					startTime.setTime(curDay);
					tmp = tf.parse(startTimeS);
					startTime.add(Calendar.HOUR, tmp.getHours());
					startTime.add(Calendar.MINUTE, tmp.getMinutes());
					
					endTime = new GregorianCalendar();
					endTime.setTime(startTime.getTime());
					tmp = tf.parse(durationS);
					endTime.add(Calendar.HOUR, tmp.getHours());
					endTime.add(Calendar.MINUTE, tmp.getMinutes());
				} catch (ParseException e) {
					Log.w("Schedule.loadPentabarf", "Can't parse date: " + e);
					return;
				}

				item = new Schedule.Item(id, title, startTime.getTime(), endTime.getTime());
				
				desc = "";
				if ((s = propMap.get("subtitle")) != null) {
					s.replaceAll("\n*$", "");
					if (s != "")
						desc += "― " + s + "\n\n";
				}
				if ((s = propMap.get("abstract")) != null) {
					s.replaceAll("\n*$", "");
					desc += s + "\n\n";
				}
				if ((s = propMap.get("description")) != null) {
					desc += s;
				}
				item.setDescription(rewrap(desc));
				
				if ((s = propMap.get("track")) != null && !s.equals("")) {
					item.setTrack(s);
					trackSet.add(s);
				}
				for (String i : links)
					item.addLink(lt, i);
				for (String i : persons)
					item.addSpeaker(i);

				curTent.addItem(item);
				propMap = null;
				links = null;
				persons = null;
			} else if (localName.equals("person")) {
				persons.add(curString);
			} else if (propMap != null) {
				propMap.put(localName, curString);
			}
		}
		
		@Override
		public void startDocument() throws SAXException {
		}
	
		@Override
		public void endDocument() throws SAXException {
		}
		
		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {
			
		}
	
		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}
	
		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {
		}
	
		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}
	
		@Override
		public void setDocumentLocator(Locator arg0) {
		}
	
		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}
	}
	
	/* TODO: Find name. */
	private class VerdiParser implements ContentHandler {
		private LinkedList<RawItem> rawItems;
		private HashMap<Integer,LinkedList<String>> candidates;
		private TreeMap<Integer,String> trackMap;
		private TreeMap<Integer,Schedule.Line> tentMap;
		private TreeMap<Integer,Schedule.Line> tentMapSorted;
		
		private String curString;
		
		private LinkedList<HashMap<String,String>> propMapStack;

		SimpleDateFormat df;

		public VerdiParser() {
			rawItems = new LinkedList<RawItem>();
			candidates = new HashMap<Integer,LinkedList<String>>();
			trackMap = new TreeMap<Integer,String>();
			tentMap = new TreeMap<Integer,Schedule.Line>();
			tentMapSorted = new TreeMap<Integer,Schedule.Line>();
			propMapStack = new LinkedList<HashMap<String,String>>();
			df = new SimpleDateFormat("yyyy-MM-dd");
		}
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			HashMap<String,String> propMap = null;
			
			curString = "";
			if (localName.equals("event")) {
				propMap = new HashMap<String,String>();
				// Don't use the ID, it's far from unique.
				//propMap.put("id", atts.getValue("id"));
			} else	if (localName.equals("room")) {
				propMap = new HashMap<String,String>();
				propMap.put("id", atts.getValue("id"));
			} else	if (localName.equals("area")) {
				propMap = new HashMap<String,String>();
				propMap.put("id", atts.getValue("id"));
			} else if (localName.equals("slot")) {
				String id = atts.getValue("id");
				String title = atts.getValue("title");
				
				Calendar startTime;
				startTime = new GregorianCalendar();
				try {
					startTime.setTime(df.parse(atts.getValue("date")));
				} catch (ParseException e) {
					// FAIL D:
				}
				startTime.add(Calendar.HOUR, Integer.parseInt(atts.getValue("hour")));
				startTime.add(Calendar.MINUTE, Integer.parseInt(atts.getValue("minute")));

				Calendar endTime;
				endTime = new GregorianCalendar();
				endTime.setTime(startTime.getTime());
				endTime.add(Calendar.MINUTE, 30 * Integer.parseInt(atts.getValue("colspan")));

				Item item = new Schedule.Item(id, title, startTime.getTime(), endTime.getTime());
				item.setDescription(rewrap(atts.getValue("abstract")));
				rawItems.add(new RawItem(item, atts));
			} else if (localName.equals("person")) {
				LinkedList<String> speakers;
				int id = Integer.parseInt(atts.getValue("candidate"));
				if ((speakers = candidates.get(id)) == null)
					candidates.put(id, (speakers = new LinkedList<String>()));
				
				/* Just use main to put the main person at the head of the list. */
				if (Integer.parseInt(atts.getValue("main")) > 0)
					speakers.addFirst(atts.getValue("name"));
				else
					speakers.addLast(atts.getValue("name"));
			}
			propMapStack.addFirst(propMap);
		}
	
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length); 
		}
	
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			HashMap<String,String> propMap;
			
			if ((propMap = propMapStack.removeFirst()) != null) {
				/* If for the current tag we had a propMap, use it and destroy it. */
				if (localName.equals("event")) {
					if (propMap.containsKey("name"))
						title = propMap.get("name");
				} else if (localName.equals("room")) {
					Schedule.Line tent = new Schedule.Line(propMap.get("id"), propMap.get("name"));
					tentMap.put(Integer.parseInt(tent.getId()), tent);
					if (propMap.containsKey("position"))
						tentMapSorted.put(Integer.parseInt(propMap.get("position")), tent);
				} else if (localName.equals("area")) {
					trackMap.put(Integer.parseInt(propMap.get("id")), propMap.get("name"));
				}
			} else if (propMapStack.size() > 0 &&
					   (propMap = propMapStack.getFirst()) != null) {
				/* Alternatively, we may be busy filling in a propMap. */
				propMap.put(localName, curString);
			}
		}
		
		@Override
		public void startDocument() throws SAXException {
		}
	
		@Override
		public void endDocument() throws SAXException {
			merge();
		}
		
		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {
			
		}
	
		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}
	
		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {
		}
	
		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}
	
		@Override
		public void setDocumentLocator(Locator arg0) {
		}
	
		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}
		
		private void merge() {
			/* Since data can be in the file in any order, put all the pieces together at the end. */

			/* Only use the "position" field list if all tents had it. */
			if (tentMapSorted.size() != tentMap.size())
				tentMapSorted = tentMap;
			for (Line tent : tentMapSorted.values()) {
				tents.add(tent);
			}
			
			for (RawItem item : rawItems) {
				LinkedList<String> speakers;
				if ((speakers = candidates.get(item.candidate)) != null) {
					for (String name : speakers)
						item.item.addSpeaker(name);
				}
				
				String area;
				if ((area = trackMap.get(item.area)) != null) {
					item.item.setTrack(area);
				}
				
				Line tent = tentMap.get(item.room);
				tent.addItem(item.item);
			}
		}
		
		private class RawItem {
			public Item item;
			public int room;
			public int area;
			public int candidate;
			
			public RawItem(Item item_, Attributes atts) {
				item = item_;
				
				String s;
				try {
					if ((s = atts.getValue("room")) != null)
						room = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					room = -1;
				}
				try {
					if ((s = atts.getValue("area")) != null)
						area = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					area = -1;
				}
				try {
					if ((s = atts.getValue("candidate")) != null)
						candidate = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					candidate = -1;
				}
			}
		}
	}
	
	public class Line {
		private String id;
		private String title;
		private TreeSet<Schedule.Item> items;
		Schedule schedule;
		
		public Line(String id_, String title_) {
			id = id_;
			title = title_;
			items = new TreeSet<Schedule.Item>();
		}
		
		public String getId() {
			return id;
		}
		
		public String getTitle() {
			return title;
		}
		
		public void addItem(Schedule.Item item) {
			item.setLine(this);
			items.add(item);
			allItems.put(item.getId(), item);

			if (firstTime == null || item.getStartTime().before(firstTime))
				firstTime = item.getStartTime();
			if (lastTime == null || item.getEndTime().after(lastTime))
				lastTime = item.getEndTime();
		}
		
		public AbstractSet<Schedule.Item> getItems() {
			TreeSet<Schedule.Item> ret = new TreeSet<Schedule.Item>();
			Calendar dayStart = new GregorianCalendar();
			
			if (curDay != null)
				dayStart.setTime(curDay);
			
			for (Item item : items) {
				if (!item.getHidden() &&
				    (curDay == null || (item.getStartTime().after(dayStart.getTime()) &&
				                        item.getEndTime().before(curDayEnd))))
					ret.add(item);
			}
			return ret;
		}
	}
	
	public class Item implements Comparable<Item> {
		private String id;
		private Line line;
		private String title;
		private String track;
		private String description;
		private Date startTime, endTime;
		private LinkedList<Schedule.Item.Link> links;
		private LinkedList<String> speakers;
		
		private boolean remind;
		private boolean hidden;
		private int stars;
		private boolean newData;
		
		Item(String id_, String title_, Date startTime_, Date endTime_) {
			id = id_;
			title = title_;
			startTime = startTime_;
			endTime = endTime_;
			
			remind = false;
			setHidden(false);
			stars = -1;
			newData = false;
		}
		
		public Schedule getSchedule() {
			return Schedule.this;
		}
		
		public void setTrack(String track_) {
			track = track_;
			
			if (trackMap == null)
				trackMap = new HashMap<String,TreeSet<Schedule.Item>>();
			
			TreeSet<Schedule.Item> items;
			if ((items = trackMap.get(track)) == null) {
				items = new TreeSet<Schedule.Item>();
				trackMap.put(track, items);
			}
			
			items.add(this);
		}
		
		public void setDescription(String description_) {
			description = description_.replaceAll("\\s+$", "").replaceAll("^\\s+", "");
		}
		
		public void addLink(Schedule.LinkType type, String url) {
			Schedule.Item.Link link = new Schedule.Item.Link(type, url);
			
			if (links == null) {
				links = new LinkedList<Schedule.Item.Link>();
			}
			links.add(link);
		}
		
		public void addSpeaker(String name) {
			if (speakers == null) {
				speakers = new LinkedList<String>();
			}
			speakers.add(name);
		}
		
		public String getId() {
			return id;
		}
		
		public String getUrl() {
			return (getSchedule().getUrl() + "#" + getId()); 
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
		
		public String getTrack() {
			return track;
		}
		
		public String getDescription() {
			return description;
		}
		
		public AbstractList<String> getSpeakers() {
			return speakers;
		}
		
		public void setLine(Line line_) {
			line = line_;
		}
		
		public Line getLine() {
			return line;
		}
		
		public LinkedList<Schedule.Item.Link> getLinks() {
			return links;
		}

		public void setRemind(boolean remind_) {
			if (remind != remind_) {
				remind = remind_;
				newData |= fullyLoaded;
				app.updateRemind(this);
			}
		}
		
		public boolean getRemind() {
			return remind;
		}

		public void setHidden(boolean hidden) {
			if (hidden != this.hidden) {
				this.hidden = hidden;
				newData |= fullyLoaded;
			}
		}
		
		public boolean getHidden() {
			return hidden;
		}

		public void setStars(int stars_) {
			if (stars != stars_) {
				stars = stars_;
				newData |= fullyLoaded;
			}
		}
		
		public int getStars() {
			return stars;
		}
		
		public void commit() {
			if (newData) {
				db.saveScheduleItem(this);
				newData = false;
			}
		}
		
		public class Link {
			private Schedule.LinkType type;
			private String url;
			
			public Link(Schedule.LinkType type_, String url_) {
				type = type_;
				url = url_;
			}
			
			public Schedule.LinkType getType() {
				return type;
			}
			
			public String getUrl() {
				return url;
			}
		}

		@Override
		public int compareTo(Item another) {
			int ret;
			if ((ret = getStartTime().compareTo(another.getStartTime())) != 0)
				return ret;
			else if ((ret = getTitle().compareTo(another.getTitle())) != 0)
				return ret;
			else
				return another.hashCode() - hashCode();
		}

		public int compareTo(Date d) {
			/* 0 if the event is "now" (d==now), 
			 * -1 if it's in the future, 
			 * 1 if it's in the past. */
			if (d.before(getStartTime()))
				return -1;
			else if (getEndTime().after(d))
				return 0;
			else
				return 1;
		}
		
		public boolean overlaps(Item other) {
			return ((other.getStartTime().after(getStartTime()) && other.getStartTime().before(getEndTime())) ||
			        (other.getEndTime().after(getStartTime()) && other.getEndTime().before(getEndTime())) ||
			        (!other.getStartTime().after(getStartTime()) && !other.getEndTime().before(getEndTime())));
		}
	}

	public class LinkType {
		private String id;
		private Drawable iconDrawable;
		
		public LinkType(String id_) {
			id = id_;
			iconDrawable = app.getResources().getDrawable(R.drawable.browser_small);
		}
		
		public void setIconUrl(String url_) {
			/* Permanent caching, if you change the icon, change the URL.. 
			 * At least we only save a cached copy if the file was parsed successfully. */
			File fn = new File(app.getCacheDir(), "icon." + hashify(url_)), fntmp = null;
			
			try {
				if (fn.canRead()) {
					iconDrawable = Drawable.createFromPath(fn.getPath());
				} else {
					/* %@#&)@*&@#%& Java has 3275327 different kinds of streams/readers or whatever.
					 * TeeReader won't work here so just go for the fucking kludge.
					 */
					fntmp = new File(app.getCacheDir(), "tmp." + hashify(url_));
					FileOutputStream copy = new FileOutputStream(fntmp);
					byte[] b = new byte[1024];
					int len;
					URL dl = new URL(url_);
					InputStream in = dl.openStream();
					while ((len = in.read(b)) != -1) {
						copy.write(b, 0, len);
					}
					
					iconDrawable = Drawable.createFromPath(fntmp.getPath());
					fn.delete();
					fntmp.renameTo(fn);
				}
			} catch (Exception e) {
				Log.e("setIconUrl", "Error while dowloading icon " + url_);
				e.printStackTrace();
			}
		}
		
		public Drawable getIcon() {
			return iconDrawable;
		}
	}
	
	static String rewrap(String desc) {
		/* Replace newlines with spaces unless there are two of them,
		 * or if the following line starts with a character. */
		if (desc != null)
			return desc.replace("\r", "").replaceAll("([^\n]) *\n *([a-zA-Z0-9])", "$1 $2");
		else
			return null;
	}
	
	public Selections getSelections() {
		boolean empty = true;
		Selections ret = new Selections(this);
		
		for (Item item : allItems.values()) {
			int t = 0;
			if (item.getRemind())
				t += 1;
			if (item.getHidden())
				t += 2;
			if (t > 0)
				empty = false;
			ret.selections.put(item.getId(), t);
		}
		
		/* Don't generate anything if there is nothing worth exporting. */
		if (empty)
			return null;
		
		return ret;
	}
	
	static public class Selections implements Serializable {
		public String url;
		public HashMap<String,Integer> selections;
		
		public Selections(Schedule sched) {
			url = sched.getUrl();
		}
		
		public Selections(byte[] in) throws DataFormatException {
			if (in == null || in[0] != 0x01)
				throw new DataFormatException("Magic number missing");
			
			Inflater unc = new Inflater(); 
			unc.setInput(in, 1, in.length - 1);
			byte[] orig = new byte[in.length * 10];
			int len = unc.inflate(orig);
			
			ByteArrayInputStream rd = new ByteArrayInputStream(orig, 0, len);
			
			len = rd.read() * 0x100 + rd.read();
			byte[] urlb = new byte[len];
			if (rd.read(urlb, 0, len) != len)
				throw new DataFormatException("Ran out of data while reading URL");
			try {
				url = new String(urlb, "utf-8");
			} catch (UnsupportedEncodingException e) {}
			
			selections = new HashMap<String,Integer>();
			while (rd.available() > 4) {
				int type = rd.read();
				
				if (type > 0x03) {
					Log.w("Schedule.Selections", "Discarding unknown bits in type: " + type);
					type &= 0x03;
				}
				
				int i, n = rd.read() * 0x100 + rd.read();
				for (i = 0; i < n; i ++) {
					len = rd.read();
					if (len == -1 || rd.available() < len)
						throw new DataFormatException("Ran out of data while reading ID");
					
					byte[] idb = new byte[len];
					rd.read(idb, 0, len);
					String id;
					try {
						id = new String(idb, "utf-8");
					} catch (UnsupportedEncodingException e) {continue;}
					selections.put(id, type);
				}
			}
		}
		
		/* Export all selections/deletions/etc in a byte array. Should export this to other devices
		 * using QR codes. The format is pretty simple, see the comments. It's zlib-compressed to
		 * hopefully keep it small enough for a QR rendered on a phone. */
		public byte[] export() {
			LinkedList<String> sels[] = (LinkedList<String>[]) new LinkedList[4];
			int i;
			
			for (i = 0; i < 4; i ++)
				sels[i] = new LinkedList<String>();
			
			for (String id : selections.keySet()) {
				int t = selections.get(id);
				sels[t].add(id);
			}
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				/* Length of URL, 16 bits network order */
				byte[] urlb = url.getBytes("utf-8");
				out.write(urlb.length >> 8);
				out.write(urlb.length & 0xff);
				out.write(urlb);
				for (i = 1; i < 4; i ++) {
					if (sels[i].size() == 0)
						continue;
					
					/* Type. Bitfield. 1 == remember, 2 == hide */
					out.write(i);
					/* Number of items, 16 bits network order */
					out.write(sels[i].size() >> 8);
					out.write(sels[i].size() & 0xff);
					for (String item : sels[i]) {
						byte[] id = item.getBytes("utf-8");
						if (id.length > 255) {
							/* Way too long. Forget it. :-/ */
							out.write(0);
							Log.e("Schedule.getSelections", "Ridiculously long item id: " + item);
						} else {
							out.write(id.length);
							out.write(id);
						}
					}
				}
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			
			/* UGH. Raw arrays in Java. :-( "I just want to compress 200 bytes of data..." */
			Deflater z = new Deflater(Deflater.BEST_COMPRESSION);
			z.setInput(out.toByteArray());
			z.finish();
			byte[] ret1 = new byte[out.size() * 2 + 100];
			byte[] ret2 = new byte[z.deflate(ret1) + 1];
			/* "Version" number. Keep it outside the compressed bit because zlib doesn't have magic numbers.
			 * I'll use this instead. I mostly need it to separate scanned URL QR codes from this binary data
			 * so one byte like this is enough. */
			ret2[0] = 0x01;
			for (i = 0; i < ret2.length - 1; i ++)
				ret2[i+1] = ret1[i];
			
			return ret2;
		}
		
		public int countBit(int bit) {
			int ret = 0;
			
			for (Integer t : selections.values()) {
				if ((t & bit) > 0)
					ret ++;
			}
			return ret;
		}
	}
}
