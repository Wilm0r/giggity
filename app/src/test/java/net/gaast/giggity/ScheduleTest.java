package net.gaast.giggity;

import android.util.Log;

import junit.framework.TestCase;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class ScheduleTest extends TestCase {
	private Schedule s = new Schedule();

	public void setUp() throws Exception {
		super.setUp();
	}

	@Parameterized.Parameters
	public static Object[] data() {
		return new Object[] {
			"America/Los_Angeles", "America/New_York",
			"Europe/Dublin", "Europe/Amsterdam",
			"Asia/Taipei", "Australia/Sydney"
		};
	}

	@Parameterized.Parameter(0)
	public String tz_;

	private void load(String fn) {
		JSONObject js = null;
		try {
			String jsf = fn.split("\\.")[0] + ".json";
			String jss = IOUtils.toString(getClass().getClassLoader().getResourceAsStream(jsf));
			js = new JSONObject(jss);
		} catch (IOException | JSONException e) {
			Assert.assertFalse(true);
		}

		String itz = js.optString("timezone");
		if (!itz.isEmpty()) {
			s.setInTZ(ZoneId.of(itz));
		}
		s.setOutTZ(ZoneId.of(tz_));

		InputStream in = getClass().getClassLoader().getResourceAsStream(fn);
		try {
			long start = System.nanoTime();
			s.loadSchedule(new BufferedReader(new InputStreamReader(in)), js.optString("url"));
			JSONObject md;
			if (js != null && (md = js.optJSONObject(("metadata"))) != null) {
				s.addMetadata(md.toString());
			}
			Log.d("ScheduleTest.load", fn + " loaded with tz=" + tz_ + " in " + ((System.nanoTime() - start) / 1000000.0) + " ms");
		} catch (IOException e) {
			Assert.assertFalse(true);
		}
	}

	private Collection<String> setNames(Collection<? extends Schedule.ItemList> in) {
		Collection<String> ret = new ArrayList<>();
		for (Schedule.ItemList i : in) {
			ret.add(i.getTitle());
		}
		return ret;
	}

	@Test
	public void testFosdem21() {
		load("fosdem_2021.xml");
		Assert.assertEquals("FOSDEM 2021", s.getTitle());
		assertThat(s.getDays(), hasSize(2));

		assertThat(s.getTents(), hasSize(106));  // Crazy Covid schedule with ∞ rooms
		assertThat(setNames(s.getTents()), hasItem("K.fosdem"));
		assertThat(setNames(s.getTents()), hasItem("L.lightningtalks"));
		assertThat(setNames(s.getTents()), hasItem("D.mozilla"));
		assertThat(setNames(s.getTents()), hasItem("D.radio"));
		assertThat(s.getTracks(), hasSize(109));
		assertThat(setNames(s.getTracks()), hasItem("Emulator Development"));
		assertThat(setNames(s.getTracks()), hasItem("Open Source Design"));
		assertThat(setNames(s.getTracks()), hasItem("Monitoring and Observability"));
		assertThat(setNames(s.getTracks()), hasItem("Microkernel"));

		assertThat(s.setDay(0).getDayOfWeek(), is(DayOfWeek.SATURDAY));
		assertThat(s.getTents(), hasSize(87));
		assertThat(setNames(s.getTents()), hasItem("K.fosdem"));
		assertThat(setNames(s.getTents()), hasItem("L.lightningtalks"));
		assertThat(setNames(s.getTents()), hasItem("D.mozilla"));
		assertThat(setNames(s.getTents()), not(hasItem("D.radio")));
		assertThat(s.getTracks(), hasSize(88));
		assertThat(setNames(s.getTracks()), hasItem("Emulator Development"));
		assertThat(setNames(s.getTracks()), hasItem("Open Source Design"));
		assertThat(setNames(s.getTracks()), hasItem("Microkernel"));
		assertThat(setNames(s.getTracks()), not(hasItem("Monitoring and Observability")));

		assertThat(s.setDay(1).getDayOfWeek(), is(DayOfWeek.SUNDAY));
		assertThat(s.getTents(), hasSize(42));
		assertThat(setNames(s.getTents()), hasItem("K.fosdem"));
		assertThat(setNames(s.getTents()), hasItem("L.lightningtalks"));
		assertThat(setNames(s.getTents()), not(hasItem("D.mozilla")));
		assertThat(setNames(s.getTents()), hasItem("D.radio"));
		assertThat(s.getTracks(), hasSize(44));
		assertThat(setNames(s.getTracks()), hasItem("Emulator Development"));
		assertThat(setNames(s.getTracks()), hasItem("Open Source Design"));
		assertThat(setNames(s.getTracks()), not(hasItem("Microkernel")));
		assertThat(setNames(s.getTracks()), hasItem("Monitoring and Observability"));

		assertThat(s.setDay(ZonedDateTime.of(2021, 2, 6, 12, 0, 0, 0, ZoneId.of(tz_))),
		           is(0)); // Saturday
		assertThat(s.getTents(), hasSize(87));

		assertThat(s.setDay(ZonedDateTime.now()),
		           is(-1)); // Unpossible!

		s.setDay(-1); // Back to all
		assertThat(s.getTents(), hasSize(106));
		assertThat(s.getTracks(), hasSize(109));

		assertThat(s.getLanguages(), hasSize(0));

		Assert.assertFalse(s.isToday());

		Schedule.Item it = s.getItem("11795");
		assertThat(it.getTitle(), is("Welcome to FOSDEM 2021"));
		it.setHidden(true);
		assertThat(s.getTracks(), hasSize(109));
		s.setDay(0);
		assertThat(s.getTracks(), hasSize(87));

		it = s.getItem("12237");
		assertThat(it.getTitle(), is("Closing FOSDEM 2021"));
		it.setHidden(true);
		s.setDay(-1);
		assertThat(s.getTracks(), hasSize(108));
		s.setShowHidden(true);
		assertThat(s.getTracks(), hasSize(109));

		if (tz_.equals("America/New_York"))
			assertThat(s.getTzDiff(), equalTo(  6.0));
		else if(tz_.equals("Europe/Dublin"))
			assertThat(s.getTzDiff(), equalTo(  1.0));
		else if(tz_.equals("Australia/Sydney"))
			assertThat(s.getTzDiff(), equalTo(-10.0));

		assertThat(s.getLinks(), hasSize(3));

		// TODO: s.updateRoomStatus(json string) ?
	}

	@Test
	public void test36c3() {
		load("36c3_merged.xml");
		Assert.assertEquals("36th Chaos Communication Congress", s.getTitle());
		assertThat(s.getDays(), hasSize(4));

		assertThat(s.getTents(), hasSize(92));
		assertThat(s.getTracks(), hasSize(30));

		assertThat(s.setDay(0).getDayOfWeek(), is(DayOfWeek.FRIDAY));
		assertThat(s.getTents(), hasSize(55));
		assertThat(s.getTracks(), hasSize(27));

		assertThat(s.setDay(1).getDayOfWeek(), is(DayOfWeek.SATURDAY));
		assertThat(s.getTents(), hasSize(70));
		assertThat(s.getTracks(), hasSize(28));

		s.setDay(2); // Sun
		assertThat(s.getTents(), hasSize(78));
		assertThat(s.getTracks(), hasSize(28));

		s.setDay(3); // Mon
		assertThat(s.getTents(), hasSize(51));
		assertThat(s.getTracks(), hasSize(20));

		assertThat(s.setDay(-1), nullValue());
		assertThat(s.getTents(), hasSize(92));
		assertThat(s.getTracks(), hasSize(30));

		assertThat(s.getLanguages(), hasSize(4));
		assertThat(s.getByLanguage("German"), hasSize(551));
		assertThat(s.getByLanguage("English"), hasSize(676));
		// O_o
		assertThat(s.getByLanguage("Abkhazian"), hasSize(1));
		assertThat(s.getByLanguage("Czech"), hasSize(1));

		Assert.assertFalse(s.isToday());

		assertThat(s.getLinks(), hasSize(2));

		Schedule.Item it = s.getItem("017b6087-ac16-4968-8beb-051596720f24");  // id=1230
		assertThat(it.getTitle(), is("Detox Seaweed Bibimbab"));
		assertThat(it.getTrack().getTitle(), is("self organized sessions"));

		Schedule.Line room = it.getLine();
		assertThat(room.getTitle(), is("Assembly:Foodhackingbase"));
		assertThat(room.getLocation(), is("https://36c3.c3nav.de/l/fhb"));
		assertThat(room.getItems(), hasSize(23));
		s.setDay(1); // Sat
		assertThat(room.getItems(), hasSize(7));
		s.setDay(-1); // Back to all
		assertThat(room.getItems(), hasSize(23));

		Schedule.Track track = room.getTrack();
		assertThat(track.getTitle(), is("self organized sessions"));
		assertThat(track.getLine(), nullValue());

		assertThat(s.getCId("1230"), equalTo("017b6087-ac16-4968-8beb-051596720f24"));
		assertThat(s.getCId("893"), equalTo("4c1d5810-e052-539d-ac78-a4ea395b24cc"));

		if (tz_.equals("America/New_York"))
			assertThat(s.getTzDiff(), equalTo(  6.0));
		else if(tz_.equals("Europe/Dublin"))
			assertThat(s.getTzDiff(), equalTo(  1.0));
		else if(tz_.equals("Australia/Sydney"))
			assertThat(s.getTzDiff(), equalTo(-10.0));
	}

	@Test
	public void testKielux2020() {
		load("kielux_2020.ics");
		Assert.assertEquals("18. Kieler Open Source und Linux Tage", s.getTitle());
		assertThat(s.getDays(), hasSize(2));

		assertThat(s.getTents(), hasSize(4));
		Assert.assertNull(s.getTracks());
		assertThat(s.getLanguages(), hasSize(0));

		Assert.assertFalse(s.isToday());

		if (tz_.equals("America/New_York"))
			assertThat(s.getTzDiff(), equalTo( 6.0));
		else if(tz_.equals("Europe/Dublin"))
			assertThat(s.getTzDiff(), equalTo( 1.0));
		else if(tz_.equals("Australia/Sydney"))
			assertThat(s.getTzDiff(), equalTo(-8.0));
	}

	@Test
	public void testLca20() {
		load("linux_conf_au_2020.ics");
		assertThat(s.getTitle(), is("linux.conf.au 2020"));
		assertThat(s.getDays(), hasSize(6));

		assertThat(s.getTents(), hasSize(7));
		assertThat(s.getTracks(), nullValue());
		assertThat(s.getLanguages(), hasSize(0));

		Assert.assertFalse(s.isToday());

		if (tz_.equals("America/New_York"))
			assertThat(s.getTzDiff(), equalTo(15.0));
		else if(tz_.equals("Europe/Dublin"))
			assertThat(s.getTzDiff(), equalTo(10.0));
		else if(tz_.equals("Australia/Sydney"))
			assertThat(s.getTzDiff(), equalTo(-1.0));

		assertThat(s.setDay(0).getDayOfWeek(), is(DayOfWeek.SUNDAY));
		assertThat(s.setDay(5).getDayOfWeek(), is(DayOfWeek.FRIDAY));
		assertThat(s.setDay(-1), nullValue());
	}

	@Test
	public void testJres2022() {
		load("jres_2022.ics");
		assertThat(s.getTitle(), is("JRES 2021/22"));
		assertThat(s.getDays(), hasSize(4));

		assertThat(s.getTents(), hasSize(15));
		assertThat(s.getTracks(), nullValue());
		assertThat(s.getLanguages(), hasSize(0));

		Assert.assertFalse(s.isToday());

		if (tz_.equals("America/New_York"))
			assertThat(s.getTzDiff(), equalTo( 6.0));
		else if(tz_.equals("Europe/Dublin"))
			assertThat(s.getTzDiff(), equalTo( 1.0));
		else if(tz_.equals("Australia/Sydney"))
			assertThat(s.getTzDiff(), equalTo(-8.0));

		assertThat(s.setDay(0).getDayOfWeek(), is(DayOfWeek.TUESDAY));
		assertThat(s.getTents(), hasSize(13));
		assertThat(s.setDay(3).getDayOfWeek(), is(DayOfWeek.FRIDAY));
		assertThat(s.getTents(), hasSize(5));
		assertThat(s.setDay(-1), nullValue());
	}
}