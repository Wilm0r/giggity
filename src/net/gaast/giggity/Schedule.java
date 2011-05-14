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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.net.ssl.HttpsURLConnection;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
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
	private HashMap<String,Schedule.Item> allItems;

	private Date firstTime, lastTime;
	private Date curDay, curDayEnd;
	private Date dayChange;
	LinkedList<Date> dayList;

	private boolean fullyLoaded;
	
	public Schedule(Giggity ctx) {
		app = ctx;
	}

	private String hashify(String url) {
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
			dayList = new LinkedList<Date>();
			Calendar day = new GregorianCalendar();
			Calendar dayEnd = new GregorianCalendar();
			day.setTime(firstTime);
			day.set(Calendar.HOUR_OF_DAY, dayChange.getHours());
			day.set(Calendar.MINUTE, dayChange.getMinutes());
			dayEnd.setTime(day.getTime());
			dayEnd.add(Calendar.DATE, 1);
			/* Add a day 0 (maybe there's an event before the first day officially
			 * starts?). Saw this in the CCC Fahrplan for example. */
			if (day.getTime().after(firstTime))
				day.add(Calendar.DATE, -1);
			while (day.getTime().before(lastTime)) {
				/* Some schedules have empty days in between. :-/ Skip those. */
				Iterator<Item> itemi = allItems.values().iterator();
				while (itemi.hasNext()) {
					Schedule.Item item = itemi.next();
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
	
	public Date getFirstTime() {
		if (curDay == null)
			return firstTime;
		
		Date ret = null;
		Iterator<Item> itemi = allItems.values().iterator();
		while (itemi.hasNext()) {
			Schedule.Item item = itemi.next();
			if (item.getStartTime().compareTo(curDay) >= 0 &&
				item.getEndTime().compareTo(curDayEnd) <= 0) {
				if (ret == null || item.getStartTime().before(ret))
					ret = item.getStartTime();
			}
		}
		
		return ret;
	}
	
	public Date getLastTime() {
		if (curDay == null)
			return lastTime;
		
		Date ret = null;
		Iterator<Item> itemi = allItems.values().iterator();
		while (itemi.hasNext()) {
			Schedule.Item item = itemi.next();
			if (item.getStartTime().compareTo(curDay) >= 0 &&
				item.getEndTime().compareTo(curDayEnd) <= 0) {
				if (ret == null || item.getEndTime().after(ret))
					ret = item.getEndTime();
			}
		}
		
		return ret;
	}

	public void loadSchedule(String source, boolean online) {
		id = null;
		title = null;
		
		allItems = new HashMap<String,Schedule.Item>();
		linkTypes = new HashMap<String,Schedule.LinkType>();
		tents = new LinkedList<Schedule.Line>();
		
		firstTime = null;
		lastTime = null;
		
		dayList = null;
		curDay = null;
		curDayEnd = null;
		dayChange = new Date();
		dayChange.setHours(6);
		
		fullyLoaded = false;

		BufferedReader in = null;
		String head;
		File fn, fntmp = null;
		URL dl;
		URLConnection dlc;
		
		url = source;

		try {
			dl = new URL(source);
			dlc = dl.openConnection();
			char[] headc = new char[detectHeaderSize];
			NetworkInfo network = ((ConnectivityManager)
					app.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo(); 
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
			
			fn = new File(app.getCacheDir(), "sched." + hashify(source));
			fntmp = null;
			
			try {
				/* Do HTTP stuff first, then HTTPS! Once we get a CastClassException, we stop. */
				HttpURLConnection.setFollowRedirects(true);
				
				/* Disabled for now since I can't get the default User-Agent until after sending it.
				String ua = ((HttpURLConnection)dlc).getRequestProperty("User-Agent");
				((HttpURLConnection)dlc).setRequestProperty("User-Agent", "Giggity, " + ua);
				*/
				
				if (fn.canRead() && fn.lastModified() > 0) {
					/* TODO: Not sure if it's a great idea to use inode metadata to store
					 * modified-since data, but it works so far.. */
					dlc.setIfModifiedSince(fn.lastModified());
				}
				
				if (!pref.getBoolean("strict_ssl", false))
					((HttpsURLConnection)dlc).setSSLSocketFactory(SSLRage.getSocketFactory());
			} catch (ClassCastException e) {
				/* It failed. Maybe we're HTTP only? Maybe even FTP? */
			}
			
			if (online && network != null && network.isConnected()) {
				int status;
				try {
					status = ((HttpURLConnection)dlc).getResponseCode();
				} catch (ClassCastException e) {
					/* Assume success if this isn't HTTP.. */
					status = 200;
				}
				Log.d("HTTP status", ""+dlc.getHeaderField(0));
				if (status == 200) {
					fntmp = new File(app.getCacheDir(), "tmp." + hashify(source));
					Writer copy = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fntmp)));
					Reader rawin = new InputStreamReader(dlc.getInputStream());
					in = new TeeReader(rawin, copy, 4096);
				} else if (status == 304) {
					Log.i("loadSchedule", "HTTP 304, using cached copy");
					/* Just continue, in = null so we'll read from cache. */
				} else {
					throw new RuntimeException("Download error: " + dlc.getHeaderField(0));
				}
			}

			if (in == null && fn.canRead()) {
				/* We have no download stream and should use the cached copy (i.e. we're offline). */
				in = new BufferedReader(new InputStreamReader(new FileInputStream(fn)));
				dlc = null;
			} else if (in == null) {
				throw new RuntimeException("No network connection or cached copy available.");
			}
			
			/* Read the first KByte (but keep it buffered) to try to detect the format. */
			in.mark(detectHeaderSize);
			in.read(headc, 0, detectHeaderSize);
			in.reset();

			head = new String(headc).toLowerCase();
		} catch (Exception e) {
			if (fntmp != null)
				fntmp.delete();
			
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
			} else {
				Log.d("head", head);
				throw new RuntimeException("File format not recognized");
			}
		} catch (RuntimeException e) {
			/* Delete any possibly corrupted file that we may have now. */
			if (fntmp != null)
				fntmp.delete();
			else
				fn.delete();
			throw e;
		}
		
		try {
			in.close();
		} catch (IOException e) {}
		
		if (fntmp != null) {
			fntmp.renameTo(fn);
		}
		
		/* Store last-modified date so we can cache more efficiently. */
		if (dlc != null && dlc.getLastModified() > 0)
			fn.setLastModified(dlc.getLastModified());
		
		if (id == null)
			id = hashify(source);

		db = app.getDb();
		db.setSchedule(this, source);
		
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

	public void commit() {
		Iterator<Item> it = allItems.values().iterator();
		
		Log.d("Schedule", "Saving all changes to the database");
		while (it.hasNext()) {
			Item item = it.next();
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
		return tents;
	}
	
	public Schedule.LinkType getLinkType(String id) {
		return linkTypes.get(id);
	}
	
	public Item getItem(String id) {
		return allItems.get(id);
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
		Iterator<Line> tenti = getTents().iterator();
		while (tenti.hasNext()) {
			Line line = tenti.next();
			Iterator<Item> itemi = line.getItems().iterator();
			while (itemi.hasNext()) {
				Item item = itemi.next();
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
	
	/* Some "proprietary" file format I started with. Should 
	 * probably remove it in favour of xcal and Pentabarf. */
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
			if (localName == "vevent") {
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
			if (localName == "vevent") {
				String uid, name, location, startTimeS, endTimeS, s;
				Date startTime, endTime;
				Schedule.Item item;
				Schedule.Line line;
				
				// Log.d("Event:", eventData.get("summary") + " in " + eventData.get("location"));
				
				if ((uid = eventData.get("uid")) == null ||
				    (name = eventData.get("summary")) == null ||
				    (location = eventData.get("location")) == null ||
				    (startTimeS = eventData.get("dtstart")) == null ||
				    (endTimeS = eventData.get("dtend")) == null) {
					Log.w("Schedule.loadXcal", "Invalid event, some attributes are missing.");
					return;
				}
				
				if ((s = eventData.get("attendee")) != null) {
					name += " (" + s + ")";
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
			} else if (localName == "x-wr-calname") {
				id = curString;
			} else if (localName == "x-wr-caldesc") {
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

		SimpleDateFormat df, tf;

		public PentabarfParser() {
			tentMap = new HashMap<String,Schedule.Line>();
			df = new SimpleDateFormat("yyyy-MM-dd");
			tf = new SimpleDateFormat("HH:mm");
			
			lt = new Schedule.LinkType("link");
		}
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			if (localName == "conference" || localName == "event") {
				propMap = new HashMap<String,String>();
				propMap.put("id", atts.getValue("id"));
				
				links = new LinkedList<String>();
				persons = new LinkedList<String>();
			} else if (localName == "day") {
				try {
					curDay = df.parse(atts.getValue("date"));
				} catch (ParseException e) {
					Log.w("Schedule.loadXcal", "Can't parse date: " + e);
					return;
				}
			} else if (localName == "room") {
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
			} else if (localName == "link" && links != null) {
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
				if ((s = propMap.get("abstract")) != null) {
					s.replaceAll("\n*$", "");
					desc += s + "\n\n";
				}
				if ((s = propMap.get("description")) != null) {
					desc += s;
				}
				/* Replace newlines with spaces unless there are two of them,
				 * or if the following line starts with a character. */
				desc = desc.replaceAll("([^\n]) *\n *([a-zA-Z0-9])", "$1 $2");
				item.setDescription(desc);
				
				if ((s = propMap.get("track")) != null)
					item.setTrack(s);
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
		private HashMap<Integer,String> areas;
		private TreeMap<Integer,Schedule.Line> tentMap;
		
		private HashMap<String,String> propMap;
		private String curString;

		SimpleDateFormat df;

		public VerdiParser() {
			rawItems = new LinkedList<RawItem>();
			candidates = new HashMap<Integer,LinkedList<String>>();
			areas = new HashMap<Integer,String>();
			tentMap = new TreeMap<Integer,Schedule.Line>();
			df = new SimpleDateFormat("yyyy-MM-dd");
		}
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
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
				item.setDescription(atts.getValue("abstract"));
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
		}
	
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length); 
		}
	
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equals("event")) {
				title = propMap.get("name");
			} else if (localName.equals("room")) {
				Schedule.Line tent = new Schedule.Line(propMap.get("id"), propMap.get("name"));
				tentMap.put(Integer.parseInt(tent.getId()), tent);
			} else if (localName.equals("area")) {
				areas.put(Integer.parseInt(propMap.get("id")), propMap.get("name"));
			} else if (localName.equals("response")) {
				merge();
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
		
		private void merge() {
			for (Line tent : tentMap.values()) {
				tents.add(tent);
			}
			for (RawItem item : rawItems) {
				LinkedList<String> speakers;
				if ((speakers = candidates.get(item.candidate)) != null) {
					for (String name : speakers)
						item.item.addSpeaker(name);
				}
				
				String area;
				if ((area = areas.get(item.area)) != null) {
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
			if (curDay == null)
				return items;
			
			Calendar dayStart = new GregorianCalendar();
			dayStart.setTime(curDay);
			
			TreeSet<Schedule.Item> ret = new TreeSet<Schedule.Item>();
			Iterator<Schedule.Item> itemi = items.iterator();
			while (itemi.hasNext()) {
				Schedule.Item item = itemi.next();
				if (item.getStartTime().after(dayStart.getTime()) &&
					item.getEndTime().before(curDayEnd))
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
		private int stars;
		private boolean newData;
		
		Item(String id_, String title_, Date startTime_, Date endTime_) {
			id = id_;
			title = title_;
			startTime = startTime_;
			endTime = endTime_;
			
			remind = false;
			stars = -1;
			newData = false;
		}
		
		public Schedule getSchedule() {
			return Schedule.this;
		}
		
		public void setTrack(String track_) {
			track = track_;
		}
		
		public void setDescription(String description_) {
			description = description_;
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
	}

	public class LinkType {
		private String id;
		private Drawable iconDrawable;
		
		public LinkType(String id_) {
			id = id_;
			iconDrawable = app.getResources().getDrawable(R.drawable.browser_small);
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

	/* I want to keep local cached copies of schedule files. This reader makes that easy. */
	private class TeeReader extends BufferedReader {
		Writer writer;
		boolean waiting;
		
		public TeeReader(Reader in, Writer out, int buf) {
			super(in, buf);
			writer = out;
		}
		
		@Override
		public void mark(int limit) throws IOException {
			super.mark(limit);
			waiting = true;
		}
		
		@Override
		public void reset() throws IOException {
			super.reset();
			waiting = false;
		}

		@Override
		public int read(char[] buf, int off, int len) throws IOException {
			int st = super.read(buf, off, len);
			if (writer != null && !waiting && st > 0) {
				writer.write(buf, off, st);
			}
			return st;
		}
		
		@Override
		public void close() throws IOException {
			super.close();
			writer.close();
		}
	}
}
