package net.gaast.deoxide;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.util.Log;
import android.util.Xml;

public class ScheduleData implements ContentHandler {
	private Deoxide app;
	
	private String id;
	private String title;

	private LinkedList<ScheduleDataLine> tents;
	private HashMap<String,ScheduleDataLinkType> linkTypes;

	private Date firstTime, lastTime;
	
	private ScheduleDataLine curTent;
	private ScheduleDataItem curItem;
	private String curString;
	
	public ScheduleData(Deoxide app_, String source) {
		app = app_;
		
		linkTypes = new HashMap<String,ScheduleDataLinkType>();
		
		Log.i("ScheduleData", "About to start parsing");
		tents = new LinkedList<ScheduleDataLine>();
		try {
			URL dl = new URL(source);
			BufferedReader in = new BufferedReader(new InputStreamReader(dl.openStream()));
			Xml.parse(in, this);
		} catch (Exception e) {
			Log.e("XML", "XML parse exception: " + e);
			e.printStackTrace();
		}
	}
	
	public String getId() {
		return id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public LinkedList<ScheduleDataLine> getTents() {
		return tents;
	}
	
	public Date getFirstTime() {
		return firstTime;
	}
	
	public Date getLastTime() {
		return lastTime;
	}
	
	public ScheduleDataLinkType getLinkType(String id) {
		return linkTypes.get(id);
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		curString += String.copyValueOf(ch, start, length); 
		// TODO Auto-generated method stub
		// Log.i("XML", "" + arg2 + " characters " + new String(arg0));
	}

	@Override
	public void endDocument() throws SAXException {
		Log.d("XML", "endDocument");
	}

	@Override
	public void endPrefixMapping(String arg0) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
			throws SAXException {
		// TODO Auto-generated method stub
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
		Log.d("XML", "startDocument");
	}

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
			
			ScheduleDataLinkType lt = new ScheduleDataLinkType(id);
			if (icon != null)
				lt.setIconUrl(icon);
			
			linkTypes.put(id, lt);
		} else if (localName == "line") {
			curTent = new ScheduleDataLine(atts.getValue("", "id"),
					                       atts.getValue("", "title"));
		} else if (localName == "item") {
			SimpleDateFormat df;
			Date startTime, endTime;

			//Log.d("XML", "itemRaw: " + atts.getValue("", "id") + " " + atts.getValue("", "title") +
			//	      " " + atts.getValue("", "startTime") + " " + atts.getValue("", "endTime"));

			try {
				/*
				df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				df.setTimeZone(TimeZone.getTimeZone("UTC"));
				startTime = df.parse(atts.getValue("", "startTime"));
				endTime = df.parse(atts.getValue("", "endTime"));
				*/
				
				startTime = new Date(Long.parseLong(atts.getValue("", "startTime")) * 1000);
				endTime = new Date(Long.parseLong(atts.getValue("", "endTime")) * 1000);
				
				if (firstTime == null || startTime.before(firstTime))
					firstTime = startTime;
				if (lastTime == null || endTime.after(lastTime))
					lastTime = endTime;
				
//				Log.d("XML", "itemParsed: " + atts.getValue("", "id") + " " + atts.getValue("", "title") +
//				      " " + startTime + " " + endTime);

				curItem = new ScheduleDataItem(atts.getValue("", "id"),
	                       atts.getValue("", "title"),
	                       startTime, endTime);
//			} catch (ParseException e) {
//				Log.e("XML", "Error while trying to parse a date");
			} catch (NumberFormatException e) {
				
			}
		} else if (localName == "itemLink") {
			ScheduleDataLinkType lt = linkTypes.get(atts.getValue("", "type"));
			curItem.addLink(lt, atts.getValue("", "href"));
		}
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
	public void startPrefixMapping(String arg0, String arg1)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}
}
