package net.gaast.giggity;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.espresso.DataInteraction;
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
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class Spresso {

	@Rule
	public ActivityScenarioRule<ChooserActivity> mActivityScenarioRule =
			new ActivityScenarioRule<>(ChooserActivity.class);

	@Test
	public void basicNav() {
		CountingIdlingResource loaders = new CountingIdlingResource("loaders");
		ScheduleViewActivity.setIdler(loaders);
		Espresso.registerIdlingResources(loaders);

//		DataInteraction relativeLayout = onData(anything())
//				                                 .inAdapterView(childAtPosition(
//						                                 withClassName(is("androidx.swiperefreshlayout.widget.SwipeRefreshLayout")),
//						                                 0))
//				                                 .atPosition(1);
		ViewInteraction relativeLayout = onView(withText("DebConf 23"));
		relativeLayout.perform(click());

		onView(withId(R.id.drawerLayout))
				.perform(DrawerActions.open());

		ViewInteraction textView = onView(
				allOf(withId(R.id.timetable), withText(R.string.timetable)));
		textView.perform(click());

		DataInteraction scheduleItemView = onData(anything())
				                                   .inAdapterView(childAtPosition(
						                                   withClassName(is("net.gaast.giggity.TimeTable")),
						                                   1))
				                                   .atPosition(4);
		scheduleItemView.perform(click());

		pressBack();

		DataInteraction scheduleItemView2 = onData(anything())
				                                    .inAdapterView(childAtPosition(
						                                    withClassName(is("net.gaast.giggity.TimeTable")),
						                                    1))
				                                    .atPosition(11);
		scheduleItemView2.perform(click());

		ViewInteraction dialog = onView(
				allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(is("Forming a SSH Cluster using old smartphones (Mobian and other  OSes) - Sustainable Computing")))));
		dialog.perform(click());

		ViewInteraction checkBox = onView(
				allOf(isDescendantOfA(allOf(withClassName(is("net.gaast.giggity.EventDialog")), hasDescendant(withText(is("Forming a SSH Cluster using old smartphones (Mobian and other  OSes) - Sustainable Computing"))))), withText(R.string.remind_me), withClassName(is("android.widget.CheckBox"))));
		checkBox.perform(click());

		checkBox.perform(click());

		pressBack();

		onView(withId(R.id.drawerLayout))
				.perform(DrawerActions.open());

		ViewInteraction textView2 = onView(
				allOf(withId(R.id.my_events), withText(R.string.my_events)));
		textView2.perform(click());

		onView(withId(R.id.drawerLayout))
				.perform(DrawerActions.open());

		ViewInteraction textView3 = onView(
				allOf(withId(R.id.search), withText(R.string.search)));
		textView3.perform(click());

		ViewInteraction searchQuery = onView(
				allOf(withClassName(is("net.gaast.giggity.ItemSearch$SearchQuery")),
						isDisplayed()));
		searchQuery.perform(replaceText("linux"), closeSoftKeyboard());

		DataInteraction scheduleItemView3 = onData(anything())
				                                    .inAdapterView(childAtPosition(
						                                    withClassName(is("net.gaast.giggity.ItemSearch")),
						                                    3))
				                                    .atPosition(4);
		scheduleItemView3.perform(click());

//		checkBox = onView(
//				allOf(isDisplayed(), withText(R.string.remind_me), withClassName(is("android.widget.CheckBox"))));
//		checkBox.perform(click());

		pressBack();

		onView(withId(R.id.drawerLayout))
				.perform(DrawerActions.open());

		ViewInteraction textView4 = onView(
				allOf(withId(R.id.my_events), withText(R.string.my_events
						),
						childAtPosition(
								allOf(withId(R.id.menu),
										childAtPosition(
												withId(R.id.scrollView),
												0)),
								5)));
		textView4.perform(click());

		ViewInteraction listView = onView(
				allOf(withParent(allOf(withId(R.id.viewerContainer),
								withParent(withId(R.id.bigScreen)))),
						isDisplayed()));
		listView.check(matches(isDisplayed()));
	}

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
