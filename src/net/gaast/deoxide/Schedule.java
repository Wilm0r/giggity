package net.gaast.deoxide;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.Xml;

public class Schedule {
	private Deoxide app;
	private DeoxideDb.Connection db;
	
	private String id;
	private String title;

	private LinkedList<Schedule.Line> tents;
	private HashMap<String,Schedule.LinkType> linkTypes;
	private HashMap<String,Schedule.Item> allItems;

	private Date firstTime, lastTime;
	
	private boolean fullyLoaded;
	
	public Schedule(Activity ctx) {
		app = (Deoxide) ctx.getApplication();
	}
	
	public void loadDeox(String source) throws LoadNetworkException, LoadDataException {
		loadXml(source, new DeoxParser());
	}
	
	public void loadXcal(String source) throws LoadNetworkException, LoadDataException {
		loadXml(source, new XcalParser());
	}
	
	public void loadXml(String source, ContentHandler parser) throws LoadNetworkException, LoadDataException {
		BufferedReader in;
		
		id = null;
		title = null;
		
		allItems = new HashMap<String,Schedule.Item>();
		linkTypes = new HashMap<String,Schedule.LinkType>();
		tents = new LinkedList<Schedule.Line>();
		
		firstTime = null;
		lastTime = null;
		
		fullyLoaded = false;
		
		try {
			URL dl = new URL(source);
			in = new BufferedReader(new InputStreamReader(dl.openStream()));
			try {
				Xml.parse(in, parser);
			} catch (Exception e) {
				Log.e("Schedule.loadXml", "XML parse exception: " + e);
				e.printStackTrace();
				throw new LoadDataException();
			}
		} catch (Exception e) {
			Log.e("Schedule.loadXml", "Exception while downloading schedule: " + e);
			e.printStackTrace();
			throw new LoadNetworkException();
		}
		
		db = app.getDb();
		db.setSchedule(this);
		
		/* From now, changes should be marked to go back into the db. */
		fullyLoaded = true;
	}
	
	public class LoadDataException extends Exception {
	}

	public class LoadNetworkException extends Exception {
	}

	public void commit() {
		Iterator<Item> it = allItems.values().iterator();
		
		while (it.hasNext()) {
			Item item = it.next();
			item.commit();
		}
	}
	
	public String getId() {
		return id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public LinkedList<Schedule.Line> getTents() {
		return tents;
	}
	
	public Date getFirstTime() {
		return firstTime;
	}
	
	public Date getLastTime() {
		return lastTime;
	}
	
	public Schedule.LinkType getLinkType(String id) {
		return linkTypes.get(id);
	}
	
	public Item getItem(String id) {
		return allItems.get(id);
	}
	
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
					
					if (firstTime == null || startTime.before(firstTime))
						firstTime = startTime;
					if (lastTime == null || endTime.after(lastTime))
						lastTime = endTime;
					
					curItem = new Schedule.Item(atts.getValue("", "id"),
		                       atts.getValue("", "title"),
		                       startTime, endTime);
	//			} catch (ParseException e) {
	//				Log.e("XML", "Error while trying to parse a date");
				} catch (NumberFormatException e) {
					
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

		SimpleDateFormat df;

		public XcalParser() {
			tentMap = new HashMap<String,Schedule.Line>();
			df = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
			//df.setTimeZone(TimeZone.getTimeZone("UTC"));
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
				
				Log.d("Event:", eventData.get("summary") + " in " + eventData.get("location"));
				
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

				if (firstTime == null || startTime.before(firstTime))
					firstTime = startTime;
				if (lastTime == null || endTime.after(lastTime))
					lastTime = endTime;

				item = new Schedule.Item(uid, name, startTime, endTime);
				
				if ((s = eventData.get("description")) != null) {
					item.setDescription(s);
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
	
	public class Line {
		private String id;
		private String title;
		private LinkedList<Schedule.Item> items;
		
		public Line(String id_, String title_) {
			id = id_;
			title = title_;
			items = new LinkedList<Schedule.Item>();
		}
		
		public String getId() {
			return id;
		}
		
		public String getTitle() {
			return title;
		}
		
		public void addItem(Schedule.Item item) {
			items.add(item);
			allItems.put(item.getId(), item);
		}
		
		public LinkedList<Schedule.Item> getItems() {
			return items;
		}
	}
	
	public class Item {
		private String id;
		private String title;
		private String description;
		private Date startTime, endTime;
		private LinkedList<Schedule.Item.Link> links;
		
		private boolean remind;
		private boolean newData;
		
		Item(String id_, String title_, Date startTime_, Date endTime_) {
			id = id_;
			title = title_;
			startTime = startTime_;
			endTime = endTime_;
			
			remind = false;
			newData = false;
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
		
		public LinkedList<Schedule.Item.Link> getLinks() {
			return links;
		}

		public void setRemind(boolean remind_) {
			if (remind != remind_) {
				remind = remind_;
				newData |= fullyLoaded;
			}
		}
		
		public boolean getRemind() {
			return remind;
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

	}

	public class LinkType {
		private String id;
		private Drawable iconDrawable;
		
		public LinkType(String id_) {
			id = id_;
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
}
