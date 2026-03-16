package net.gaast.giggity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.widget.Button;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.InflaterInputStream;

import androidx.annotation.NonNull;

public class ImportView extends ScheduleListView implements ScheduleViewer {
	// Initial version of this file was AI-generated. The result is decent but also unsurprising.
	// Notable were the two cases of "catch Exception { log it and walk away haha }", including one
	// that was completely unnecessary anyway!
	// I guess it did still save me time, but ... as usual, really not loads.
	private final Schedule sched;
	private final Set<String> toSee = new HashSet<>();
	private final Set<String> toHide = new HashSet<>();

	// and ... hope I didn't forget anything?
	// Also, see what to do with huge # of deletions. Huge URLs are a bigger concern than UI maybe..
	// Try to get out the short IDs again which for almost every conf *are* unique.

	public ImportView(Context ctx, Schedule sched, String url) {
		super(ctx);
		this.sched = sched;
		// Override user preference this one time, in case some imported deletions already match ours.
		sched.setShowHidden(true);

		parseExportLink(url);
		refreshContents();
	}

	private void parseExportLink(String url) {
		Uri uri = Uri.parse(url.replaceFirst("#", "?"));
		toSee.addAll(decodeParam(uri.getQueryParameter("see")));
		toHide.addAll(decodeParam(uri.getQueryParameter("del")));
	}

	private Set<String> decodeParam(String param) {
		Set<String> ids = new HashSet<>();
		if (param == null || param.isEmpty()) return ids;

		try {
			byte[] compressed = Base64.decode(param, Base64.URL_SAFE | Base64.NO_PADDING);
			ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
			InflaterInputStream gis = new InflaterInputStream(bis);
			String jsonString = IOUtils.toString(gis, StandardCharsets.UTF_8);

			// 3. Parse JSON Array
			JSONArray array = new JSONArray(jsonString);
			for (int i = 0; i < array.length(); i++) {
				try {
					ids.add(Integer.toString(array.getInt(i)));
				} catch (JSONException e) {
					ids.add(array.getString(i));
				}
				ids.add(array.optString(i, ""));
			}
		} catch (IOException | JSONException e) {
			android.util.Log.e("Giggity", "Error decoding param: " + param, e);
		}

		return ids;
	}

	@Override
	public void refreshContents() {
		ArrayList fullList = new ArrayList();
		ArrayList<Schedule.Item> coming = new ArrayList<>();
		ArrayList<Schedule.Item> hiding = new ArrayList<>();

		for (Schedule.Line tent : sched.getTents()) {
			for (Schedule.Item item : tent.getItems()) {
				if (toSee.contains(item.getGuid()) || toSee.contains(item.getId())) {
					coming.add(item);
				}
				if (toHide.contains(item.getGuid()) || toHide.contains(item.getId())) {
					hiding.add(item);
				}
			}
		}

		if (coming.size() > 0) {
			fullList.add(ctx.getString(R.string.reminders));
			fullList.addAll(coming);
			Button importButton = getButton(toSee);
			fullList.add(importButton);
		}

		if (hiding.size() > 0) {
			String header = (coming.size() > 0 ? "\n" : "") + ctx.getString(R.string.hidden);
			fullList.add(header);
			fullList.addAll(hiding);
			Button importButton = getButton(toHide);
			fullList.add(importButton);
		}

		if (fullList.isEmpty()) {
			fullList.add(ctx.getString(R.string.no_items_to_show));
		}

		fullList.add("\n");  // Cheeky edge-to-edge workaround.

		setShowRemind(true);
		setList(fullList);
	}

	@NonNull
	private Button getButton(Set<String> which) {
		Button importButton = new Button(ctx);
		importButton.setText(R.string.import_all);
		importButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder dib = new AlertDialog.Builder(ctx);
				dib.setMessage(R.string.overwrite_or_merge_question);
				DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int btn) {
						if (btn == DialogInterface.BUTTON_NEUTRAL) {
							//
						} else if (which == toSee) {
							((ScheduleUI)sched).importReminders(which, btn == DialogInterface.BUTTON_NEGATIVE);
						} else if (which == toHide) {
							((ScheduleUI)sched).importDeletions(which, btn == DialogInterface.BUTTON_NEGATIVE);
						}
					}
				};
				dib.setNegativeButton(R.string.overwrite, l);
				dib.setNeutralButton(R.string.cancel, null);  // Nothing to do then. (Same result as tapping outside the dialog.)
				dib.setPositiveButton(R.string.merge, l);
				dib.create().show();
			}
		});
		return importButton;
	}

	@Override
	public boolean multiDay() {
		return true;
	}
}
