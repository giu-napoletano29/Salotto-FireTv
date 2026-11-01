/*
 * Homestead - a replacement home screen for Fire TV.
 * Copyright (C) 2026 Homestead contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. It is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.homestead.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User preferences, read synchronously on the main thread (the values are small).
 *
 * Hidden tiles are keyed by AppModel.Item.key(), so inputs and the Amazon Home tile can be
 * hidden as well as apps.
 */
public final class Prefs {

    private static final String FILE = "settings";

    private static final String K_HIDDEN = "hidden";
    private static final String K_SORT = "sort";
    private static final String K_COLUMNS = "columns";
    private static final String K_SEC_INPUTS = "sec_inputs";
    private static final String K_SEC_NONTV = "sec_nontv";
    private static final String K_CLOCK = "clock";
    private static final String K_BACKGROUND = "background";
    private static final String K_BACKGROUND_ID = "background_id";
    private static final String K_PHOTO_DIM = "photo_dim";
    private static final String K_ADB = "adb_enabled";
    private static final String K_TUNNEL = "adb_tunnel_enabled";
    private static final String K_REAPPLY = "adb_reapply_on_boot";
    private static final String K_RESTORE_WATCH = "restore_watch";
    private static final String K_IDLE = "idle_minutes";
    private static final String K_SHORTCUTS_PLACE = "shortcuts_place";
    private static final String K_RAIL_PHONE = "rail_phone";
    private static final String K_SHORTCUTS = "shortcuts";
    private static final String K_DISABLED = "disabled_by_us";
    private static final String K_REFUSED = "refused_by_fire_os";
    private static final String K_HOME_OFF = "home_off_by_user";
    private static final String K_UPDATE_STARTED = "update_started_at";
    private static final String K_SETUP_SEEN = "setup_seen";
    private static final String K_ONBOARDED = "onboarded";
    private static final String K_UPD_LASTCHECK = "update_last_check";
    private static final String K_UPD_SKIP = "update_skipped_version";
    private static final String K_UPD_NOTIFIED = "update_notified_version";
    private static final String K_UPD_AUTO = "update_auto_check";
    private static final String K_TV_FIRST = "livetv_first";
    private static final String K_GREETING = "greeting";
    private static final String K_DATE = "date_format";
    private static final String K_NONTV_WIDE = "nontv_wide";
    private static final String K_FIRE_ICONS = "fire_os_icons";
    private static final String K_DOCK_SIZE = "dock_size";

    public static final int SORT_LAST_USED = 0;
    public static final int SORT_ALPHA = 1;

    public static final int CLOCK_24H = 0;
    public static final int CLOCK_12H = 1;
    public static final int CLOCK_OFF = 2;

    public static final int DATE_OFF = 0;
    /** Weekday, day and month, in the order the TV's language uses. */
    public static final int DATE_LONG = 1;
    /** The TV's short numeric date. */
    public static final int DATE_SHORT = 2;

    private Prefs() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- hidden tiles

    /** Returns a copy: a Set from SharedPreferences must not be mutated. */
    public static Set<String> hidden(Context c) {
        Set<String> s = sp(c).getStringSet(K_HIDDEN, Collections.<String>emptySet());
        return new HashSet<String>(s);
    }

    public static boolean isHidden(Context c, String key) {
        if (key == null) return false;
        return sp(c).getStringSet(K_HIDDEN, Collections.<String>emptySet()).contains(key);
    }

    public static void setHidden(Context c, String key, boolean hide) {
        if (key == null) return;
        Set<String> s = hidden(c);
        if (hide) s.add(key); else s.remove(key);
        sp(c).edit().putStringSet(K_HIDDEN, s).apply();
    }

    public static void clearHidden(Context c) {
        sp(c).edit().putStringSet(K_HIDDEN, new HashSet<String>()).apply();
    }

    public static int hiddenCount(Context c) {
        return sp(c).getStringSet(K_HIDDEN, Collections.<String>emptySet()).size();
    }

    // ---------------------------------------------------------------- layout

    public static int sort(Context c) { return sp(c).getInt(K_SORT, SORT_LAST_USED); }
    public static void setSort(Context c, int v) { sp(c).edit().putInt(K_SORT, v).apply(); }

    public static int columns(Context c) {
        int n = sp(c).getInt(K_COLUMNS, 6);
        if (n < 4) n = 4;
        if (n > 8) n = 8;
        return n;
    }
    public static void setColumns(Context c, int v) { sp(c).edit().putInt(K_COLUMNS, v).apply(); }

    /** Index into DOCK_SIZE_NAMES (HomeGrid maps it to a tile height). Medium by default. */
    public static final String[] DOCK_SIZE_NAMES = { "Small", "Medium", "Large", "Extra large" };
    /** The name of a dock size in the TV's language; DOCK_SIZE_NAMES stays the English key. */
    public static String dockSizeName(Context c, int i) {
        switch (i) {
            case 0: return c.getString(R.string.dk_small);
            case 2: return c.getString(R.string.dk_large);
            case 3: return c.getString(R.string.dk_xlarge);
            default: return c.getString(R.string.dk_medium);
        }
    }
    public static int dockSize(Context c) {
        int n = sp(c).getInt(K_DOCK_SIZE, 1);
        return (n < 0 || n >= DOCK_SIZE_NAMES.length) ? 1 : n;
    }
    public static void setDockSize(Context c, int v) { sp(c).edit().putInt(K_DOCK_SIZE, v).apply(); }

    public static boolean showInputs(Context c) { return sp(c).getBoolean(K_SEC_INPUTS, true); }
    public static void setShowInputs(Context c, boolean v) { sp(c).edit().putBoolean(K_SEC_INPUTS, v).apply(); }

    public static boolean showNonTv(Context c) { return sp(c).getBoolean(K_SEC_NONTV, true); }
    public static void setShowNonTv(Context c, boolean v) { sp(c).edit().putBoolean(K_SEC_NONTV, v).apply(); }

    /** A Backgrounds.Preset id or Backgrounds.PHOTO_ID; null if the choice predates ids. */
    public static String backgroundId(Context c) { return sp(c).getString(K_BACKGROUND_ID, null); }
    /** How 1.6.4 and earlier saved it: a position in that version's list (4, Black, if unset). */
    public static int backgroundPosition(Context c) { return sp(c).getInt(K_BACKGROUND, 4); }
    /** The id, and the old position too unless it is -1 (a look those versions did not have). */
    public static void setBackground(Context c, String id, int oldPosition) {
        SharedPreferences.Editor e = sp(c).edit().putString(K_BACKGROUND_ID, id);
        if (oldPosition >= 0) e.putInt(K_BACKGROUND, oldPosition);
        e.apply();
    }

    /** Index into Backgrounds.DIM_NAMES. Light by default, so labels stay readable. */
    public static int photoDim(Context c) { return sp(c).getInt(K_PHOTO_DIM, 1); }
    public static void setPhotoDim(Context c, int v) { sp(c).edit().putInt(K_PHOTO_DIM, v).apply(); }

    public static int clockMode(Context c) { return sp(c).getInt(K_CLOCK, CLOCK_24H); }
    public static void setClockMode(Context c, int v) { sp(c).edit().putInt(K_CLOCK, v).apply(); }

    // ---------------------------------------------------------------- the ADB link

    /** Off by default: the first connection raises the system authorisation dialog. */
    public static boolean adbEnabled(Context c) { return sp(c).getBoolean(K_ADB, false); }
    public static void setAdbEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean(K_ADB, v).apply();
    }

    /**
     * Route the ADB link through the on-device VPN tunnel (TunnelVpnService). Off by
     * default and set only by explicit user action; once set, later connects bring the
     * tunnel up automatically since the system remembers VPN consent.
     */
    public static boolean tunnelEnabled(Context c) { return sp(c).getBoolean(K_TUNNEL, false); }
    public static void setTunnelEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean(K_TUNNEL, v).apply();
    }

    /** Re-apply package choices after reboot; some are reverted by the system. */
    /** The user switched the Home button off from the app; cleared when it comes back on. */
    public static boolean homeOffByUser(Context c) { return sp(c).getBoolean(K_HOME_OFF, false); }
    public static void setHomeOffByUser(Context c, boolean v) {
        sp(c).edit().putBoolean(K_HOME_OFF, v).apply();
    }

    /** When the in-app updater last started an install (wall clock), 0 if none pending. */
    public static long updateStarted(Context c) { return sp(c).getLong(K_UPDATE_STARTED, 0L); }
    public static void setUpdateStarted(Context c, long at) {
        sp(c).edit().putLong(K_UPDATE_STARTED, at).commit();   // must survive the replace
    }

    public static boolean reapplyOnBoot(Context c) { return sp(c).getBoolean(K_REAPPLY, true); }
    public static void setReapplyOnBoot(Context c, boolean v) {
        sp(c).edit().putBoolean(K_REAPPLY, v).apply();
    }

    public static boolean setupSeen(Context c) { return sp(c).getBoolean(K_SETUP_SEEN, false); }
    public static void setSetupSeen(Context c, boolean v) {
        sp(c).edit().putBoolean(K_SETUP_SEEN, v).apply();
    }

    /** Whether the first-run wizard has been completed or skipped. */
    public static boolean onboarded(Context c) { return sp(c).getBoolean(K_ONBOARDED, false); }
    public static void setOnboarded(Context c, boolean v) {
        sp(c).edit().putBoolean(K_ONBOARDED, v).apply();
    }

    /**
     * Live TV first: a large Live TV tile on top, then TV apps, then inputs; Home lands on
     * Live TV. Off by default; it only changes anything on a TV with a tuner.
     */
    public static boolean liveTvFirst(Context c) { return sp(c).getBoolean(K_TV_FIRST, false); }
    public static void setLiveTvFirst(Context c, boolean v) {
        sp(c).edit().putBoolean(K_TV_FIRST, v).apply();
    }

    /** "Good evening" at the top left. Off by default. */
    public static boolean greeting(Context c) { return sp(c).getBoolean(K_GREETING, false); }
    public static void setGreeting(Context c, boolean v) { sp(c).edit().putBoolean(K_GREETING, v).apply(); }

    /** The date at the top left, one of DATE_*. Off by default. */
    public static int dateFormat(Context c) { return sp(c).getInt(K_DATE, DATE_OFF); }
    public static void setDateFormat(Context c, int v) { sp(c).edit().putInt(K_DATE, v).apply(); }

    /** Non-TV apps as banner-sized tiles built from their icon, as Fire TV's own home does. */
    public static boolean nonTvWide(Context c) { return sp(c).getBoolean(K_NONTV_WIDE, false); }
    public static void setNonTvWide(Context c, boolean v) { sp(c).edit().putBoolean(K_NONTV_WIDE, v).apply(); }

    /** Amazon Home, Settings and TV Health use icons read from Fire OS's own apps. */
    public static boolean fireOsIcons(Context c) { return sp(c).getBoolean(K_FIRE_ICONS, false); }
    public static void setFireOsIcons(Context c, boolean v) { sp(c).edit().putBoolean(K_FIRE_ICONS, v).apply(); }

    /**
     * Tell me when the system undoes my changes: after an update Fire OS can switch back on
     * a package Debloat turned off, and a tile on top of the home screen offers to redo it.
     * Off by default.
     */
    /** Quick Fire TV settings in the top bar: off, or on (SHORTCUTS_BAR; the default here). */
    public static final int SHORTCUTS_OFF = 0;
    public static final int SHORTCUTS_BAR = 3;

    public static int shortcutsPlace(Context c) {
        return sp(c).getInt(K_SHORTCUTS_PLACE, SHORTCUTS_BAR) == SHORTCUTS_OFF
                ? SHORTCUTS_OFF : SHORTCUTS_BAR;
    }
    public static void setShortcutsPlace(Context c, int v) {
        sp(c).edit().putInt(K_SHORTCUTS_PLACE, v).apply();
    }

    /** The phone-control icon at the bottom of the side rail; on until switched off. */
    public static boolean railPhone(Context c) { return sp(c).getBoolean(K_RAIL_PHONE, true); }
    public static void setRailPhone(Context c, boolean v) { sp(c).edit().putBoolean(K_RAIL_PHONE, v).apply(); }

    /** Screen ids (SystemSettingsPanel) in the bar; four until chosen. */
    public static Set<String> shortcuts(Context c) {
        Set<String> s = sp(c).getStringSet(K_SHORTCUTS, null);
        if (s != null) return new HashSet<String>(s);
        Set<String> d = new HashSet<String>();
        d.add("network");
        d.add("controllers");
        d.add("applications");
        d.add("device");
        return d;
    }
    public static void setShortcut(Context c, String id, boolean on) {
        Set<String> s = shortcuts(c);
        if (on) s.add(id); else s.remove(id);
        sp(c).edit().putStringSet(K_SHORTCUTS, s).apply();
    }

    /** Minutes without a button press before the idle clock covers the home screen; 0 is never. */
    public static final int[] IDLE_CHOICES = { 0, 2, 5, 10, 15, 30 };
    public static int idleMinutes(Context c) { return sp(c).getInt(K_IDLE, 5); }
    public static void setIdleMinutes(Context c, int v) { sp(c).edit().putInt(K_IDLE, v).apply(); }

    /**
     * What a button of a TV remote does (KeyBindings), by its HDMI-CEC code; Red is Home until
     * changed. The four coloured buttons were once kept by Android key code: that is still read
     * where the new key has no value.
     */
    public static String tvCec(Context c, int cec) {
        String v = sp(c).getString("tvcec_" + cec, null);
        if (v != null) return v;
        int old = KeyBindings.legacyKey(cec);
        if (old >= 0) return sp(c).getString("tvkey_" + old, cec == 0x72 ? "home" : "off");
        return "off";
    }
    public static void setTvCec(Context c, int cec, String action) {
        sp(c).edit().putString("tvcec_" + cec, action).apply();
    }

    /** The buttons added by pressing them (KeyBindings), as CEC codes. */
    public static java.util.List<Integer> tvCecList(Context c) {
        java.util.List<Integer> out = new java.util.ArrayList<Integer>();
        for (String s : sp(c).getStringSet("tvcec_list", new java.util.HashSet<String>())) {
            try { out.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) { }
        }
        return out;
    }
    public static void addTvCec(Context c, int cec) {
        Set<String> s = new java.util.HashSet<String>(
                sp(c).getStringSet("tvcec_list", new java.util.HashSet<String>()));
        s.add(String.valueOf(cec));
        sp(c).edit().putStringSet("tvcec_list", s).apply();
    }
    public static void removeTvCec(Context c, int cec) {
        Set<String> s = new java.util.HashSet<String>(
                sp(c).getStringSet("tvcec_list", new java.util.HashSet<String>()));
        s.remove(String.valueOf(cec));
        sp(c).edit().putStringSet("tvcec_list", s).apply();
    }

    public static boolean restoreWatch(Context c) { return sp(c).getBoolean(K_RESTORE_WATCH, false); }
    public static void setRestoreWatch(Context c, boolean v) {
        sp(c).edit().putBoolean(K_RESTORE_WATCH, v).apply();
    }

    // ---------------------------------------------------------------- phone remote

    private static final String K_REMOTE = "remote_on";
    private static final String K_REMOTE_TOKENS = "remote_sessions";
    private static final String K_REMOTE_DAYS = "remote_session_days";
    /** Phones kept signed in; the oldest is dropped when one more signs in. */
    private static final int REMOTE_TOKENS_MAX = 8;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /** How long a phone stays signed in after pairing, in days; 0 is until it is signed out. */
    public static final int[] REMOTE_SESSION_CHOICES = { 1, 7, 30, 0 };

    /** The phone remote (RemoteServer) listens only while this is on; off until chosen. */
    public static boolean remoteOn(Context c) { return sp(c).getBoolean(K_REMOTE, false); }
    public static void setRemoteOn(Context c, boolean v) {
        sp(c).edit().putBoolean(K_REMOTE, v).apply();
    }

    public static int remoteSessionDays(Context c) { return sp(c).getInt(K_REMOTE_DAYS, 7); }
    public static void setRemoteSessionDays(Context c, int days) {
        sp(c).edit().putInt(K_REMOTE_DAYS, days).apply();
    }

    /**
     * When the session of this token ends, in epoch millis: 0 if it never does, -1 if the token
     * is not a session (unknown, or ended). Every stored token is compared, so the time does not
     * tell where a guess went wrong.
     */
    public static synchronized long remoteTokenExpiry(Context c, String token) {
        if (token == null || token.length() < 32) return -1L;
        long now = System.currentTimeMillis();
        long found = -1L;
        for (String[] e : remoteSessionList(c)) {
            long until = parseLong(e[1]);
            boolean same = e[0].length() == token.length()
                    && java.security.MessageDigest.isEqual(e[0].getBytes(), token.getBytes());
            if (same && (until == 0L || until > now)) found = until;
        }
        return found;
    }

    /** Phones signed in now. */
    public static synchronized int remoteSessions(Context c) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (String[] e : remoteSessionList(c)) {
            long until = parseLong(e[1]);
            if (until == 0L || until > now) n++;
        }
        return n;
    }

    /** Starts a session that lasts as long as the setting says. */
    public static synchronized void addRemoteToken(Context c, String token) {
        int days = remoteSessionDays(c);
        long until = days <= 0 ? 0L : System.currentTimeMillis() + days * DAY_MS;
        java.util.List<String[]> list = remoteSessionList(c);
        list.add(new String[]{ token, String.valueOf(until) });
        while (list.size() > REMOTE_TOKENS_MAX) list.remove(0);
        saveSessions(c, list);
    }

    public static synchronized void removeRemoteToken(Context c, String token) {
        java.util.List<String[]> list = remoteSessionList(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i)[0].equals(token)) list.remove(i);
        }
        saveSessions(c, list);
    }

    public static synchronized void clearRemoteTokens(Context c) {
        sp(c).edit().remove(K_REMOTE_TOKENS).apply();
    }

    /** The stored sessions without the ended ones, as {token, expiry}. */
    private static java.util.List<String[]> remoteSessionList(Context c) {
        long now = System.currentTimeMillis();
        java.util.List<String[]> out = new java.util.ArrayList<String[]>();
        for (String part : sp(c).getString(K_REMOTE_TOKENS, "").split(",")) {
            String[] e = part.split(":");
            if (e.length != 2 || e[0].length() < 32) continue;
            long until = parseLong(e[1]);
            if (until == 0L || until > now) out.add(e);
        }
        return out;
    }

    private static void saveSessions(Context c, java.util.List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(i > 0 ? "," : "").append(list.get(i)[0]).append(':').append(list.get(i)[1]);
        }
        sp(c).edit().putString(K_REMOTE_TOKENS, sb.toString()).apply();
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1L; }
    }

    // ---------------------------------------------------------------- updates

    public static boolean autoUpdateCheck(Context c) { return sp(c).getBoolean(K_UPD_AUTO, true); }
    public static void setAutoUpdateCheck(Context c, boolean v) {
        sp(c).edit().putBoolean(K_UPD_AUTO, v).apply();
    }

    /** Epoch millis of the last automatic check; used to throttle to once a day. */
    public static long lastUpdateCheck(Context c) { return sp(c).getLong(K_UPD_LASTCHECK, 0L); }
    public static void setLastUpdateCheck(Context c, long v) {
        sp(c).edit().putLong(K_UPD_LASTCHECK, v).apply();
    }

    /** A version the user chose to skip. */
    public static String skippedVersion(Context c) { return sp(c).getString(K_UPD_SKIP, ""); }
    public static void setSkippedVersion(Context c, String v) {
        sp(c).edit().putString(K_UPD_SKIP, v == null ? "" : v).apply();
    }

    /** The last version auto-prompted for, so each version prompts only once. */
    public static String notifiedVersion(Context c) { return sp(c).getString(K_UPD_NOTIFIED, ""); }
    public static void setNotifiedVersion(Context c, String v) {
        sp(c).edit().putString(K_UPD_NOTIFIED, v == null ? "" : v).apply();
    }

    // ------------------------------------------------------ packages we disabled

    /** Packages this app disabled, so "undo everything" restores only those. */
    public static Set<String> disabledByUs(Context c) {
        return new HashSet<String>(
                sp(c).getStringSet(K_DISABLED, Collections.<String>emptySet()));
    }

    public static void noteDisabled(Context c, String pkg, boolean disabled) {
        if (pkg == null) return;
        Set<String> s = disabledByUs(c);
        if (disabled) s.add(pkg); else s.remove(pkg);
        sp(c).edit().putStringSet(K_DISABLED, s).apply();
    }

    /** Packages Fire OS refused to switch off when we tried, for TV Health's breakdown. */
    public static Set<String> refusedByFireOs(Context c) {
        return new HashSet<String>(
                sp(c).getStringSet(K_REFUSED, Collections.<String>emptySet()));
    }

    public static void noteRefused(Context c, String pkg, boolean refused) {
        if (pkg == null) return;
        Set<String> s = refusedByFireOs(c);
        if (refused) s.add(pkg); else s.remove(pkg);
        sp(c).edit().putStringSet(K_REFUSED, s).apply();
    }

    public static void clearDisabledRecord(Context c) {
        sp(c).edit().putStringSet(K_DISABLED, new HashSet<String>()).apply();
    }
}
