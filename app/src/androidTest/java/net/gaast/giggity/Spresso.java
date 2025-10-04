package net.gaast.giggity;

import android.Manifest;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
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
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
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
	public ActivityScenarioRule<ChooserActivity> mActivityScenarioRule =
			new ActivityScenarioRule<>(ChooserActivity.class);

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
		if (Build.VERSION.SDK_INT >= 30) {
			// (Weirdly docs claim API version 23 should already support this, yet my API 26 VM does not..)
			try {
				getInstrumentation().getUiAutomation().grantRuntimePermission(
				BuildConfig.APPLICATION_ID, Manifest.permission.POST_NOTIFICATIONS);
			} catch (SecurityException e) {
			}  // If we can't get it we probably don't yet need it?
		}

		CountingIdlingResource loaders = new CountingIdlingResource("loaders");
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
