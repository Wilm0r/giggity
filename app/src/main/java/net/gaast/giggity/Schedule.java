/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2021 Wilmer van der Gaast <wilmer@gaast.net>
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

import android.content.Context;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.widget.CheckBox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class Schedule implements Serializable {
	private final int detectHeaderSize = 1024;
	
	private String url;
	private String title;

	private LinkedList<Schedule.Line> tents = new LinkedList<>();
	protected HashMap<String,Schedule.Item> allItems = new HashMap<>();
	protected HashMap<String,String> cIdMap = new HashMap<>();
	private Collator trackSort;
	private SortedMap<String,Track> tracks;

	private ZonedDateTime firstTime, lastTime;
	private ZonedDateTime dayFirstTime, dayLastTime;  // equal to full schedule bounds (so spanning multiple days) if day = -1
	private int curDayNum;
	private ZonedDateTime curDay, curDayEnd;          // null if curDayNum = -1
	// For internal use, *exact* hour boundaries (day-before for some day change + tz offset combs)
	private LinkedList<ZonedDateTime> dayList = new LinkedList<>();
	// For external use, dates only
	private LinkedList<ZonedDateTime> day0List = new LinkedList<>();
	private boolean showHidden;  // So hidden items are shown but with a different colour.

	private ZoneId inTZ = ZoneId.systemDefault();   // TZ-less/UTC times to be interpreted as/converted to this.
	private ZoneId outTZ = ZoneId.systemDefault();  // Usually our local timezone, this is returned externally.
	private LocalTime dayChange = LocalTime.of(6, 0);

	private HashSet<String> languages = new HashSet<>();

	/* Misc. data not in the schedule file but from Giggity's menu.json. Though it'd certainly be
	 * nice if some file formats could start supplying this info themselves. */
	private String icon;
	private LinkedList<Link> links;
	protected String roomStatusUrl;

	protected boolean fullyLoaded;

	public Schedule() {
		trackSort = Collator.getInstance();
		trackSort.setStrength(Collator.PRIMARY);
		tracks = new TreeMap<>(trackSort);
	}

	public void loadSchedule(BufferedReader in, String url_) throws IOException, LoadException {
		url = url_;
		char[] headc = new char[detectHeaderSize];

		/* Read the first KByte (but keep it buffered) to try to detect the format. */
		in.mark(detectHeaderSize);
		in.read(headc, 0, detectHeaderSize);
		in.reset();

		String head = new String(headc).toLowerCase();

		/* Yeah, I know this is ugly, and actually reasonably fragile. For now it
		 * just seems somewhat more efficient than doing something smarter, and
		 * I want to avoid doing XML-specific stuff here already. */
		if (head.contains("<icalendar") && head.contains("<vcalendar")) {
			loadXcal(in);
		} else if (head.contains("<schedule") && head.contains("<conference")) {
			loadPentabarf(in);
		} else if (head.contains("begin:vcalendar")) {
			loadIcal(in);
		} else {
			Log.d("head", head);
			throw new LoadException(getString(R.string.format_unknown));
		}

		Log.d("load", "Schedule has " + languages.size() + " languages");

		if (title == null)
			title = url;

		if (allItems.size() == 0) {
			throw new LoadException(getString(R.string.schedule_empty));
		}

		ZonedDateTime day = firstTime.truncatedTo(ChronoUnit.DAYS).with(dayChange);
		/* Add a day 0 (maybe there's an event before the first day officially
		 * starts?). Saw this in the CCC Fahrplan for example. */
		if (day.isAfter(firstTime))
			day = day.minusDays(1);

		ZonedDateTime dayEnd = day.plusDays(1);

		dayList = new LinkedList<>();
		while (day.isBefore(lastTime)) {
			/* Some schedules have empty days in between. :-/ Skip those. */
			for (Schedule.Item item : allItems.values()) {
				if (item.startTime.compareTo(day) >= 0 &&
				    item.endTime.compareTo(dayEnd) <= 0) {
					// Exact start time of day (could be "yesterday")
					dayList.add(day);
					// Midnight date-only for display purpose.
					day0List.add(day.truncatedTo(ChronoUnit.DAYS));
					break;
				}
			}
			day = dayEnd;
			dayEnd = dayEnd.plusDays(1);
		}
	}

	public String getString(int id) {
		// To be overridden by ScheduleUI, or ignored otherwise?
		return "String id=" + id;
	}

	// Think I'll use this one for stand-alone when the R class doesn't exist?
	public String getString(String id) {
		return id;
	}

	static public class LoadException extends RuntimeException {
		public LoadException(String description) {
			super(description);
		}

		// Slightly cleaner without the "FooException:" prefix for ones that are custom anyway.
		public String toString() {
			return getMessage();
		}
	}

	static public class LateException extends LoadException {
		public LateException() {
			super("LoadException. This thread has lost the race.");
		}
	}

	public static String hashify(String url) {
		String ret = "";
		try {
			/* md5, sha1... small diff I guess? (No clue how this evolved!) */
			MessageDigest md5 = MessageDigest.getInstance("SHA-1");
			md5.update(url.getBytes());
			byte raw[] = md5.digest();
			for (int i = 0; i < raw.length; i ++)
				ret += String.format("%02x", raw[i]);
		} catch (NoSuchAlgorithmException e) {
			// WTF mate
		}
		return ret;
	}
	
	public LinkedList<ZonedDateTime> getDays() {
		return day0List;
	}
	
	/** Total duration of this event in seconds. */
	public long eventLength() {
		return lastTime.toEpochSecond() - firstTime.toEpochSecond();
	}

	public void setInTZ(ZoneId inTZ) {
		if (fullyLoaded) {
			throw new RuntimeException("Can't change inTZ after loading.");
		}

		this.inTZ = inTZ;
	}

	public void setOutTZ(ZoneId outTZ) {
		this.outTZ = outTZ;
	}

	public int getDayNum() {
		return curDayNum;
	}
	
	// Returns DATE in EVENT timezone. Don't use for anything other than date display purposes.
	public ZonedDateTime setDay(int day) {
		if (day == -1) {
			curDayNum = day;
			curDay = curDayEnd = null;
			dayFirstTime = firstTime;
			dayLastTime = lastTime;
			return null;
		} else {
			curDayNum = day % dayList.size();
			curDay = dayList.get(curDayNum);
			curDayEnd = curDay.plusDays(1);

			dayFirstTime = dayLastTime = null;
			for (Schedule.Item item : allItems.values()) {
				if (item.startTime.compareTo(curDay) >= 0 &&
						item.endTime.compareTo(curDayEnd) <= 0) {
					if (dayFirstTime == null || item.startTime.isBefore(dayFirstTime))
						dayFirstTime = item.startTime;
					if (dayLastTime == null || item.endTime.isAfter(dayLastTime))
						dayLastTime = item.endTime;
				}
			}

			return day0List.get(curDayNum);
		}
	}

	public double getTzDiff() {
		// Calculate difference *now* if the event is current, otherwise at the start of the conf.
		// (Just in case there's a DST change on one of the sides mid-event?)
		ZonedDateTime mp = ZonedDateTime.now().withZoneSameInstant(inTZ);
		if (!isToday()) {
			mp = firstTime;
		}
		int diff = inTZ.getRules().getOffset(mp.toInstant()).getTotalSeconds() -
		           outTZ.getRules().getOffset(mp.toInstant()).getTotalSeconds();
		return diff / 3600.0;
	}

	/* Sets day to one overlapping given moment in time and returns day number, or -1 if no match. */
	public int setDay(ZonedDateTime now) {
		int i = 0;
		for (ZonedDateTime day : dayList) {
			ZonedDateTime dayEnd = day.plusDays(1);
			if (day.isBefore(now) && dayEnd.isAfter(now)) {
				setDay(i);
				return i;
			}
			i ++;
		}
		return -1;
	}

	public DateTimeFormatter getDayFormat() {
		if (eventLength() > (86400 * 5))
			return DateTimeFormatter.ofPattern("EE d MMMM");
		else
			return DateTimeFormatter.ofPattern("EE");
	}
	
	/** Get earliest item.startTime */
	public ZonedDateTime getFirstTimeZoned() {
		if (curDay == null) {
			return firstTime.withZoneSameInstant(outTZ);
		} else {
			return dayFirstTime.withZoneSameInstant(outTZ);
		}
	}
	
	/** Get highest item.endTime */
	public ZonedDateTime getLastTimeZoned() {
		if (curDay == null) {
			return lastTime.withZoneSameInstant(outTZ);
		} else {
			return dayLastTime.withZoneSameInstant(outTZ);
		}
	}

	public Date getFirstTime() {
		return Date.from(getFirstTimeZoned().toInstant());
	}

	public Date getLastTime() {
		return Date.from(getLastTimeZoned().toInstant());
	}

	public boolean isToday() {
		ZonedDateTime now = ZonedDateTime.now();
		return (getFirstTimeZoned().isBefore(now) && getLastTimeZoned().isAfter(now));
	}
	
	private void loadXcal(BufferedReader in) {
		loadXml(in, new XcalParser());
	}
	
	private void loadPentabarf(BufferedReader in) {
		loadXml(in, new PentabarfParser());
	}
	
	private void loadXml(BufferedReader in, ContentHandler parser) {
		try {
			XMLReader reader = XMLReaderFactory.createXMLReader();
			reader.setContentHandler(parser);
			reader.parse(new InputSource(in));
			in.close();
		} catch (Exception e) {
			Log.e("Schedule.loadXml", "XML parse exception: " + e);
			e.printStackTrace();
			throw new LoadException("XML parsing problem: " + e);
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
		int lnum = -1;  // Note: Code below around line continuation results in "somewhat" funky flow and lnum value!
		// Also, I've noticed that the JRES schedule (see jres_2022.ics test file) has spurious CRCRLF (yes, double CR) newlines which messes up the line number counting but meh.
		try {
			line = "";
			while (true) {
				++lnum;
				s = in.readLine();
				//Log.d("icalRead", s);
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
						key = null;
						AttributesImpl at = new AttributesImpl();
						for (String bit : split[0].split(";")) {
							if (key == null) {
								key = bit.toLowerCase();
								continue;
							}
							String kv[] = bit.split("=", 2);
							if (kv.length == 2) {
								at.addAttribute("", kv[0].toLowerCase(), kv[0].toLowerCase(), "", kv[1]);
							}
						}
						value = value.replace("\\n", "\n").replace("\\,", ",")
						             .replace("\\;", ";").replace("\\\\", "\\");
						/* Fake <key>value</key> */
						p.startElement("", key, "", at);
						p.characters(value.toCharArray(), 0, value.length());
						p.endElement("", key, "");
					}
				} else if (lnum > 0){
					// Log.d("ical", "No clue what to do with line " + lnum + ": " + s);
				}
				if (s != null)
					line = s;
				else
					break;
			}
		} catch (IOException|SAXException|NullPointerException e) {
			e.printStackTrace();
			throw new LoadException("Read error at line " + lnum + ": " + e);
		}
	}

	/** OOB metadata related to schedule but separately supplied by Giggity (it's non-standard) gets merged here.
	  I should see whether I could get support for this kind of data into the Pentabarf format. */
	protected void addMetadata(String md_json) {
		if (md_json == null)
			return;
		try {
			JSONObject md;
			md = new JSONObject(md_json);
			if (md.has("icon")) {
				icon = md.getString("icon");
			}
			if (md.has("links")) {
				links = new LinkedList<>();
				JSONArray linklist = md.getJSONArray("links");
				for (int i = 0; i < linklist.length(); ++i) {
					JSONObject link = linklist.getJSONObject(i);
					Schedule.Link slink = new Link(link.getString("url"), link.getString("title"));
					slink.setType(link.optString("type", null));
					links.addLast(slink);
				}
			}
			if (md.has("rooms")) {
				JSONArray roomlist = md.getJSONArray("rooms");
				for (int i = 0; i < roomlist.length(); ++i) {
					JSONObject jroom = roomlist.getJSONObject(i);
					for (Line room : getTents()) {
						if (room.location != null) {
							// Guess I could allow overlapping regexes starting with more specific
							// ones. So if we've already assigned a location w/ a previous regex,
							// skip this room.
							continue;
						}
						// Using regex matching here to be a little more fuzzy. Also, possibly rooms
						// that are close to each other (and may have similar names) can just share
						// one entry.
						if (!room.getTitle().matches(jroom.getString("name"))) {
							continue;
						}
						if (jroom.has("c3nav_slug")) {
							room.location = md.getString("c3nav_base") + "/l/" +
									jroom.getString("c3nav_slug");
						} else {
							String name;
							if (jroom.has("show_name")) {
								name = jroom.getString("show_name") + " (" + room.getTitle() + ")";
							} else {
								name = room.getTitle();
							}
							JSONArray latlon = jroom.getJSONArray("latlon");
							try {
								room.location = ("geo:0,0?q=" + latlon.optDouble(0, 0) + "," +
										latlon.optDouble(1, 0) + "(" +
										URLEncoder.encode(name, "utf-8") + ")");
							} catch (UnsupportedEncodingException e) {
								// I'm a useless language! (Have I mentioned yet how if a machine
								// doesn't do utf-8 then it should maybe not be on the Internet?)
							}
						}
					}
				}
			}
			if (md.has("fosdemRoomStatus")) {
				roomStatusUrl = md.getString("fosdemRoomStatus");
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return;
		}
	}

	public void commit() {
		Log.d("Schedule", "Saving all changes to the database");
		for (Schedule.Item item : allItems.values()) {
			item.commit();
		}
	}

	public String getUrl() {
		return url;
	}
	
	public String getTitle() {
		return title;
	}
	
	public Collection<Schedule.Line> getTents() {
		ArrayList<Line> ret = new ArrayList<>();
		for (Line line : tents) {
			if (line.getItems().size() > 0)
				ret.add(line);
		}
		
		return ret;
	}
	
	public Item getItem(String id) {
		return allItems.get(id);
	}

	public String getCId(String id) {
		return cIdMap.get(id);
	}

	public Collection<Track> getTracks() {
		if (tracks == null || tracks.size() == 0)
			return null;

		ArrayList<Track> ret = new ArrayList<>();
		for (Track e : tracks.values()) {
			if (e.getItems().size() > 0) {
				ret.add(e);
			}
		}

		return ret;
	}

	public ArrayList<Item> getByLanguage(String language) {
		ArrayList<Item> ret = new ArrayList<>();
		for (Item item : allItems.values()) {
			if (item.getLanguage() != null && item.getLanguage().equals(language)) {
				ret.add(item);
			}
		}
		return ret;
	}
	
	public AbstractList<Item> searchItems(String q_) {
		return null;
	}

	public LinkedList<Link> getLinks() {
		return links;
	}

	public Collection<String> getLanguages() { return languages; }

	public void setShowHidden(boolean showHidden) {
		this.showHidden = showHidden;
	}

	public boolean getShowHidden() {
		return showHidden;
	}

	public String getIconUrl() {
		return icon;
	}

	public boolean hasRoomStatus() {
		return roomStatusUrl != null;
	}

	/* Returns true if any of the statuses has changed. */
	public boolean updateRoomStatus(String json) {
		boolean ret = false;
		JSONArray parsed;
		try {
			parsed = new JSONArray(json);
			/* Easier lookup */
			HashMap<String, JSONObject> lu = new HashMap<>();
			for (int i = 0; i < parsed.length(); ++i) {
				JSONObject e = parsed.getJSONObject(i);
				lu.put(e.getString("roomname"), e);
			}
			for (Line l : getTents()) {
				if (!lu.containsKey(l.getTitle())) {
					continue;
				}
				JSONObject e = lu.get(l.getTitle());
				RoomStatus st = RoomStatus.UNKNOWN;
				switch (e.optInt("state", -1)) {
					case 0:
						st = RoomStatus.OK;
						break;
					case 1:
						st = RoomStatus.FULL;
						break;
					case 2:
						st = RoomStatus.EVACUATE;
						break;
				}
				ret |= l.setRoomStatus(st);
			}
		} catch (JSONException e) {
			Log.d("updateRoomStatus", "JSON parse failure");
			e.printStackTrace();
			return false;
		}
		return ret;
	}

	protected void applyItem(Item item) {
		// To be implemented by ScheduleUI only, for updating internal/peristent app state on for
		// example reminders, etc.
	}

	private class XcalParser implements ContentHandler {
		private HashMap<String,Schedule.Line> tentMap;
		private HashMap<String,String> eventData;
		private HashMap<String,Attributes> eventDataAttr;
		private String curString;

		DateTimeFormatter dfUtc, dfLocal;

		public XcalParser() {
			tentMap = new HashMap<String,Schedule.Line>();

			dfUtc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));
			dfLocal = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(inTZ);
		}

		private ZonedDateTime parseTime(String s, String tz) throws ParseException {
			TemporalAccessor ret;
			try {
				ret = dfUtc.parse(s, new ParsePosition(0));
				return ZonedDateTime.from(ret).withZoneSameInstant(inTZ);
			} catch (DateTimeParseException e) {
				if (tz == null) {
					ret = dfLocal.parse(s, new ParsePosition(0));
				} else {
					ret = dfLocal.withZone(ZoneId.of(tz)).parse(s, new ParsePosition(0));
				}
				return ZonedDateTime.from(ret);
			}
		}

		/* Yay I'll just write my own parser... Spec is at https://www.kanzaki.com/docs/ical/duration-t.html
		   Don't feel like importing a non-GPL library for just this. Also, returning an int (seconds) instead
		   of some kind of timedelta since the Java/Android version I'm targeting (<8?) doesn't have one yet.
		 */
		private Duration parseDuration(String durSpec) {
			int ret = 0;
			Matcher m = Pattern.compile("(\\d+)([WDHMS])").matcher(durSpec);
			while (m.find()) {
				int bit = Integer.parseInt(m.group(1));
				/* break missing intentionally everywhere below. You'll see why. */
				switch (m.group(2)) {
					case "W":
						bit *= 7;
					case "D":
						bit *= 24;
					case "H":
						bit *= 60;
					case "M":
						bit *= 60;
				}
				ret += bit;
			}
			return Duration.ofSeconds(ret);
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("vevent")) {
				eventData = new HashMap<>();
				eventDataAttr = new HashMap<>();
			} else {
				if (atts != null && atts.getLength() > 0 && eventDataAttr != null) {
					eventDataAttr.put(localName, atts);
				}
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
				String uid, name, location, startTimeS, endTimeS, durationS = null, s;
				ZonedDateTime startTime, endTime;
				Schedule.Item item;
				Schedule.Line line;
				
				if ((uid = eventData.get("uid")) == null ||
				    (name = eventData.get("summary")) == null ||
				    (location = eventData.get("location")) == null ||
				    (startTimeS = eventData.get("dtstart")) == null ||
				    ((endTimeS = eventData.get("dtend")) == null &&
				     (durationS = eventData.get("duration")) == null)) {
					Log.w("Schedule.loadXcal", "Invalid event, some attributes are missing. Have: " + String.join(", ", eventData.keySet()));
					return;
				}
				
				try {
					Attributes at = eventDataAttr.get("dtstart");
					String tz = null;
					if (at != null) {
						tz = at.getValue("tzid");
					}

					startTime = parseTime(startTimeS, tz);
					if (durationS != null) {
						endTime = startTime.plus(parseDuration(durationS));
					} else {
						// If dtend has a different tz then you're a terrible person.
						endTime = parseTime(endTimeS, tz);
					}
				} catch (ParseException e) {
					Log.w("Schedule.loadXcal", "Can't parse date: " + e);
					return;
				}

				item = new Schedule.Item(uid, name, startTime, endTime);
				
				if ((s = eventData.get("description")) != null) {
					item.setDescription(s);
				}
				
				if ((s = eventData.get("url")) != null) {
					item.addLink(new Link(s));
				}

				if ((line = tentMap.get(location)) == null) {
					line = new Schedule.Line(location);
					tents.add(line);
					tentMap.put(location, line);
				}
				line.addItem(item);
				
				eventData = null;
			} else if (localName.equals("x-wr-calname")) {
				title = curString;
			} else if (localName.equals("x-wr-caldesc")) {
				// Fall back to this field if necessary, calname is likely more suitable (brief)
				if (title == null) {
					title = curString;
				}
			} else if (eventData != null) {
				eventData.put(localName, curString);
			}
		}

		@Override
		public void setDocumentLocator(Locator locator) {}

		@Override
		public void startDocument() throws SAXException {}

		@Override
		public void endDocument() throws SAXException {}

		@Override
		public void startPrefixMapping(String s, String s1) throws SAXException {}

		@Override
		public void endPrefixMapping(String s) throws SAXException {}

		@Override
		public void ignorableWhitespace(char[] chars, int i, int i1) throws SAXException {}

		@Override
		public void processingInstruction(String s, String s1) throws SAXException {}

		@Override
		public void skippedEntity(String s) throws SAXException {}
	}

	/* Pentabarf, the old conference organisation tool has a pretty excellent native XML format
	   and is now the preferred file format. http://pentabarf.org/Main_Page
	   It's not really maintained anymore though, a recent fork called Frab is more maintained and
	   Giggity can read its XML exports just as well https://github.com/frab/frab
	 */
	private class PentabarfParser implements ContentHandler {
		private Schedule.Line curTent;
		private HashMap<String,Schedule.Line> tentMap;
		private HashMap<String,String> propMap;
		private String curString;
		private LinkedList<String> persons;
		private LinkedList<Link> links;
		private LocalDate curDay;

		private DateTimeFormatter df, tf, zdf;

		public PentabarfParser() {
			tentMap = new HashMap<>();

			df = DateTimeFormatter.ISO_LOCAL_DATE;
			// tf = DateTimeFormatter.ISO_LOCAL_TIME;  // Nope, won't take the optional seconds. :<
			tf = DateTimeFormatter.ofPattern("H:mm[:ss]");

			// zoned date+time format in the <date/> tag, not used by all schedules BUT the only one
			// that may have tz awareness... (Used by several schedules yet for example not FOSDEM.
			zdf = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("conference") || localName.equals("event")) {
				propMap = new HashMap<String,String>();

				if (atts.getValue("id") != null)
					propMap.put("id", atts.getValue("id"));
				if (atts.getValue("guid") != null)
					propMap.put("guid", atts.getValue("guid"));

				links = new LinkedList<>();
				persons = new LinkedList<>();
			} else if (localName.equals("day")) {
				curDay = LocalDate.parse(atts.getValue("date"), df);
				// TODO: PARSE ERROR?
			} else if (localName.equals("room")) {
				String name = atts.getValue("name");
				Schedule.Line line;
				
				if (name == null)
					return;
				
				if ((line = tentMap.get(name)) == null) {
					line = new Schedule.Line(name);
					tents.add(line);
					tentMap.put(name, line);
				}
				curTent = line;
			} else if (localName.equals("link") && links != null) {
				String href = atts.getValue("href");
				if (href != null)
					links.add(new Link(href));
			}
		}
	
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length); 
		}
		
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			curString = curString.trim(); // D:
			if (localName.equals("conference")) {
				title = propMap.get("title");
				if (propMap.get("day_change") != null) {
					dayChange = LocalTime.parse(propMap.get("day_change"), tf);
				}
				if (propMap.get("time_zone_name") != null) {
					ZoneId fTZ = ZoneId.of(propMap.get("time_zone_name"));
					if (!fTZ.normalized().equals(inTZ.normalized())) {
						Log.w("ScheduleTZ", "In-file timezone " + fTZ + " seems to mismatch our records: " + inTZ);
					}
					inTZ = fTZ;
				}
			} else if (localName.equals("event")) {
				String id = null, guid = null;
				String title, startTimeS, startZonedTimeS, durationS, s, desc, wl;
				ZonedDateTime startTime, endTime;
				Schedule.Item item;

				startTimeS = propMap.get("start");
				startZonedTimeS = propMap.get("date");
				id = propMap.get("id");
				guid = propMap.get("guid");
				if ((id == null && guid == null) ||
				    (title = propMap.get("title")) == null ||
				    (startTimeS == null && startZonedTimeS == null) ||
				    (durationS = propMap.get("duration")) == null) {
					Log.w("Schedule.loadPentabarf", "Invalid event, some attributes are missing.");
					return;
				}

				startTime = null;
				try {
					if (startZonedTimeS != null) {
						// All internal timestamps must be the tz-native times, in the conf's zone
						startTime = ZonedDateTime.parse(startZonedTimeS, zdf);
					}
				} catch (DateTimeParseException e){
					startZonedTimeS = null;
				}
				if (startZonedTimeS == null) {
					LocalTime rawTime = LocalTime.parse(startTimeS, tf);
					startTime = ZonedDateTime.of(curDay, rawTime, inTZ);

					if (rawTime.isBefore(dayChange)) {
						// In Frab files, if a time is before day_change it's after midnight, thus
						// date should be incremented by one. (Not needed when using zoned *full*
						// timestamp above.)
						startTime = startTime.plusDays(1);
					}
				}

				LocalTime rawTime = LocalTime.parse(durationS, tf);
				endTime = startTime.plusHours(rawTime.getHour()).plusMinutes(rawTime.getMinute());

				String cid = null;  // canonical ID. This file format has been, hm, evolving?
				if (guid != null) {
					cid = guid;
					if (id != null) {
						String prev = cIdMap.put(id, guid);
						if (prev != null) {
							Log.i("Schedule.loadPentabarf", "Schedule contains duplicate event id=" +
									     id + " used by both guid=" + prev + " and guid=" + guid);
						}
					}
				} else if (id != null) {
					// FOSDEM still uses just these, as do a few others. :(
					cid = id;
					if (allItems.get(id) != null) {
						Log.e("Schedule.loadPentabarf", "Schedule contains duplicate event id=" + id + ", and does NOT provide GUIDs for deduplication!");
					}
				}

				item = new Schedule.Item(cid, title, startTime, endTime);
				
				if ((s = propMap.get("subtitle")) != null) {
					if (!s.isEmpty())
						item.setSubtitle(s);
				}
				
				if ((wl = propMap.get("url")) != null) {
					if (!wl.isEmpty())
						item.setWebLink(wl);
				}
				
				desc = "";
				// TODO: IMHO the separation between these two is not used in a meaningful way my most,
				// or worse, description is just a copy of abstract. Some heuristics would be helpful.
				if ((s = propMap.get("abstract")) != null &&
				    (!propMap.containsKey("description") ||
				     !Giggity.fuzzyStartsWith(propMap.get("abstract"), propMap.get("description")))) {
					desc += s.trim() + "\n\n";
				}
				if ((s = propMap.get("description")) != null) {
					desc += s;
				}
				item.setDescription(desc);
				
				if ((s = propMap.get("track")) != null && !s.equals("")) {
					item.setTrack(s);
				}
				for (Link i : links)
					item.addLink(i);
				for (String i : persons)
					item.addSpeaker(i);

				String lang = propMap.get("language");
				if (lang != null && !lang.isEmpty()) {
					Locale loc = new Locale(lang);
					item.setLanguage(loc.getDisplayLanguage());
				}

				curTent.addItem(item);
				propMap = null;
				links = null;
				persons = null;
			} else if (localName.equals("person")) {
				persons.add(curString);
			} else if (localName.equals("link")) {
				String title = curString.trim();
				if (!links.isEmpty() && !title.isEmpty()) {
					links.getLast().setTitle(title);
				}
			} else if (propMap != null) {
				propMap.put(localName, curString);
			}
		}

		@Override
		public void setDocumentLocator(Locator locator) {}

		@Override
		public void startDocument() throws SAXException {}

		@Override
		public void endDocument() throws SAXException {}

		@Override
		public void startPrefixMapping(String s, String s1) throws SAXException {}

		@Override
		public void endPrefixMapping(String s) throws SAXException {}

		@Override
		public void ignorableWhitespace(char[] chars, int i, int i1) throws SAXException {}

		@Override
		public void processingInstruction(String s, String s1) throws SAXException {}

		@Override
		public void skippedEntity(String s) throws SAXException {}
	}

	public enum RoomStatus {
		UNKNOWN,
		OK,
		FULL,  // Options from here will be rendered in red. Modify EventDialog if that's no longer okay.
		EVACUATE,
	};

	public class ItemList {
		protected String title;
		protected TreeSet<Schedule.Item> items;

		public ItemList(String title_) {
			title = title_;
			items = new TreeSet<Schedule.Item>();
		}

		public String getTitle() {
			return title;
		}

		protected void addItem(Schedule.Item item) {
			items.add(item);
		}

		public AbstractSet<Schedule.Item> getItems() {
			TreeSet<Schedule.Item> ret = new TreeSet<Schedule.Item>();

			for (Item item : items) {
				if ((!item.isHidden() || showHidden) &&
				    (curDay == null || (!item.startTime.isBefore(curDay) &&
				                        !item.endTime.isAfter(curDayEnd))))
					ret.add(item);
			}
			return ret;
		}
	}

	public class Line extends ItemList implements Serializable {
		private String location;  // geo: URL (set by metadata loader)
		private RoomStatus roomStatus;

		public Line(String title_) {
			super(title_);
			roomStatus = RoomStatus.UNKNOWN;
		}
		
		public String getTitle() {
			if (roomStatus == RoomStatus.FULL)
				return "⚠️" + title;  // warning sign
			else if (roomStatus == RoomStatus.EVACUATE)
				return "🚫" + title;  // prohibited sign
			else
				return title;
		}
		
		public void addItem(Schedule.Item item) {
			item.setLine(this);
			super.addItem(item);

			/* The rest really should be in the caller, but there are several callsites, one per parser. TODO. */
			allItems.put(item.getId(), item);

			if (firstTime == null || item.startTime.isBefore(firstTime))
				firstTime = item.startTime;
			if (lastTime == null || item.endTime.isAfter(lastTime))
				lastTime = item.endTime;

			if (item.getLanguage() != null) {
				languages.add(item.getLanguage());
			}
		}

		public void setLocation(String location) {
			this.location = location;
		}

		public String getLocation() {
			return location;
		}

		public boolean setRoomStatus(RoomStatus newSt) {
			boolean ret = newSt != roomStatus;
			roomStatus = newSt;
			return ret;
		}

		public RoomStatus getRoomStatus() {
			return roomStatus;
		}

		// Return Schedule.Line for this track, only if it's one and the same for all its items.
		public Track getTrack() {
			Track ret = null;
			for (Item it : getItems()) {
				if (ret == null) {
					ret = it.getTrack();
				} else if (ret != it.getTrack()) {
					return null;
				}
			}
			return ret;
		}
	}

	public class Track extends ItemList implements Comparable<Track>, Serializable {
		public Track(String title_) {
			super(title_);
		}

		// Return Schedule.Line for this track, only if it's one and the same for all its items.
		public Line getLine() {
			Line ret = null;
			for (Item it : getItems()) {
				if (ret == null) {
					ret = it.getLine();
				} else if (ret != it.getLine()) {
					return null;
				}
			}
			return ret;
		}

		@Override
		public int compareTo(Track other) {
			return trackSort.compare(getTitle(), other.getTitle());
		}
	}
	
	public class Item implements Comparable<Item>, Serializable {
		private String id;
		private Line line;
		private String title, subtitle;
		private Track track;
		private String description;
		private ZonedDateTime startTime, endTime;
		private LinkedList<Schedule.Link> links;
		private LinkedList<String> speakers;
		private String language;
		private String webLink;
		
		private boolean remind;
		private boolean hidden;
		private boolean newData;

		Item(String id_, String title_, ZonedDateTime startTime_, ZonedDateTime endTime_) {
			id = id_;
			title = title_;
			startTime = startTime_;
			endTime = endTime_;
		}

		@Override
		public int hashCode() {
			// No clue what the default version does but the numbers seem too low to me.
			// I'm using this for notification + alarm IDs now so use all 32 bits.
			try {
				MessageDigest md5 = MessageDigest.getInstance("MD5");
				md5.update(getUrl().getBytes());
				byte raw[] = md5.digest();
				return ByteBuffer.wrap(raw, 0, 4).getInt();
			} catch (NoSuchAlgorithmException e) {  // WTF no
				e.printStackTrace();
				return super.hashCode();
			}
		}
		
		public Schedule getSchedule() {
			return Schedule.this;
		}

		public void setTrack(String track_) {
			if (!tracks.containsKey(track_)) {
				tracks.put(track_, new Track(track_));
			}
			track = tracks.get(track_);
			track.addItem(this);
		}
		
		public void setDescription(String description_) {
			// Keep the trim pls k thx baibai!
			description = description_.trim();
		}

		public void addLink(Schedule.Link link) {
			if (links == null) {
				links = new LinkedList<Schedule.Link>();
			}
			for (Schedule.Link l : links)
				if (l.getUrl().equals(link.getUrl()))
					return;
			
			links.add(link);
		}
		
		public void addSpeaker(String name) {
			if (speakers == null) {
				speakers = new LinkedList<String>();
			}
			speakers.add(name);
		}

		public void setLanguage(String lang) {
			if (lang != null && !lang.isEmpty()) {
				language = lang;
			} else {
				language = null;
			}
		}
		
		public String getId() {
			return id;
		}
		
		public String getUrl() {
			return (getSchedule().getUrl() + "#" + getId()); 
		}

		public String getWebLink() {
			return webLink;
		}

		public void setWebLink(String webLink) {
			this.webLink = webLink;
		}
		
		public String getTitle() {
			return title;
		}
		
		public String getSubtitle() {
			return subtitle;
		}
		
		public void setSubtitle(String s) {
			subtitle = s;
		}

		public ZonedDateTime getStartTimeZoned() {
			return startTime.withZoneSameInstant(outTZ);
		}
		
		public ZonedDateTime getEndTimeZoned() {
			return endTime.withZoneSameInstant(outTZ);
		}

		public Date getStartTime() {
			return Date.from(startTime.toInstant());
		}

		public Date getEndTime() {
			return Date.from(endTime.toInstant());
		}

		public Track getTrack() {
			return track;
		}
		
		public String getDescription() {
			return description;
		}

		public String getDescriptionStripped() {
			if (description == null) {
				return null;
			}
			String ret = description;
			/* Very clunky HTML stripper */
			if (ret.startsWith("<") || ret.contains("<p>")) {
				ret = ret.replaceAll("<[^>]*>", "");
			}
			return ret;
		}

		public String getLanguage() {
			return language;
		}

		public Spanned getDescriptionSpanned(Context ctx) {
			if (description == null) {
				return null;
			}

			if (description.contains("</")) {
				// Mild suggestion of HTML detected. Let's first check whether it's serious?
				Pattern htmlCheck = Pattern.compile("(?i)</?(?!p)\\b");
				if (htmlCheck.matcher(description).find()) {
					// Meaningful HTML found (i.e. more than just a few <p> tags) \o/
					// Markwon doesn't turn <p>..</p> into proper paragraphs AFAICT, so mangle them
					// a little bit.
					description = description.replaceAll("(?is)(\\s*</?p>\\s*)+", "<p><p>").trim();
					description = description.replaceAll("(?i)(<[^/p][^>]+>)(<p>)+", "$1");
					final Markwon mw = Markwon.builder(ctx).usePlugin(HtmlPlugin.create()).build();
					return mw.toMarkdown(description);
				}
				// Seen in the FOSDEM schedule: Markdown-ish but with paragraphs marked with both
				// whitespace and <p> tags. Well let's make it markdown then...
				description = description.replaceAll("(?is)(\\s*</?p>\\s*)+", "\n\n").trim();
			}

			final Markwon mw = Markwon.builder(ctx)
					                   .usePlugin(LinkifyPlugin.create()).build();
			return mw.toMarkdown(description);
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
		
		public LinkedList<Schedule.Link> getLinks() {
			return links;
		}

		public void setRemind(boolean remind_) {
			if (remind != remind_) {
				remind = remind_;
				newData |= fullyLoaded;
				applyItem(this);
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
		
		public boolean isHidden() {
			return hidden;
		}

		public void commit() {
			if (newData) {
				applyItem(this);
				newData = false;
			}
		}

		@Override
		public int compareTo(Item another) {
			int ret;
			if (this == null || startTime == null || getTitle() == null ||
			    another == null || another.startTime == null || another.getTitle() == null) {
				// Shouldn't happen in normal operation anyway, but it does happen during
				// de-serialisation for some reason :-( (Possibly because a "hollow" duplicate of an
				// object is restored before the filled in original?)
				// Log.d("Schedule.Item.compareTo", "null-ish object passed");
				return -123;
			}
			if ((ret = startTime.compareTo(another.startTime)) != 0) {
				return ret;
			} else if ((ret = getTitle().compareTo(another.getTitle())) != 0)
				return ret;
			else
				return another.hashCode() - hashCode();
		}

		// FIXME: Grr I think these two do the opposite of what a compareTo is meant to do?
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

		public int compareTo(ZonedDateTime d) {
			/* 0 if the event is "now" (d==now),
			 * -1 if it's in the future,
			 * 1 if it's in the past. */
			if (d.isBefore(startTime))
				return -1;
			else if (endTime.isAfter(d))
				return 0;
			else
				return 1;
		}

		public boolean overlaps(Item other) {
			// True if other's start- or end-time is during our event, or if it starts before and ends after ours.
			return (compareTo(other.startTime) == 0 || compareTo(other.endTime.minusSeconds(1)) == 0 ||
			        (!other.startTime.isAfter(startTime) && !other.endTime.isBefore(endTime)));
		}
	}

	public class Link implements Serializable {
		private String url, title;
		/* If type is set, at least ScheduleViewActivity will try to download and then view locally.
		   This works better for PDFs for example, also with caching it's beneficial on poor conference
		   WiFi (or worse, roaming!). */
		private String type;

		public Link(String url_, String title_) {
			if (!url_.matches("^[a-z]+:.*$"))
				url_ = "http://" + url_;
			url = url_;
			title = title_;
		}

		public Link(String url_) {
			this(url_, url_);
		}

		public String getUrl() {
			return url;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title_) {
			title = title_;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}
	}

	public Selections getSelections() {
		boolean empty = true;
		Selections ret = new Selections(this);
		
		for (Item item : allItems.values()) {
			int t = 0;
			if (item.getRemind())
				t += 1;
			if (item.isHidden())
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
	
	public void setSelections(Selections sel, CheckBox[] cbs) {
		if (!cbs[ScheduleUI.ImportSelections.KEEP_REMIND].isChecked()) {
			for (Item item : allItems.values()) {
				item.setRemind(false);
			}
		}
		if (!cbs[ScheduleUI.ImportSelections.KEEP_HIDDEN].isChecked()) {
			for (Item item : allItems.values()) {
				item.setHidden(false);
			}
		}
		if (cbs[ScheduleUI.ImportSelections.IMPORT_REMIND].isChecked()) {
			for (String id : sel.selections.keySet()) {
				if ((sel.selections.get(id) & 1) > 0) {
					Item item = allItems.get(id);
					if (item != null)
						item.setRemind(true);
				}
			}
		}
		if (cbs[ScheduleUI.ImportSelections.IMPORT_REMIND].isChecked()) {
			for (String id : sel.selections.keySet()) {
				if ((sel.selections.get(id) & 2) > 0) {
					Item item = allItems.get(id);
					if (item != null)
						item.setHidden(true);
				}
			}
		}
	}
	
	static public class Selections implements Serializable {
		public String url;
		public HashMap<String,Integer> selections = new HashMap<>();
		
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
				Log.d("Selections.url", url);
			} catch (UnsupportedEncodingException e) {}
			
			while (rd.available() > 4) {
				int type = rd.read();
				
				if (type > 0x03) {
					Log.w("Schedule.Selections", "Discarding unknown bits in type: " + type);
					type &= 0x03;
				}
				Log.d("Selections.type", "" + type);
				
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
					Log.d("Selections.id", id);
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
	}
}
