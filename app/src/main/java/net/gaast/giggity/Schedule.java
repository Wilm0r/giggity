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

import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.util.Log;
import android.util.Xml;
import android.widget.CheckBox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.DateTimeUtils;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeParseException;
import org.threeten.bp.temporal.TemporalAccessor;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Schedule implements Serializable {
	private final int detectHeaderSize = 1024;
	
	private String url;
	private String title;

	private LinkedList<Schedule.Line> tents = new LinkedList<>();
	protected HashMap<String,Schedule.Item> allItems = new HashMap<>();
	private SortedMap<String,Track> tracks = new TreeMap<>();

	private ZonedDateTime firstTime, lastTime;
	private ZonedDateTime dayFirstTime, dayLastTime;  // equal to full schedule bounds (so spanning multiple days) if day = -1
	private ZonedDateTime curDay, curDayEnd;          // null if day = -1
	private ZoneId nativeTz = ZoneId.systemDefault();
	private LocalTime dayChange = LocalTime.of(6, 0);
	private LinkedList<ZonedDateTime> dayList;
	private boolean showHidden;  // So hidden items are shown but with a different colour.

	private HashSet<String> languages = new HashSet<>();

	/* Misc. data not in the schedule file but from Giggity's menu.json. Though it'd certainly be
	 * nice if some file formats could start supplying this info themselves. */
	private String icon;
	private LinkedList<Link> links;
	protected String roomStatusUrl;

	protected boolean fullyLoaded;

	protected void loadSchedule(BufferedReader in, String url_) throws IOException, LoadException {
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
		} else if (head.contains("{")) {
			loadJson(in);
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
		if (dayList == null) {
			ZonedDateTime day = ZonedDateTime.of(firstTime.toLocalDate(), dayChange, nativeTz);
			/* Add a day 0 (maybe there's an event before the first day officially
			 * starts?). Saw this in the CCC Fahrplan for example. */
			if (day.isAfter(firstTime))
				day = day.minusDays(1);

			ZonedDateTime dayEnd = day.plusDays(1);

			dayList = new LinkedList<>();
			while (day.isBefore(lastTime)) {
				/* Some schedules have empty days in between. :-/ Skip those. */
				for (Schedule.Item item : allItems.values()) {
					if (item.getStartTimeZoned().compareTo(day) >= 0 &&
						item.getEndTimeZoned().compareTo(dayEnd) <= 0) {
						dayList.add(day);
						break;
					}
				}
				day = dayEnd;
				dayEnd = dayEnd.plusDays(1);
			}
		}
		return dayList;
	}
	
	/** Total duration of this event in seconds. */
	public long eventLength() {
		return lastTime.toEpochSecond() - firstTime.toEpochSecond();
	}
	
	public ZonedDateTime getDay() {
		if (curDay != null) {
			return curDay;
		} else {
			return null;
		}
	}
	
	public void setDay(int day) {
		if (day == -1) {
			curDay = curDayEnd = null;
			dayFirstTime = firstTime;
			dayLastTime = lastTime;
		} else {
			if (day >= getDays().size())
				day = 0;

			curDay = getDays().get(day);
			curDayEnd = curDay.plusDays(1);

			dayFirstTime = dayLastTime = null;
			for (Schedule.Item item : allItems.values()) {
				if (item.getStartTimeZoned().compareTo(curDay) >= 0 &&
						item.getEndTimeZoned().compareTo(curDayEnd) <= 0) {
					if (dayFirstTime == null || item.getStartTimeZoned().isBefore(dayFirstTime))
						dayFirstTime = item.getStartTimeZoned();
					if (dayLastTime == null || item.getEndTimeZoned().isAfter(dayLastTime))
						dayLastTime = item.getEndTimeZoned();
				}
			}
		}
	}

	/* Sets day to one overlapping given moment in time and returns day number, or -1 if no match. */
	public int setDay(ZonedDateTime now) {
		int i = 0;
		for (ZonedDateTime day : getDays()) {
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
			return firstTime;
		} else {
			return dayFirstTime;
		}
	}
	
	/** Get highest item.endTime */
	public ZonedDateTime getLastTimeZoned() {
		if (curDay == null) {
			return lastTime;
		} else {
			return dayLastTime;
		}
	}

	public Date getFirstTime() {
		return DateTimeUtils.toDate(getFirstTimeZoned().toInstant());
		// return Date.from(getFirstTimeZoned().toInstant());
	}

	public Date getLastTime() {
		return DateTimeUtils.toDate(getLastTimeZoned().toInstant());
		// return Date.from(getLastTimeZoned().toInstant());
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
			Xml.parse(in, parser);
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
			throw new LoadException("Read error: " + e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new LoadException("Parse error: " + e);
		}
	}

	/*Reading the JSON open event format data fetched from the API end point*/
	private void loadJson(BufferedReader in) {

		StringBuffer buffer = new StringBuffer();
		DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(nativeTz);
		HashMap<String, Schedule.Line> tentMap = new HashMap<String, Schedule.Line>();
		Boolean hasMicrolocs = false;
		Scanner s = new Scanner(in);
		HashMap<String, String> locs = new HashMap<>();

		while (s.hasNext()) {
			buffer.append(s.nextLine());

		}

		String output = buffer.toString();
		Schedule.Line line = null;

		try {

			JSONObject conference = new JSONObject(output);
			int i;

			//Using this title name so the user doesn't see URL when she clicks back
			title = conference.getString("name");

			if (conference.has("logo")) {
				icon = conference.getString("logo");
			}

			links = new LinkedList<>();

			//Adding website separately because it is not in social_links
			if (conference.has("event_url")) {
				Schedule.Link slink = new Link(conference.getString("event_url"), "Website");
				links.addLast(slink);
			}

			//Adding ticket url separately because it is not in social_links
			if (conference.has("ticket_url")) {
				Schedule.Link slink = new Link(conference.getString("ticket_url"), "Ticket URL");
				links.addLast(slink);
			}

			//Using social links of the event like facebook, google+, etc.
			if (conference.has("social_links")) {
				JSONArray linklist = conference.getJSONArray("social_links");
				for (i = 0; i < linklist.length(); ++i) {
					JSONObject link = linklist.getJSONObject(i);
					Schedule.Link slink = new Link(link.getString("link"), link.getString("name"));
					slink.setType(link.optString("type", null));
					links.addLast(slink);
				}
			}


			if (conference.has("microlocations")) {

				/*Changing the flag after checking the organizer is using with microlocations
				options enabled*/

				hasMicrolocs = true;

				//Getting microlocations to add latitude and longitude
				JSONArray microlocations = conference.getJSONArray("microlocations");

				for (i = 0; i < microlocations.length(); i++) {

					JSONObject room = microlocations.getJSONObject(i);
					locs.put(room.getString("name"), room.getString("longitude") + "," + room.getString("latitude"));
				}
			}


			//The sessions are contained in the array present in an object
			JSONArray events = conference.getJSONArray("sessions");

			for (i = 0; i < events.length(); i++) {

				JSONObject event = events.getJSONObject(i);
				String uid = event.getString("id");
				String title = event.getString("title");

				/*Our date format is different and I changed getTimeInMillis() a bit to ignore "+08"
				in second part to avoid error in integer parsing*/
				String startTimeS = event.getString("start_time");
				String endTimeS = event.getString("end_time");
				ZonedDateTime startTime, endTime;

				if (startTimeS.contains("+")) {
					startTimeS = startTimeS.substring(0, startTimeS.lastIndexOf('+'));
				}
				startTimeS = startTimeS.substring(0, startTimeS.lastIndexOf('-'));

				if (endTimeS.contains("+")) {
					endTimeS = endTimeS.substring(0, endTimeS.lastIndexOf('+'));
				}
				endTimeS = endTimeS.substring(0, endTimeS.lastIndexOf('-'));

				startTime = ZonedDateTime.from(df.parse(startTimeS));
				endTime = ZonedDateTime.from(df.parse(endTimeS));

				Schedule.Item item = new Schedule.Item(uid, title, startTime, endTime);
				item.setDescription(event.getString("long_abstract"));

				if (event.getString("signup_url") != "null") {
					item.addLink(new Link(event.getString("signup_url")));
				}

				JSONObject microlocation = event.getJSONObject("microlocation");
				String location = microlocation.getString("name");

				if ((line = tentMap.get(location)) == null) {
					line = new Schedule.Line(location);
					tents.add(line);
					tentMap.put(location, line);
				}

				//Getting value (latitude and longitude) from the map by key (name)

				if (hasMicrolocs && line.getTitle()!=null && !line.getTitle().equals("")) {
					String locString = locs.get(line.getTitle());
					String latitude = locString.substring(0, locString.indexOf(','));
					String longitude = locString.substring(locString.indexOf(',') + 1);

					//Adding location details here
					String latlon = null;
					try {
						latlon = ("geo:0,0?q=" + longitude + "," +
								latitude + "(" +
								URLEncoder.encode(line.getTitle(), "utf-8") + ")");
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}


					if (latlon != null) {
						line.setLocation(latlon);
					}
				}

				line.addItem(item);
			}

		} catch (JSONException e) {
			e.printStackTrace();
			throw new LoadException("Parse error: " + e);
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

	public Collection<Track> getTracks() {
		if (tracks == null || tracks.size() == 0)
			return null;

		TreeSet<Track> ret = new TreeSet<>();
		for (Track e : tracks.values()) {
			if (e.getItems().size() > 0) {
				ret.add(e);
			}
		}

		return ret;
	}

	// Return all of them. Same if day = -1, otherwise the above filters for just non-empty ones today
	public Map<String, Track> allTracks() {
		if (tracks == null || tracks.size() == 0)
			return null;

		return tracks;
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
		
		return ret;
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
		private String curString;

		DateTimeFormatter dfUtc, dfLocal;

		public XcalParser() {
			tentMap = new HashMap<String,Schedule.Line>();

			dfUtc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));
			dfLocal = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(nativeTz);
		}

		private ZonedDateTime parseTime(String s) throws ParseException {
			TemporalAccessor ret;
			try {
				ret = dfUtc.parse(s, new ParsePosition(0));
			} catch (DateTimeParseException e) {
				ret = dfLocal.parse(s, new ParsePosition(0));
			}
			return ZonedDateTime.from(ret);
		}

		/* Yay I'll just write my own parser... Spec is at https://www.kanzaki.com/docs/ical/duration-t.html
		   Don't feel like importing a non-GPL library for just this. Also, returning an int (seconds) instead
		   of some kind of timedelta since the Java/Android version I'm targeting (<8?) doesn't have one yet.
		 */
		private int parseDuration(String durSpec) {
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
			Log.d("parseDuration", durSpec + ": " + ret + " seconds");
			return ret;
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("vevent")) {
				eventData = new HashMap<>();
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
					Log.w("Schedule.loadXcal", "Invalid event, some attributes are missing.");
					return;
				}
				
				try {
					startTime = parseTime(startTimeS);
					if (endTimeS != null) {
						endTime = parseTime(endTimeS);
					} else {
						endTime = startTime.plusSeconds(parseDuration(durationS));
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
		public void startPrefixMapping(String s, String s1) {}

		@Override
		public void endPrefixMapping(String s) {}

		@Override
		public void ignorableWhitespace(char[] chars, int i, int i1) {}

		@Override
		public void processingInstruction(String s, String s1) {}

		@Override
		public void skippedEntity(String s) {}
	}

	/* Pentabarf, the old conference organisation tool has a pretty excellent native XML format
	   and is now the preferred file format. http://pentabarf.org/Main_Page
	   It's not really maintained anymore though, a recent fork called Frab is more maintained and
	   Giggity can read its XML exports just as well https://github.com/frab/frab
	 */
	@SuppressWarnings("deprecation")
	private class PentabarfParser implements ContentHandler {
		private Schedule.Line curTent;
		private HashMap<String,Schedule.Line> tentMap;
		private HashMap<String,String> propMap;
		private String curString;
		private LinkedList<String> persons;
		private LinkedList<Link> links;
		private LocalDate curDay;

		private DateTimeFormatter df, tf;

		public PentabarfParser() {
			tentMap = new HashMap<>();

			//nativeTz = ZoneId.of("Europe/Brussels");
			//nativeTz = ZoneId.of("US/Pacific");
			df = DateTimeFormatter.ISO_LOCAL_DATE;
			//tf = DateTimeFormatter.ISO_LOCAL_TIME;
			tf = DateTimeFormatter.ofPattern("H:mm[:ss]");
		}
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("conference") || localName.equals("event")) {
				propMap = new HashMap<String,String>();
				propMap.put("id", atts.getValue("id"));
				
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
					// TODO: PARSE ERROR?
				}
			} else if (localName.equals("event")) {
				String id, title, startTimeS, durationS, s, desc, wl;
				ZonedDateTime startTime, endTime;
				Schedule.Item item;
				
				if ((id = propMap.get("id")) == null ||
				    (title = propMap.get("title")) == null ||
				    (startTimeS = propMap.get("start")) == null ||
				    (durationS = propMap.get("duration")) == null) {
					Log.w("Schedule.loadPentabarf", "Invalid event, some attributes are missing.");
					return;
				}

				LocalTime rawTime = LocalTime.parse(startTimeS, tf);
				startTime = ZonedDateTime.of(curDay, rawTime, nativeTz);

				if (rawTime.isBefore(dayChange)) {
					// In Frab files, if a time is before day_change it's technically the next
					// day.
					startTime = startTime.plusDays(1);
				}

				rawTime = LocalTime.parse(durationS, tf);
				endTime = startTime.plusHours(rawTime.getHour()).plusMinutes(rawTime.getMinute());

				item = new Schedule.Item(id, title, startTime, endTime);
				
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
					s = s.replaceAll("\n*$", "");
					desc += s + "\n\n";
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
		public void startPrefixMapping(String s, String s1) {}

		@Override
		public void endPrefixMapping(String s) {}

		@Override
		public void ignorableWhitespace(char[] chars, int i, int i1) {}

		@Override
		public void processingInstruction(String s, String s1) {}

		@Override
		public void skippedEntity(String s) {}
	}

	public enum RoomStatus {
		UNKNOWN,
		OK,
		FULL,
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
				    (curDay == null || (!item.getStartTimeZoned().isBefore(curDay) &&
				                        !item.getEndTimeZoned().isAfter(curDayEnd))))
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

			if (firstTime == null || item.getStartTimeZoned().isBefore(firstTime))
				firstTime = item.getStartTimeZoned();
			if (lastTime == null || item.getEndTimeZoned().isAfter(lastTime))
				lastTime = item.getEndTimeZoned();

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
			HashSet<Track> ret = new HashSet<>();
			for (Item it : getItems()) {
				ret.add(it.getTrack());
				if (ret.size() != 1) {
					return null;
				}
			}
			return ret.iterator().next();
		}
	}

	public class Track extends ItemList implements Comparable<Track>, Serializable {
		public Track(String title_) {
			super(title_);
		}

		// Return Schedule.Line for this track, only if it's one and the same for all its items.
		public Line getLine() {
			HashSet<Line> ret = new HashSet<>();
			for (Item it : getItems()) {
				ret.add(it.getLine());
				if (ret.size() != 1) {
					return null;
				}
			}
			return ret.iterator().next();
		}

		@Override
		public int compareTo(Track track) {
			return getTitle().compareTo(track.getTitle());
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

		@Deprecated
		private int stars = -1;

		Item(String id_, String title_, ZonedDateTime startTime_, ZonedDateTime endTime_) {
			id = id_;
			title = title_;
			startTime = startTime_;
			endTime = endTime_;
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
			return startTime;
		}
		
		public ZonedDateTime getEndTimeZoned() {
			return endTime;
		}

		public Date getStartTime() {
			return DateTimeUtils.toDate(getStartTimeZoned().toInstant());
			// return Date.from(getStartTimeZoned().toInstant());
		}

		public Date getEndTime() {
			return DateTimeUtils.toDate(getEndTimeZoned().toInstant());
			// return Date.from(getEndTimeZoned().toInstant());
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

		private String descriptionMarkdownHack(String md) {
			String ret = md;
			ret = ret.replaceAll("(?m)^#### (.*)$", "<h4>$1</h4>");
			ret = ret.replaceAll("(?m)^### (.*)$", "<h3>$1</h3>");
			ret = ret.replaceAll("(?m)^## (.*)$", "<h2>$1</h2>");
			ret = ret.replaceAll("(?m)^# (.*)$", "<h1>$1</h1>");
			ret = ret.replaceAll("(?m)^ {0,2}[-+*] ", "<li>");
			ret = ret.replaceAll("(?m)^ {0,2}([0-9]+\\. )", "<br>$1");
			ret = ret.replaceAll("\n\n(?=[^<])", "<p>");
			ret = ret.replaceAll("\\[([^\\]]+)\\]\\((http[^\\)]+)\\)", "<a href=\"$2\">$1</a>");
			return ret;
		}

		public Spanned getDescriptionSpanned() {
			if (description == null) {
				return null;
			}

			String html;
			if (description.startsWith("<") || description.contains("<p>")) {
				html = description;
			} else {
				html = descriptionMarkdownHack(description);
			}
			Spanned formatted;
			if (android.os.Build.VERSION.SDK_INT < 24) {
				/* This parser is VERY limited, results aren't great, but let's give it a shot.
				   I'd really like to avoid using a full-blown WebView.. */
				Html.TagHandler th = new Html.TagHandler() {
					@Override
					public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
						if (tag.equals("li")) {
							if (opening) {
								output.append(" • ");
							} else {
								output.append("\n");
							}
						} else if (tag.equals("ul") || tag.equals("ol")) {
							/* For both opening and closing */
							output.append("\n");
						}
					}
				};
				formatted = (Spannable) Html.fromHtml(html, null, th);
			} else {
				formatted = Html.fromHtml(html, Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM, null, null);
			}
			// TODO: This, too, ruins existing links. WTF guys.. :<
			// Linkify.addLinks(formatted, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
			return formatted;
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
				applyItem(this);
				newData = false;
			}
		}

		@Override
		public int compareTo(Item another) {
			int ret;
			if (this == null || getStartTimeZoned() == null || getTitle() == null ||
			    another == null || another.getStartTimeZoned() == null || another.getTitle() == null) {
				// Shouldn't happen in normal operation anyway, but it does happen during
				// de-serialisation for some reason :-( (Possibly because a "hollow" duplicate of an
				// object is restored before the filled in original?)
				// Log.d("Schedule.Item.compareTo", "null-ish object passed");
				return -123;
			}
			if ((ret = getStartTimeZoned().compareTo(another.getStartTimeZoned())) != 0) {
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
			if (d.isBefore(getStartTimeZoned()))
				return -1;
			else if (getEndTimeZoned().isAfter(d))
				return 0;
			else
				return 1;
		}

		public boolean overlaps(Item other) {
			// True if other's start- or end-time is during our event, or if it starts before and ends after ours.
			return (compareTo(other.getStartTimeZoned()) == 0 || compareTo(other.getEndTimeZoned()) == 0 ||
			        (!other.getStartTimeZoned().isAfter(getStartTimeZoned()) && !other.getEndTimeZoned().isBefore(getEndTimeZoned())));
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
