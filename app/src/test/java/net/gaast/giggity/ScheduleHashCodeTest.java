package net.gaast.giggity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScheduleHashCodeTest {
	private Schedule.Item item(String scheduleUrl, String guidOrId) {
		return new Schedule().new Item(guidOrId, null, guidOrId, null, null);
	}

	@Test
	public void itemsWithEqualScheduleUrlAndDiscriminatorHaveEqualHashCodes() {
		Schedule.Item first = item("https://example.test/schedule", "item-guid");
		Schedule.Item second = item("https://example.test/schedule", "item-guid");

		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void equalItemsHaveEqualHashCodes() {
		Schedule.Item first = item("https://example.test/schedule", "item-guid");
		Schedule.Item second = item("https://example.test/schedule", "item-guid");

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}
}
