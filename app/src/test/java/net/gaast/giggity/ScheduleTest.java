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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class ScheduleTest extends TestCase {
	private Schedule s = new Schedule();

	public void setUp() {
		Log.d("ScheduleTest", "Running with tz=" + tz_);
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
		long start = System.nanoTime();
		ZoneId tz = ZoneId.of(tz_);
		s.setNativeTz(tz);
		InputStream in = getClass().getClassLoader().getResourceAsStream(fn);
		try {
			s.loadSchedule(new BufferedReader(new InputStreamReader(in)), "file://res/" + fn);
			JSONObject md;
			if (js != null && (md = js.optJSONObject(("metadata"))) != null) {
				s.addMetadata(md.toString());
			}
			Log.d("ScheduleTest.load", fn + " loaded in " + ((System.nanoTime() - start) / 1000000.0) + " ms");
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

		s.setDay(0); // Saturday
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

		s.setDay(1); // Sunday
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

		s.setDay(-1); // Back to all
		assertThat(s.getTents(), hasSize(106));
		assertThat(s.getTracks(), hasSize(109));

		assertThat(s.getLanguages(), hasSize(0));

		// assertThat(s.getTzOffset(), equalTo(0.0));
		Assert.assertFalse(s.isToday());

		assertThat(s.getLinks(), hasSize(3));
	}

	@Test
	public void test36c3() {
		load("36c3_merged.xml");
		Assert.assertEquals("36th Chaos Communication Congress", s.getTitle());
		assertThat(s.getDays(), hasSize(4));

		assertThat(s.getTents(), hasSize(92));
		assertThat(s.getTracks(), hasSize(30));

		s.setDay(0); // Fri
		assertThat(s.getTents(), hasSize(55));
		assertThat(s.getTracks(), hasSize(27));

		s.setDay(1); // Sat
		assertThat(s.getTents(), hasSize(70));
		assertThat(s.getTracks(), hasSize(28));

		s.setDay(2); // Sun
		assertThat(s.getTents(), hasSize(78));
		assertThat(s.getTracks(), hasSize(28));

		s.setDay(3); // Mon
		assertThat(s.getTents(), hasSize(51));
		assertThat(s.getTracks(), hasSize(20));

		s.setDay(-1); // Back to all
		assertThat(s.getTents(), hasSize(92));
		assertThat(s.getTracks(), hasSize(30));

		assertThat(s.getLanguages(), hasSize(4));
		assertThat(s.getByLanguage("German"), hasSize(543));
		assertThat(s.getByLanguage("English"), hasSize(674));
		// BS entries? :-/
		assertThat(s.getByLanguage("Abkhazian"), hasSize(1));
		assertThat(s.getByLanguage("Czech"), hasSize(1));

		// assertThat(s.getTzOffset(), equalTo(0.0));
		Assert.assertFalse(s.isToday());

		assertThat(s.getLinks(), hasSize(2));

		Schedule.Item it = s.getItem("1230");
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
	}

	@Test
	public void testKielux2020() {
		load("kielux_2020.ics");
		Assert.assertEquals("18. Kieler Open Source und Linux Tage", s.getTitle());
		assertThat(s.getDays(), hasSize(2));

		assertThat(s.getTents(), hasSize(4));
		Assert.assertNull(s.getTracks());
		assertThat(s.getLanguages(), hasSize(0));

		// assertThat(s.getTzOffset(), equalTo(0.0));
		Assert.assertFalse(s.isToday());
	}

	@Test
	public void testLca20() {
		load("linux_conf_au_2020.ics");
		assertThat(s.getTitle(), is("linux.conf.au 2020"));
		assertThat(s.getDays(), hasSize(6));

		assertThat(s.getTents(), hasSize(7));
		assertThat(s.getTracks(), nullValue());
		assertThat(s.getLanguages(), hasSize(0));

		if (tz_.equals("America/New_York"))
			assertThat(s.getTzOffset(), equalTo(15.0));
		else if(tz_.equals("Europe/Dublin"))
			assertThat(s.getTzOffset(), equalTo(10.0));
		else if(tz_.equals("Australia/Sydney"))
			assertThat(s.getTzOffset(), equalTo(-1.0));
		Assert.assertFalse(s.isToday());
	}
}