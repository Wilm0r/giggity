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

import android.Manifest;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.NonNull;

public class SettingsActivity extends PreferenceActivity {
	private static final int CALENDAR_PERMISSION = 0xca1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		findPreference("calendar_enabled").setOnPreferenceChangeListener((pref, value) -> {
			if (!(Boolean) value) {
				setCalendar(false);
			} else if (CalendarSync.hasPermission(this)) {
				setCalendar(true);
			} else {
				requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,
				                                Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION);
				return false;
			}
			return true;
		});

		if (Build.VERSION.SDK_INT >= 30) {
			getListView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
				@NonNull
				@Override
				public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
					DisplayCutout cut = null;
					Insets r = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
					getListView().setPadding(r.left, r.top, r.right, r.bottom);
					getListView().setClipToPadding(false);

					return insets;
				}
			});
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
	                                       @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == CALENDAR_PERMISSION && CalendarSync.hasPermission(this)) {
			((CheckBoxPreference) findPreference("calendar_enabled")).setChecked(true);
			setCalendar(true);
		}
	}

	private void setCalendar(boolean on) {
		// The listener runs before the preference is saved, and CalendarSync reads it.
		PreferenceManager.getDefaultSharedPreferences(this).edit()
		                 .putBoolean("calendar_enabled", on).commit();
		final CalendarSync cal = ((Giggity) getApplication()).calendar;
		new Thread(() -> {
			if (on) {
				cal.enable();
			} else {
				cal.disable();
			}
		}).start();
	}

	@Override 
	public void onDestroy() {
		/* Maybe alarms were disabled or the period was changed. */
		((Giggity)getApplication()).updateRemind();

		Log.d("prefs", "onDestroy");
		super.onDestroy();
	}
}
