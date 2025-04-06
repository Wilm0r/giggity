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

import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.NonNull;

public class SettingsActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

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
	public void onDestroy() {
		/* Maybe alarms were disabled or the period was changed. */
		((Giggity)getApplication()).updateRemind();

		Log.d("prefs", "onDestroy");
		super.onDestroy();
	}
}
