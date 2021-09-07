package net.gaast.giggity;

import android.util.Log;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ScheduleTest {
	public ScheduleTest() {
		super();
	}

	private Schedule load(String fn) {
		long start = System.nanoTime();
		Schedule s = new Schedule();
		InputStream in = getClass().getClassLoader().getResourceAsStream(fn);

		try {
			s.loadSchedule(new BufferedReader(new InputStreamReader(in)), "file://res/" + fn);
			Log.d("ScheduleTest.load", fn + " loaded in " + ((System.nanoTime() - start) / 1000000.0) + " ms");
		} catch (IOException e) {
		}
		return s;
	}

	private Collection<String> tentNames(Collection<? extends Schedule.ItemList> in) {
		Collection<String> ret = new ArrayList<>();
		for (Schedule.ItemList i : in) {
			ret.add(i.getTitle());
		}
		return ret;
	}

	@Test
	public void testFosdem21() {
		Schedule s = load("fosdem_2021.xml");
		Assert.assertEquals("FOSDEM 2021", s.getTitle());
		Assert.assertEquals(2, s.getDays().size());

		Assert.assertEquals(106, s.getTents().size());  // Crazy Covid schedule with ∞ rooms
		Assert.assertTrue(tentNames(s.getTents()).contains("K.fosdem"));
		Assert.assertTrue(tentNames(s.getTents()).contains("L.lightningtalks"));
		Assert.assertTrue(tentNames(s.getTents()).contains("D.mozilla"));
		Assert.assertTrue(tentNames(s.getTents()).contains("D.radio"));
		Assert.assertEquals(109, s.getTracks().size());
		Assert.assertTrue(tentNames(s.getTracks()).contains("Emulator Development"));
		Assert.assertTrue(tentNames(s.getTracks()).contains("Open Source Design"));
		Assert.assertTrue(tentNames(s.getTracks()).contains("Monitoring and Observability"));
		Assert.assertTrue(tentNames(s.getTracks()).contains("Microkernel"));

		s.setDay(0); // Saturday
		Assert.assertEquals(87, s.getTents().size());
		Assert.assertTrue(tentNames(s.getTents()).contains("K.fosdem"));
		Assert.assertTrue(tentNames(s.getTents()).contains("L.lightningtalks"));
		Assert.assertTrue(tentNames(s.getTents()).contains("D.mozilla"));
		Assert.assertFalse(tentNames(s.getTents()).contains("D.radio"));
		Assert.assertEquals(88, s.getTracks().size());
		Assert.assertTrue(tentNames(s.getTracks()).contains("Emulator Development"));
		Assert.assertTrue(tentNames(s.getTracks()).contains("Open Source Design"));
		Assert.assertTrue(tentNames(s.getTracks()).contains("Microkernel"));
		Assert.assertFalse(tentNames(s.getTracks()).contains("Monitoring and Observability"));

		s.setDay(1); // Sunday
		Assert.assertEquals(42, s.getTents().size());
		Assert.assertTrue(tentNames(s.getTents()).contains("K.fosdem"));
		Assert.assertTrue(tentNames(s.getTents()).contains("L.lightningtalks"));
		Assert.assertFalse(tentNames(s.getTents()).contains("D.mozilla"));
		Assert.assertTrue(tentNames(s.getTents()).contains("D.radio"));
		Assert.assertEquals(44, s.getTracks().size());
		Assert.assertTrue(tentNames(s.getTracks()).contains("Emulator Development"));
		Assert.assertTrue(tentNames(s.getTracks()).contains("Open Source Design"));
		Assert.assertFalse(tentNames(s.getTracks()).contains("Microkernel"));
		Assert.assertTrue(tentNames(s.getTracks()).contains("Monitoring and Observability"));

		s.setDay(-1); // Back to all
		Assert.assertEquals(106, s.getTents().size());
		Assert.assertEquals(109, s.getTracks().size());

		Assert.assertEquals(0, s.getLanguages().size());

		Assert.assertEquals(0, s.getTzOffset(), .01);  // WRONG, it's actually tz-clueless.. TODO
		Assert.assertFalse(s.isToday());
	}

	@Test
	public void test36c3() {
		Schedule s = load("36c3_merged.xml");
		Assert.assertEquals("36th Chaos Communication Congress", s.getTitle());
		Assert.assertEquals(4, s.getDays().size());

		Assert.assertEquals(92, s.getTents().size());
		Assert.assertEquals(30, s.getTracks().size());

		s.setDay(0); // Fri
		Assert.assertEquals(55, s.getTents().size());
		Assert.assertEquals(27, s.getTracks().size());

		s.setDay(1); // Sat
		Assert.assertEquals(70, s.getTents().size());
		Assert.assertEquals(28, s.getTracks().size());

		s.setDay(2); // Sun
		Assert.assertEquals(78, s.getTents().size());
		Assert.assertEquals(28, s.getTracks().size());

		s.setDay(3); // Mon
		Assert.assertEquals(51, s.getTents().size());
		Assert.assertEquals(20, s.getTracks().size());

		s.setDay(-1); // Back to all
		Assert.assertEquals(92, s.getTents().size());
		Assert.assertEquals(30, s.getTracks().size());

		Assert.assertEquals(4, s.getLanguages().size());
		Assert.assertEquals(543, s.getByLanguage("German").size());
		Assert.assertEquals(674, s.getByLanguage("English").size());
		// BS entries? :-/
		Assert.assertEquals(1, s.getByLanguage("Abkhazian").size());
		Assert.assertEquals(1, s.getByLanguage("Czech").size());

		// TODO AWARENESS Assert.assertEquals(0, s.getTzOffset(), .01);  // WRONG, it's actually tz-clueless.. TODO
		Assert.assertFalse(s.isToday());
	}

	@Test
	public void testKolt20() {
		Schedule s = load("kolt_20.ics");
		Assert.assertEquals("18. Kieler Open Source und Linux Tage", s.getTitle());
		Assert.assertEquals(3, s.getDays().size());

		Assert.assertEquals(4, s.getTents().size());
		Assert.assertNull(s.getTracks());
		Assert.assertEquals(0, s.getLanguages().size());

		Assert.assertEquals(0, s.getTzOffset(), .01);  // WRONG, it's actually tz-clueless.. TODO
		Assert.assertFalse(s.isToday());
	}

	@Test
	public void testLca20() {
		Schedule s = load("linux_conf_au_2020.ics");
		Assert.assertEquals("linux.conf.au 2020", s.getTitle());
		Assert.assertEquals(6, s.getDays().size());

		Assert.assertEquals(7, s.getTents().size());
		Assert.assertNull(s.getTracks());
		Assert.assertEquals(0, s.getLanguages().size());

		Assert.assertEquals(15, s.getTzOffset(), .01);  // WRONG, it's actually tz-clueless.. TODO
		Assert.assertFalse(s.isToday());
	}
}