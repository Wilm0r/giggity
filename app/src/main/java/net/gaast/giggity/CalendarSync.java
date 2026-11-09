package net.gaast.giggity;

import android.Manifest;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.util.Log;

import java.time.ZoneId;
import java.util.ArrayList;

/* Mirrors the user's selected items into a calendar owned by Giggity's own account. Giggity has an account (no password
 * or anything) and in that, a calendar. Giggity acts as sync adapter for that account, so when the user deletes an
 * event in a calendar app, Giggity is told to sync and reacts by delecting it from "my events". As RFC 1925 says: You
 * can always add another level of indirection.
 */
public class CalendarSync {
        static final String ACCOUNT_TYPE = "net.gaast.giggity";
        // Not translated: changing it would orphan the calendar.
        private static final String ACCOUNT_NAME = "Giggity";

        private final Giggity app;
        private final Account account = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);

        public CalendarSync(Giggity app) {
                this.app = app;
        }

        public static boolean hasPermission(Context ctx) {
                return ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                       ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        }

        public boolean isEnabled() {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
                return pref.getBoolean("calendar_enabled", false) && hasPermission(app);
        }

        private Uri asSyncAdapter(Uri uri) {
                return uri.buildUpon()
                          .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                          .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                          .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
                          .build();
        }

        private long getCalendarId(boolean create) {
                ContentResolver cr = app.getContentResolver();
                try (Cursor q = cr.query(Calendars.CONTENT_URI,
                                         new String[]{Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME},
                                         Calendars.ACCOUNT_NAME + "=? AND " + Calendars.ACCOUNT_TYPE + "=?",
                                         new String[]{account.name, account.type}, null)) {
                        if (q != null && q.moveToFirst()) {
                                long id = q.getLong(0);
                                String name = app.getString(R.string.calendar_name);
                                if (!name.equals(q.getString(1))) {
                                        // The user changed the system language since we created the calendar.
                                        ContentValues row = new ContentValues();
                                        row.put(Calendars.CALENDAR_DISPLAY_NAME, name);
                                        cr.update(asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, id)),
                                                  row, null, null);
                                }
                                return id;
                        }
                }
                if (!create) {
                        return -1;
                }

                AccountManager.get(app).addAccountExplicitly(account, null, null);
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true);

                ContentValues row = new ContentValues();
                row.put(Calendars.ACCOUNT_NAME, account.name);
                row.put(Calendars.ACCOUNT_TYPE, account.type);
                row.put(Calendars.NAME, "giggity");
                row.put(Calendars.CALENDAR_DISPLAY_NAME, app.getString(R.string.calendar_name));
                row.put(Calendars.CALENDAR_COLOR, app.getColor(R.color.primary));
                row.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
                row.put(Calendars.OWNER_ACCOUNT, account.name);
                row.put(Calendars.VISIBLE, 1);
                row.put(Calendars.SYNC_EVENTS, 1);
                row.put(Calendars.ALLOWED_REMINDERS, "" + Reminders.METHOD_ALERT);
                row.put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().getId());
                Uri uri = cr.insert(asSyncAdapter(Calendars.CONTENT_URI), row);
                Log.i("CalendarSync", "Created calendar " + uri);
                return uri == null ? -1 : ContentUris.parseId(uri);
        }

        /* Returns the event's _ID, or -1 if there's none. Includes events the user has deleted but
         * that we haven't processed yet, so that we don't recreate them behind the user's back. */
        private long findEvent(long cal, String url, String guid, boolean[] deleted) {
                try (Cursor q = app.getContentResolver().query(
                                Events.CONTENT_URI, new String[]{Events._ID, Events.DELETED},
                                Events.CALENDAR_ID + "=? AND " + Events._SYNC_ID + "=? AND " + Events.SYNC_DATA1 + "=?",
                                new String[]{"" + cal, guid, url}, null)) {
                        if (q != null && q.moveToFirst()) {
                                deleted[0] = q.getInt(1) != 0;
                                return q.getLong(0);
                        }
                }
                return -1;
        }

        /** Adds, updates or removes the calendar event for one item. */
        public synchronized void update(Schedule.Item item) {
                if (!isEnabled()) {
                        return;
                }
                try {
                        long cal = getCalendarId(item.getRemind());
                        if (cal < 0) {
                                return;
                        }
                        ContentResolver cr = app.getContentResolver();
                        String url = item.getSchedule().getUrl();
                        boolean[] deleted = {false};
                        long ev = findEvent(cal, url, item.getGuid(), deleted);
                        if (deleted[0]) {
                                return;
                        }
                        if (!item.getRemind()) {
                                if (ev >= 0) {
                                        cr.delete(asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, ev)), null, null);
                                }
                                return;
                        }

                        ContentValues row = new ContentValues();
                        row.put(Events.TITLE, item.getTitle());
                        row.put(Events.DTSTART, item.getStartTime().getTime());
                        row.put(Events.DTEND, item.getEndTime().getTime());
                        row.put(Events.EVENT_TIMEZONE, item.getSchedule().getInTZ().getId());
                        if (item.getLine() != null) {
                                row.put(Events.EVENT_LOCATION, item.getLine().getTitle());
                        }
                        row.put(Events.DESCRIPTION, item.getDescriptionStripped());
                        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
                        boolean remind = pref.getBoolean("reminder_enabled", true);
                        row.put(Events.HAS_ALARM, remind ? 1 : 0);
                        if (ev >= 0) {
                                cr.update(asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, ev)), row, null, null);
                                // Giggity's settings win over reminders edited in a calendar app. This is sad for the user, who
                                // will however have to accept that the event organisers set the agenda.
                                cr.delete(asSyncAdapter(Reminders.CONTENT_URI), Reminders.EVENT_ID + "=?",
                                          new String[]{"" + ev});
                        } else {
                                row.put(Events.CALENDAR_ID, cal);
                                row.put(Events._SYNC_ID, item.getGuid());
                                row.put(Events.SYNC_DATA1, url);
                                Uri uri = cr.insert(asSyncAdapter(Events.CONTENT_URI), row);
                                ev = uri == null ? -1 : ContentUris.parseId(uri);
                        }
                        if (ev >= 0 && remind) {
                                row = new ContentValues();
                                row.put(Reminders.EVENT_ID, ev);
                                row.put(Reminders.METHOD, Reminders.METHOD_ALERT);
                                row.put(Reminders.MINUTES, Integer.parseInt(pref.getString("reminder_period", "5")));
                                cr.insert(asSyncAdapter(Reminders.CONTENT_URI), row);
                        }
                } catch (SecurityException e) {
                        Log.w("CalendarSync", "Lost calendar permission? " + e);
                }
        }

        /** Removes events for items that are gone from the schedule or no longer selected. */
        public synchronized void reconcile(Schedule sched) {
                if (!isEnabled()) {
                        return;
                }
                try {
                        long cal = getCalendarId(false);
                        if (cal < 0) {
                                return;
                        }
                        ContentResolver cr = app.getContentResolver();
                        ArrayList<Long> stale = new ArrayList<>();
                        try (Cursor q = cr.query(Events.CONTENT_URI, new String[]{Events._ID, Events._SYNC_ID},
                                                 Events.CALENDAR_ID + "=? AND " + Events.SYNC_DATA1 + "=? AND " +
                                                 Events.DELETED + "=0",
                                                 new String[]{"" + cal, sched.getUrl()}, null)) {
                                while (q != null && q.moveToNext()) {
                                        Schedule.Item item = sched.getItem(q.getString(1));
                                        if (item == null || !item.getRemind()) {
                                                stale.add(q.getLong(0));
                                        }
                                }
                        }
                        for (long ev : stale) {
                                cr.delete(asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, ev)), null, null);
                        }
                } catch (SecurityException e) {
                        Log.w("CalendarSync", "Lost calendar permission? " + e);
                }
        }

        /** Picks up the user's changes in calendar apps. A deleted event deselects its item. */
        public synchronized void processDirty() {
                if (!hasPermission(app)) {
                        return;
                }
                try {
                        long cal = getCalendarId(false);
                        if (cal < 0) {
                                return;
                        }
                        ContentResolver cr = app.getContentResolver();
                        ArrayList<Long> ids = new ArrayList<>();
                        ArrayList<Boolean> deleted = new ArrayList<>();
                        try (Cursor q = cr.query(Events.CONTENT_URI,
                                                 new String[]{Events._ID, Events.DELETED, Events._SYNC_ID, Events.SYNC_DATA1},
                                                 Events.CALENDAR_ID + "=? AND " + Events.DIRTY + "=1",
                                                 new String[]{"" + cal}, null)) {
                                while (q != null && q.moveToNext()) {
                                        ids.add(q.getLong(0));
                                        deleted.add(q.getInt(1) != 0);
                                        if (q.getInt(1) != 0 && q.getString(2) != null && q.getString(3) != null) {
                                                deselect(q.getString(3), q.getString(2));
                                        }
                                }
                        }
                        for (int i = 0; i < ids.size(); i++) {
                                Uri uri = asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, ids.get(i)));
                                if (deleted.get(i)) {
                                        cr.delete(uri, null, null);
                                } else {
                                        ContentValues row = new ContentValues();
                                        row.put(Events.DIRTY, 0);
                                        cr.update(uri, row, null, null);
                                }
                        }
                } catch (SecurityException e) {
                        Log.w("CalendarSync", "Lost calendar permission? " + e);
                }
        }

        private void deselect(final String url, final String guid) {
                Log.i("CalendarSync", "Deleted in calendar app: " + url + " " + guid);
                app.getDb().clearRemind(url, guid);
                new Handler(Looper.getMainLooper()).post(() -> {
                        ScheduleUI sched = app.getCachedSchedule(url);
                        Schedule.Item item = sched == null ? null : sched.getItem(guid);
                        if (item != null) {
                                item.setRemind(false);
                        }
                });
        }

        /** Creates the calendar and fills it with the selected items of loaded schedules. */
        public void enable() {
                if (!isEnabled()) {
                        return;
                }
                try {
                        getCalendarId(true);
                } catch (SecurityException e) {
                        Log.w("CalendarSync", "Lost calendar permission? " + e);
                        return;
                }
                for (Schedule.Item item : app.getRemindItems()) {
                        update(item);
                }
        }

        /** Removing the account makes the provider drop the calendar and its events. */
        public synchronized void disable() {
                if (hasPermission(app)) {
                        try {
                                app.getContentResolver().delete(asSyncAdapter(Calendars.CONTENT_URI),
                                                                Calendars.ACCOUNT_NAME + "=? AND " + Calendars.ACCOUNT_TYPE + "=?",
                                                                new String[]{account.name, account.type});
                        } catch (SecurityException e) {
                                Log.w("CalendarSync", "Lost calendar permission? " + e);
                        }
                }
                AccountManager.get(app).removeAccountExplicitly(account);
        }

        public static class SyncService extends Service {
                private static SyncAdapter adapter;

                @Override
                public void onCreate() {
                        synchronized (SyncService.class) {
                                if (adapter == null) {
                                        adapter = new SyncAdapter(getApplicationContext());
                                }
                        }
                }

                @Override
                public IBinder onBind(Intent intent) {
                        return adapter.getSyncAdapterBinder();
                }
        }

        private static class SyncAdapter extends AbstractThreadedSyncAdapter {
                SyncAdapter(Context ctx) {
                        super(ctx, true);
                }

                @Override
                public void onPerformSync(Account account, Bundle extras, String authority,
                                          ContentProviderClient provider, SyncResult syncResult) {
                        ((Giggity) getContext().getApplicationContext()).calendar.processDirty();
                }
        }

        public static class AuthenticatorService extends Service {
                @Override
                public IBinder onBind(Intent intent) {
                        return new Authenticator(this).getIBinder();
                }
        }

        /* The account has no credentials. "Add account" in the system settings opens our own
         * settings, with the checkbox to enable/disable the account. */
        private static class Authenticator extends AbstractAccountAuthenticator {
                private final Context ctx;

                Authenticator(Context ctx) {
                        super(ctx);
                        this.ctx = ctx;
                }

                @Override
                public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
                                         String authTokenType, String[] requiredFeatures, Bundle options) {
                        Bundle ret = new Bundle();
                        ret.putParcelable(AccountManager.KEY_INTENT, new Intent(ctx, SettingsActivity.class));
                        return ret;
                }

                @Override
                public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
                        return null;
                }

                @Override
                public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
                        return null;
                }

                @Override
                public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                                           String authTokenType, Bundle options) {
                        return null;
                }

                @Override
                public String getAuthTokenLabel(String authTokenType) {
                        return null;
                }

                @Override
                public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                                                String authTokenType, Bundle options) {
                        return null;
                }

                @Override
                public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) {
                        Bundle ret = new Bundle();
                        ret.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
                        return ret;
                }
        }
}
