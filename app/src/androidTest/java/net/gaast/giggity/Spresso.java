package net.gaast.giggity;

import android.os.Build;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParentIndex;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class Spresso {

	@Rule
	public ActivityScenarioRule<ChooserActivity> mActivityRule = new ActivityScenarioRule<>(
			ChooserActivity.class);

	@Rule
	public TestRule grantPermissionRule =
		Build.VERSION.SDK_INT >= 33
			? GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")
			: (base, description) -> base;

	private static Matcher scheduleByTitle(Matcher nameMatcher){
		return new TypeSafeMatcher<Db.DbSchedule>(){
			@Override
			public void describeTo(Description description) {
				description.appendText("DbSchedule matching title ").appendDescriptionOf(nameMatcher);
			}

			@Override
			public boolean matchesSafely(Db.DbSchedule sch) {
				return nameMatcher.matches(sch.getTitle());
			}
		};
	}

	private static Matcher scheduleItemByTitle(Matcher nameMatcher){
		return new TypeSafeMatcher<Schedule.Item>(){
			@Override
			public void describeTo(Description description) {
				description.appendText("Schedule.Item matching title ").appendDescriptionOf(nameMatcher);
			}

			@Override
			public boolean matchesSafely(Schedule.Item sci) {
				return nameMatcher.matches(sci.getTitle());
			}
		};
	}

	@Test
	public void testBasicNav() {
		Intents.init();

		// I wrote this test in '23 when this schedule was near the top of the list. Of course by
		// now it isn't in the list anymore at all by default, so add it.
		Giggity context = ApplicationProvider.getApplicationContext();
		context.getDb().updateRawSchedule("{\"id\": \"debconf23\", \"version\": 2023090301, \"url\": \"https://debconf23.debconf.org/schedule/pentabarf.xml\", \"title\": \"DebConf 23\", \"start\": \"2023-09-10\", \"end\": \"2023-09-17\", \"timezone\": \"Asia/Kolkata\", \"metadata\": {\"icon\": \"https://debconf23.debconf.org/static/img/favicon/favicon-128-rgba.png\", \"links\": [{\"url\": \"https://debconf23.debconf.org/\", \"title\": \"Website\"}, {\"title\": \"Wiki\", \"url\": \"https://wiki.debian.org/DebConf/23\"}, {\"title\": \"Venues Map\", \"type\": \"application/pdf\", \"url\": \"https://debconf23.debconf.org/static/img/dc23-venue-map.pdf\"}, {\"title\": \"Amenities Map\", \"type\": \"application/pdf\", \"url\": \"https://debconf23.debconf.org/static/img/dc23-amenities-map.pdf\"}, {\"url\": \"https://debconf23.debconf.org/about/coc/\", \"title\": \"Code of Conduct\"}, {\"url\": \"https://debconf23.debconf.org/about/debcamp/\", \"title\": \"DebCamp\"}, {\"url\": \"https://debconf23.debconf.org/about/accommodation/\", \"title\": \"Accommodation\"}, {\"url\": \"https://debconf23.debconf.org/about/childcare/\", \"title\": \"Child care\"}, {\"url\": \"https://debconf23.debconf.org/about/covid19/\", \"title\": \"COVID-19\"}, {\"url\": \"https://wiki.debian.org/DebConf/23/Faq\", \"title\": \"FAQ\"}, {\"url\": \"https://debconf23.debconf.org/about/venue/\", \"title\": \"Venue\"}, {\"url\": \"https://debconf23.debconf.org/about/visas/\", \"title\": \"Visas\"}], \"rooms\": [{\"name\": \"Anamudi\", \"latlon\": [10.00888, 76.36152]}, {\"name\": \"(Ponmudi|Kuthiran)\", \"latlon\": [10.00715, 76.36249]}]}}".getBytes(StandardCharsets.UTF_8));

		CountingIdlingResource loaders = new CountingIdlingResource("loaders");
		ChooserActivity.setIdler(loaders);
		Espresso.registerIdlingResources(loaders);
		onView(withClassName(containsString("SwipeRefreshLayout"))).perform(swipeDown());
		ChooserActivity.setIdler(null);
		Espresso.unregisterIdlingResources(loaders);

		loaders = new CountingIdlingResource("scheduleLoaders");
		ScheduleViewActivity.setIdler(loaders);
		Espresso.registerIdlingResources(loaders);


		// Load schedule.
		onData(scheduleByTitle(containsString("DebConf 23"))).perform(click());


		// Start with the first day.
		onView(withId(R.id.drawerLayout)).perform(DrawerActions.open());
		onView(withId(R.id.drawerScroll)).perform(swipeUp());
		onView(allOf(withText(R.string.change_day))).perform(click());
		onView(allOf(withClassName(endsWith("CheckedTextView")), withText(containsString("10")))).perform(click());

		// Timetable view.
		onView(withId(R.id.drawerLayout)).perform(DrawerActions.open());
		onView(withId(R.id.drawerScroll)).perform(swipeDown());
		onView(allOf(withId(R.id.timetable), withText(R.string.timetable))).perform(click());
		onView(withId(R.id.drawerLayout)).perform(DrawerActions.close());

		onData(scheduleItemByTitle(startsWith("Using FOSS to fight"))).inAdapterView(withClassName(is("net.gaast.giggity.ScheduleListView"))).perform(click());
		pressBack();

		onData(scheduleItemByTitle(startsWith("Forming a SSH Cluster"))).inAdapterView(withClassName(is("net.gaast.giggity.ScheduleListView"))).perform(click());
		ViewInteraction dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("Forming a SSH Cluster")))));
		dialog.perform(click());  // .... I kind of don't understand but this click is ... load-bearing?

		ViewInteraction remind = onView(allOf(isDescendantOfA(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("Forming a SSH Cluster"))))), withText(R.string.remind_me), withClassName(is("android.widget.CheckBox"))));
		remind.perform(click());

		pressBack();

		// 12th Sep
		onView(withId(R.id.drawerLayout)).perform(DrawerActions.open());
		onView(withId(R.id.drawerScroll)).perform(swipeUp());
		onView(allOf(withText(R.string.change_day))).perform(click());
		onView(allOf(withClassName(endsWith("CheckedTextView")), withText(containsString("12")))).perform(click());

		onData(scheduleItemByTitle(startsWith("Face-to-face Debian"))).inAdapterView(withClassName(is("net.gaast.giggity.ScheduleListView"))).perform(click());
		dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("Face-to-face Debian")))));
		dialog.perform(click());  // .... I kind of don't understand but this click is ... load-bearing?

		remind = onView(allOf(isDescendantOfA(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("Face-to-face Debian"))))), withText(R.string.remind_me), withClassName(is("android.widget.CheckBox"))));
		remind.perform(click());

		pressBack();

		onData(scheduleItemByTitle(startsWith("/usr-merge"))).inAdapterView(withClassName(is("net.gaast.giggity.ScheduleListView"))).perform(click());
		dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("/usr-merge")))));
		dialog.perform(click());  // .... I kind of don't understand but this click is ... load-bearing?

		remind = onView(allOf(isDescendantOfA(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("/usr-merge"))))), withText(R.string.remind_me), withClassName(is("android.widget.CheckBox"))));
		remind.perform(click());

		pressBack();


		// Search query
		onView(withId(R.id.drawerLayout)).perform(DrawerActions.open());
		onView(withId(R.id.drawerScroll)).perform(swipeDown());
		onView(allOf(withId(R.id.search), withText(R.string.search))).perform(click());

		ViewInteraction searchQuery = onView(
				allOf(withClassName(is("net.gaast.giggity.ItemSearch$SearchQuery")),
						isDisplayed()));
		searchQuery.perform(replaceText("linux"), closeSoftKeyboard());

		onData(scheduleItemByTitle(startsWith("What's new in the"))).inAdapterView(withClassName(is("net.gaast.giggity.ScheduleListView"))).perform(click());
		dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("What's new in the")))));
		dialog.perform(click());  // .... I kind of don't understand but this click is ... load-bearing?

		remind = onView(allOf(isDescendantOfA(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("What's new in the"))))), withText(R.string.remind_me), withClassName(is("android.widget.CheckBox"))));
		remind.perform(click());

		pressBack();

		// My events, see what we selected so far?
		onView(withId(R.id.drawerLayout)).perform(DrawerActions.open());
		onView(withId(R.id.drawerScroll)).perform(swipeDown());
		onView(allOf(withId(R.id.my_events), withText(R.string.my_events))).perform(click());

		// TODO: Check why MyItemsView exists but Timetable is just ScheduleListView... Is it because of the odd little carousel thing at the top?
		onData(scheduleItemByTitle(startsWith("/usr-merge"))).inAdapterView(withClassName(is("net.gaast.giggity.MyItemsView"))).perform(click());
		dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("/usr-merge")))));
		dialog.check(matches(isDisplayed()));
		dialog.perform(click(), swipeLeft());
		dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("What's new in the")))));
		dialog.check(matches(isDisplayed()));
		dialog.perform(click(), swipeLeft());
		// Verify that we indeed swiped to the next talk, the one initially tapped on is not visible.
		// (But due to how the swipe view works, it's indeed still in the view hierarchy somewhere.)
		dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("/usr-merge")))));
		dialog.check(matches(not(isDisplayed())));
		// The second swipe should've been a no-op, we should still be showing the same talk.
		dialog = onView(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(startsWith("What's new in the")))));
		dialog.check(matches(isDisplayed()));

		pressBack();
		pressBack();
		onData(scheduleByTitle(containsString("DebConf 23"))).perform(longClick());
		onView(allOf(withText(R.string.share_selections))).perform(click());
//		onData(scheduleByTitle(containsString("FOSDEM 2026"))).perform(scrollTo());

		ScheduleViewActivity.setIdler(null);

		// shareSelections() wraps the ACTION_SEND intent in a chooser, so Espresso records
		// ACTION_CHOOSER. Match the nested EXTRA_INTENT to inspect the actual share content.
		Intents.intended(allOf(
				IntentMatchers.hasAction(android.content.Intent.ACTION_CHOOSER),
				IntentMatchers.hasExtra(android.content.Intent.EXTRA_INTENT, allOf(
						IntentMatchers.hasAction(android.content.Intent.ACTION_SEND),
						IntentMatchers.hasExtra(android.content.Intent.EXTRA_TEXT, allOf(
								containsString("ggt.gaa.st"),
								containsString("&see=")
						))
				))
		));

		Intents.release();
	}

	@Test
	public void testDeepLinkWithImport() {
		// Arguments are long because they're still using full guid's.
		String deepLink = "https://ggt.gaa.st/#url=https%3A%2F%2Ffosdem.org%2F2026%2Fschedule%2Fxml&see=eJwVjMENQyEMxXbh3CdBXlLILFUP-YTsP0LpyQdb_jRaz_lkIYQHJk6stxbmqHiSOTy8vZqWbU8O0CmwUwbfx6FTyq9bdfo_61WVMZBGwlIFd0KEXsji7p7t-wMj1h7f&del=eJyLVkoySzazMDOz0DU3TzbVNU00TdS1NDUx1LU0SU1JTTEyNbJMslCKBQDOEAou";
		android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(deepLink));
		intent.setClass(ApplicationProvider.getApplicationContext(), ScheduleViewActivity.class);

		CountingIdlingResource loaders = new CountingIdlingResource("deepLinkLoaders");
		ScheduleViewActivity.setIdler(loaders);
		Espresso.registerIdlingResources(loaders);

		androidx.test.core.app.ActivityScenario.launch(intent);

		onView(withClassName(is("net.gaast.giggity.ImportView")))
		.check(matches(isDisplayed()));

		onView(withText(R.string.reminders)).check(matches(isDisplayed()));
		onView(withText(R.string.hidden)).check(matches(isDisplayed()));

		// I wish there were a "click on the n'th *match*" matcher but it seems not.
		onView(Matchers.allOf(withText(R.string.import_all), isDisplayed(), isDescendantOfA(withParentIndex(4))))
		.perform(click());

		Giggity context = ApplicationProvider.getApplicationContext();
		ScheduleUI s = context.getSchedule("https://fosdem.org/2026/schedule/xml", Fetcher.Source.CACHE_ONLY, null);
		Assert.assertEquals("https://ggt.gaa.st/#url=https%3A%2F%2Ffosdem.org%2F2026%2Fschedule%2Fxml", s.exportLink());

		onView(Matchers.allOf(withText(R.string.overwrite), isDisplayed()))
		.perform(click());

		// Can't do an exact match because I don't think the order will be deterministic. :(
		Assert.assertNotEquals("https://ggt.gaa.st/#url=https%3A%2F%2Ffosdem.org%2F2026%2Fschedule%2Fxml", s.exportLink());

		int num = 0;
		for (Schedule.Line line : s.getTents()) {
			for (Schedule.Item it : line.getItems()) {
				if (it.getRemind()) {
					onData(scheduleItemByTitle(startsWith(it.getTitle()))).inAdapterView(withClassName(is("net.gaast.giggity.ImportView"))).check(matches(isDisplayed()));
					num++;
				}
			}
		}
		Assert.assertEquals(3, num);
	}

	// Pretty simple test just to make sure the render code still runs without crashing, and that
	// toggle to vertical mode works as expected.
	@Test
	public void testBlockSchedule() throws InterruptedException {
		Giggity context = ApplicationProvider.getApplicationContext();
		context.getDb().updateRawSchedule("{\"id\": \"debconf23\", \"version\": 2023090301, \"url\": \"https://debconf23.debconf.org/schedule/pentabarf.xml\", \"title\": \"DebConf 23\", \"start\": \"2023-09-10\", \"end\": \"2023-09-17\", \"timezone\": \"Asia/Kolkata\", \"metadata\": {\"icon\": \"https://debconf23.debconf.org/static/img/favicon/favicon-128-rgba.png\"}}".getBytes(StandardCharsets.UTF_8));

		// Ensure horizontal mode so we can test the toggle.
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putBoolean("block_schedule_vertical", false)
				.apply();

		CountingIdlingResource loaders = new CountingIdlingResource("blockScheduleChooserLoaders");
		ChooserActivity.setIdler(loaders);
		Espresso.registerIdlingResources(loaders);
		onView(withClassName(containsString("SwipeRefreshLayout"))).perform(swipeDown());
		ChooserActivity.setIdler(null);
		Espresso.unregisterIdlingResources(loaders);

		loaders = new CountingIdlingResource("blockScheduleLoaders");
		ScheduleViewActivity.setIdler(loaders);
		Espresso.registerIdlingResources(loaders);

		onData(scheduleByTitle(containsString("DebConf 23"))).perform(click());

		// Navigate to block schedule via the drawer. setScheduleView() calls days.show() so
		// the transpose button should be visible immediately.
		onView(withId(R.id.drawerLayout)).perform(DrawerActions.open());
		onView(withId(R.id.drawerScroll)).perform(swipeDown());
		onView(allOf(withId(R.id.block_schedule), withText(R.string.block_schedule))).perform(click());

		onView(withClassName(is("net.gaast.giggity.BlockSchedule"))).check(matches(isDisplayed()));
		onView(withId(R.id.transpose)).check(matches(isDisplayed()));

		// Tap transpose: switches to vertical and resets the 2s hide timer.
		onView(withId(R.id.transpose)).perform(click());
		onView(withClassName(is("net.gaast.giggity.BlockScheduleVertical"))).check(matches(isDisplayed()));
		onView(withId(R.id.transpose)).check(matches(isDisplayed()));

		// After the hide delay (2000ms in DayButtonsHider), the button container goes INVISIBLE.
		Thread.sleep(2500);
		onView(withId(R.id.transpose)).check(matches(not(isDisplayed())));

		Espresso.unregisterIdlingResources(loaders);
	}

	// Probably delete this, was included by the recorder but I'm not actually using it anymore.
	private static Matcher<View> childAtPosition(
			final Matcher<View> parentMatcher, final int position) {

		return new TypeSafeMatcher<View>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("Child at position " + position + " in parent ");
				parentMatcher.describeTo(description);
			}

			@Override
			public boolean matchesSafely(View view) {
				ViewParent parent = view.getParent();
				return parent instanceof ViewGroup && parentMatcher.matches(parent)
						       && view.equals(((ViewGroup) parent).getChildAt(position));
			}
		};
	}
}
