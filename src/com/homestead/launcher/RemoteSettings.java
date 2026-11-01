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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The settings the phone remote can change, as a list the phone's page draws forms from, and
 * the one place that applies a change. Only what is listed here can be changed from the
 * phone, and a value is checked against its options before it is used.
 *
 * Each entry does what the same row on the TV's own Settings pages does: it writes the same
 * preference and then redraws the home screen. Main thread only.
 */
final class RemoteSettings {

    private RemoteSettings() { }

    static final String OK = "ok", BAD = "bad", PENDING = "pending", NEEDS_LINK = "needs_link";

    // ---------------------------------------------------------------- the list

    /** {"groups":[{"title","items":[{"id","type","label","value","options","help"}]}]}. */
    static String schema(Context c) throws JSONException {
        JSONArray groups = new JSONArray();

        // ---- look
        JSONArray look = new JSONArray();
        JSONArray bgs = new JSONArray();
        for (int i = 0; i < Backgrounds.PRESETS.length; i++) {
            bgs.put(opt(Backgrounds.PRESETS[i].id, Backgrounds.PRESETS[i].name));
        }
        for (int i = 0; i < Backgrounds.ARTS.length; i++) {
            bgs.put(opt(Backgrounds.ARTS[i].id, Backgrounds.ARTS[i].name));
        }
        String bgNow = Backgrounds.currentId(c);
        // A photo is chosen on the TV; it is only listed while it is the one in use.
        if (Backgrounds.PHOTO_ID.equals(bgNow)) bgs.put(opt(Backgrounds.PHOTO_ID, "Photo"));
        look.put(choice("background", "Background", bgNow, bgs, null));

        JSONArray cols = new JSONArray();
        for (int n = Prefs.COLUMNS_MIN; n <= Prefs.COLUMNS_MAX; n++) cols.put(opt(String.valueOf(n), String.valueOf(n)));
        look.put(choice("columns", "Grid columns", String.valueOf(Prefs.columns(c)), cols, null));

        JSONArray dock = new JSONArray();
        for (int i = 0; i < Prefs.DOCK_SIZE_NAMES.length; i++) {
            dock.put(opt(String.valueOf(i), Prefs.DOCK_SIZE_NAMES[i]));
        }
        look.put(choice("dock", "Dock size", String.valueOf(Prefs.dockSize(c)), dock, null));

        JSONArray clock = new JSONArray();
        clock.put(opt(String.valueOf(Prefs.CLOCK_24H), "24-hour"));
        clock.put(opt(String.valueOf(Prefs.CLOCK_12H), "12-hour"));
        clock.put(opt(String.valueOf(Prefs.CLOCK_OFF), "Off"));
        look.put(choice("clock", "Clock", String.valueOf(Prefs.clockMode(c)), clock, null));

        JSONArray date = new JSONArray();
        date.put(opt(String.valueOf(Prefs.DATE_OFF), "Off"));
        date.put(opt(String.valueOf(Prefs.DATE_LONG), "Weekday and month"));
        date.put(opt(String.valueOf(Prefs.DATE_SHORT), "Numbers"));
        look.put(choice("date", "Date", String.valueOf(Prefs.dateFormat(c)), date, null));

        look.put(sw("greeting", "Greeting", Prefs.greeting(c), null));
        look.put(sw("wide", "Non-TV apps as wide tiles", Prefs.nonTvWide(c), null));
        look.put(sw("fireicons", "Fire TV system icons", Prefs.fireOsIcons(c), null));
        groups.put(group("Look", look));

        // ---- what is shown
        JSONArray content = new JSONArray();
        JSONArray sort = new JSONArray();
        sort.put(opt(String.valueOf(Prefs.SORT_LAST_USED), "Last used"));
        sort.put(opt(String.valueOf(Prefs.SORT_ALPHA), "A - Z"));
        content.put(choice("sort", "Sort apps by", String.valueOf(Prefs.sort(c)), sort, null));
        if (AppModel.hasLiveTv()) content.put(sw("tvfirst", "Live TV first", Prefs.liveTvFirst(c), null));
        content.put(sw("inputs", "Show \"Inputs & System\"", Prefs.showInputs(c), null));
        content.put(sw("nontv", "Show \"Non-TV Applications\"", Prefs.showNonTv(c), null));
        content.put(hiddenApps(c));
        groups.put(group("Content", content));

        // ---- behaviour
        JSONArray behaviour = new JSONArray();
        JSONArray idle = new JSONArray();
        for (int i = 0; i < Prefs.IDLE_CHOICES.length; i++) {
            idle.put(opt(String.valueOf(Prefs.IDLE_CHOICES[i]), SettingsPanel.idleLabel(c, Prefs.IDLE_CHOICES[i])));
        }
        behaviour.put(choice("idle", "Idle clock", String.valueOf(Prefs.idleMinutes(c)), idle, null));
        groups.put(group("Behaviour", behaviour));

        // ---- the TV itself
        JSONArray tv = new JSONArray();
        JSONArray saver = new JSONArray();
        saver.put(opt("Colours", "Colours"));
        saver.put(opt("Off", "Off"));
        saver.put(opt("Amazon's", "Amazon's (with ads)"));
        String now = DisplaySoundPanel.screensaverNow(c);
        tv.put(choice("screensaver", "Screensaver", now, saver, null));
        tv.put(sw("sounds", "Interface sounds", DisplaySoundPanel.soundsOn(c), null));
        if (DisplaySoundPanel.ledAvailable(c)) tv.put(sw("led", "Power LED", DisplaySoundPanel.ledOn(c), null));
        groups.put(group("TV", tv));

        return new JSONObject().put("groups", groups).toString();
    }

    /** Every app on the grid with a switch for hiding it. */
    private static JSONObject hiddenApps(Context c) throws JSONException {
        JSONArray opts = new JSONArray();
        List<AppModel.Item> items = AppModel.allItems();
        for (int i = 0; i < items.size(); i++) {
            AppModel.Item it = items.get(i);
            if (it.type != AppModel.TYPE_APP || it.pkg == null || it.label == null) continue;
            opts.put(new JSONObject().put("v", it.key()).put("l", it.label)
                    .put("on", Prefs.isHidden(c, it.key())));
        }
        return new JSONObject().put("id", "hidden").put("type", "multi")
                .put("label", "Hidden apps").put("options", opts);
    }

    private static JSONObject group(String title, JSONArray items) throws JSONException {
        return new JSONObject().put("title", title).put("items", items);
    }

    private static JSONObject opt(String v, String l) throws JSONException {
        return new JSONObject().put("v", v).put("l", l);
    }

    private static JSONObject sw(String id, String label, boolean on, String help) throws JSONException {
        return new JSONObject().put("id", id).put("type", "switch").put("label", label)
                .put("value", on ? "1" : "0");
    }

    private static JSONObject choice(String id, String label, String value, JSONArray options,
            String help) throws JSONException {
        return new JSONObject().put("id", id).put("type", "choice").put("label", label)
                .put("value", value).put("options", options);
    }

    // ---------------------------------------------------------------- changing one

    /**
     * Applies a change: {@link #OK}, {@link #BAD} for an id or value that is not on the list,
     * {@link #PENDING} when it is being written over the ADB link, or {@link #NEEDS_LINK}.
     * A switch's value is "1" or "0"; the hidden apps take "key=1" to hide and "key=0" to show.
     */
    static String apply(final Context c, String id, String value) {
        if (id == null || value == null) return BAD;
        boolean on = "1".equals(value);
        boolean flag = on || "0".equals(value);
        try {
            if ("background".equals(id)) {
                if (!knownBackground(value)) return BAD;
                Backgrounds.choose(c, value);
            } else if ("columns".equals(id)) {
                int n = Integer.parseInt(value);
                if (n < Prefs.COLUMNS_MIN || n > Prefs.COLUMNS_MAX) return BAD;
                Prefs.setColumns(c, n);
                AppModel.invalidateMetrics();
            } else if ("dock".equals(id)) {
                int n = Integer.parseInt(value);
                if (n < 0 || n >= Prefs.DOCK_SIZE_NAMES.length) return BAD;
                Prefs.setDockSize(c, n);
            } else if ("clock".equals(id)) {
                int n = Integer.parseInt(value);
                if (n < Prefs.CLOCK_24H || n > Prefs.CLOCK_OFF) return BAD;
                Prefs.setClockMode(c, n);
            } else if ("date".equals(id)) {
                int n = Integer.parseInt(value);
                if (n < Prefs.DATE_OFF || n > Prefs.DATE_SHORT) return BAD;
                Prefs.setDateFormat(c, n);
            } else if ("greeting".equals(id) && flag) {
                Prefs.setGreeting(c, on);
            } else if ("wide".equals(id) && flag) {
                Prefs.setNonTvWide(c, on);
                AppModel.reload(c);
            } else if ("fireicons".equals(id) && flag) {
                Prefs.setFireOsIcons(c, on);
                AppModel.reload(c);
            } else if ("sort".equals(id)) {
                int n = Integer.parseInt(value);
                if (n != Prefs.SORT_LAST_USED && n != Prefs.SORT_ALPHA) return BAD;
                Prefs.setSort(c, n);
                AppModel.resort(c);
            } else if ("tvfirst".equals(id) && flag) {
                Prefs.setLiveTvFirst(c, on);
            } else if ("inputs".equals(id) && flag) {
                Prefs.setShowInputs(c, on);
            } else if ("nontv".equals(id) && flag) {
                Prefs.setShowNonTv(c, on);
            } else if ("hidden".equals(id)) {
                int eq = value.lastIndexOf('=');
                if (eq < 1 || eq != value.length() - 2) return BAD;
                String key = value.substring(0, eq);
                if (!isApp(key)) return BAD;
                Prefs.setHidden(c, key, value.charAt(eq + 1) == '1');
            } else if ("idle".equals(id)) {
                int n = Integer.parseInt(value);
                boolean known = false;
                for (int i = 0; i < Prefs.IDLE_CHOICES.length; i++) known |= Prefs.IDLE_CHOICES[i] == n;
                if (!known) return BAD;
                Prefs.setIdleMinutes(c, n);
            } else if ("screensaver".equals(id)) {
                if (!("Colours".equals(value) || "Off".equals(value) || "Amazon's".equals(value))) return BAD;
                DisplaySoundPanel.screensaverSet(c, value, new Runnable() { public void run() { } });
                return OK;
            } else if ("sounds".equals(id) && flag) {
                return DisplaySoundPanel.soundsSet(c, on, new Runnable() { public void run() { } })
                        ? PENDING : NEEDS_LINK;
            } else if ("led".equals(id) && flag) {
                if (!DisplaySoundPanel.ledAvailable(c)) return BAD;
                DisplaySoundPanel.ledSet(c, on);
                return OK;
            } else {
                return BAD;
            }
        } catch (NumberFormatException e) {
            return BAD;
        }
        HomeRedirectService.refreshHome();
        return OK;
    }

    private static boolean knownBackground(String id) {
        for (int i = 0; i < Backgrounds.PRESETS.length; i++) if (Backgrounds.PRESETS[i].id.equals(id)) return true;
        for (int i = 0; i < Backgrounds.ARTS.length; i++) if (Backgrounds.ARTS[i].id.equals(id)) return true;
        return false;
    }

    private static boolean isApp(String key) {
        List<AppModel.Item> items = AppModel.allItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).type == AppModel.TYPE_APP && key.equals(items.get(i).key())) return true;
        }
        return false;
    }
}
