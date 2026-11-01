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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything about the TV itself, as opposed to Homestead's own look and behaviour (that is
 * SettingsPanel): the settings screens Amazon answers directly, Homestead's own Screen &
 * sound page for the handful Amazon locks behind a permission, a row for Amazon's full
 * settings menu, and the shortcuts row on the home screen.
 *
 * Every screen from Amazon is an action that com.amazon.tv.settings.v2 answers, aimed at
 * that package so a second handler (the quick settings overlay also answers some) cannot
 * turn the press into an app chooser. A screen is left out on a TV where the action finds
 * nothing, or where it needs a permission only Amazon's own apps hold.
 */
public final class SystemSettingsPanel {

    static final String SETTINGS_PKG = "com.amazon.tv.settings.v2";
    private static final String A = "com.amazon.device.settings.action.";
    private static final String PERMISSION = "com.amazon.tv.permission.LAUNCHER_SETTINGS";

    static final int ID = 0, TITLE = 1, ACTION = 2;

    /** Id (also the icon), title, action. Resolved on a TCL 40V5D, Fire OS 14. */
    static final String[][] SCREENS = {
            { "network", "Network", "android.settings.WIFI_SETTINGS" },
            { "display", "Display & sounds", A + "DISPLAY_AND_SOUNDS" },
            { "controllers", "Controllers & Bluetooth", A + "CONTROLLERS" },
            { "accessibility", "Accessibility", A + "ACCESSIBILITY" },
            { "applications", "Applications", A + "APPLICATIONS" },
            { "language", "Language", A + "LANGUAGE" },
            { "preferences", "Preferences", A + "PREFERENCES" },
            { "data", "Data monitoring", A + "DATA_MONITOR" },
            { "account", "Amazon account", A + "MY_ACCOUNT" },
            { "device", "My Fire TV", A + "DEVICE" },
    };

    private SystemSettingsPanel() { }

    /**
     * The title of a screen, in the TV's language. SCREENS keeps the English text as a
     * stable internal key (matched in help text, never shown by itself); this is what is
     * actually shown, in Homestead's own menu and on a pill.
     */
    static String title(Context c, String id) {
        if ("network".equals(id)) return c.getString(R.string.sys_network);
        if ("display".equals(id)) return c.getString(R.string.sys_display);
        if ("controllers".equals(id)) return c.getString(R.string.sys_controllers);
        if ("accessibility".equals(id)) return c.getString(R.string.sys_accessibility);
        if ("applications".equals(id)) return c.getString(R.string.sys_applications);
        if ("language".equals(id)) return c.getString(R.string.sys_language);
        if ("preferences".equals(id)) return c.getString(R.string.sys_preferences);
        if ("data".equals(id)) return c.getString(R.string.sys_data);
        if ("account".equals(id)) return c.getString(R.string.sys_account);
        if ("device".equals(id)) return c.getString(R.string.sys_device);
        return id;
    }

    /** The four titles too long for a pill, shortened - also in the TV's language. */
    static String shortTitle(Context c, String id) {
        if ("controllers".equals(id)) return c.getString(R.string.sys_controllers_short);
        if ("display".equals(id)) return c.getString(R.string.sys_display_short);
        if ("account".equals(id)) return c.getString(R.string.sys_account_short);
        if ("data".equals(id)) return c.getString(R.string.sys_data_short);
        return title(c, id);
    }

    /** What a screen holds, in the TV's language. */
    static String help(Context c, String id) {
        if ("network".equals(id)) return c.getString(R.string.sys_help_network);
        if ("display".equals(id)) return c.getString(R.string.sys_help_display);
        if ("controllers".equals(id)) return c.getString(R.string.sys_help_controllers);
        if ("accessibility".equals(id)) return c.getString(R.string.sys_help_accessibility);
        if ("applications".equals(id)) return c.getString(R.string.sys_help_applications);
        if ("language".equals(id)) return c.getString(R.string.sys_help_language);
        if ("preferences".equals(id)) return c.getString(R.string.sys_help_preferences);
        if ("data".equals(id)) return c.getString(R.string.sys_help_data);
        if ("account".equals(id)) return c.getString(R.string.sys_help_account);
        if ("device".equals(id)) return c.getString(R.string.sys_help_device);
        return "";
    }

    static Intent intentFor(String action) {
        Intent i = new Intent(action);
        i.setPackage(SETTINGS_PKG);
        return i;
    }

    /**
     * The screens that open from here on this TV. Several demand a permission only Amazon's
     * own apps hold (signature | privileged), so pressing them would throw; those titles go
     * into {@code locked} when it is given.
     */
    static List<String[]> available(Context c, List<String> locked) {
        List<String[]> out = new ArrayList<String[]>();
        for (int i = 0; i < SCREENS.length; i++) {
            ResolveInfo ri = c.getPackageManager().resolveActivity(intentFor(SCREENS[i][ACTION]), 0);
            if (ri == null || ri.activityInfo == null) continue;
            String need = ri.activityInfo.permission;
            if (need != null && c.checkSelfPermission(need) != PackageManager.PERMISSION_GRANTED) {
                if (locked != null) locked.add(title(c, SCREENS[i][ID]));
                continue;
            }
            out.add(SCREENS[i]);
        }
        return out;
    }

    // ---------------------------------------------------------------- the hub

    public static View build(final Context c, final SettingsPanel.Host host) {
        final Panel p = new Panel(c, c.getString(R.string.sys_title), null, Panel.SHEET);
        List<String> locked = new ArrayList<String>();
        List<String[]> open = available(c, locked);

        p.addHeader(c.getString(R.string.sys_header_open));
        for (int i = 0; i < open.size(); i++) {
            final Intent intent = intentFor(open.get(i)[ACTION]);
            p.addRow(title(c, open.get(i)[ID]), "", new Runnable() {
                public void run() { SetupPanel.start(c, intent); }
            }).opens().setHelp(help(c, open.get(i)[ID]));
        }

        p.addHeader(c.getString(R.string.sys_header_own));
        List<String> checks = DisplaySoundPanel.issues(c);
        Panel.Row screenSound = p.addRow(c.getString(R.string.sys_screen_sound),
                checks.isEmpty() ? "" : c.getString(R.string.sys_to_check, checks.size()), new Runnable() {
            public void run() { host.showPanel(DisplaySoundPanel.build(c, host)); }
        }).opens();
        if (!checks.isEmpty()) screenSound.setValueColor(0xFFE8C468);
        StringBuilder sb = new StringBuilder(DisplaySoundPanel.builtInTv(c)
                ? c.getString(R.string.sys_ss_help_tv)
                : c.getString(R.string.sys_ss_help_stick));
        sb.append(c.getString(R.string.sys_ss_help_tail));
        if (!checks.isEmpty()) {
            sb.append(c.getString(R.string.sys_worth));
            for (int i = 0; i < checks.size(); i++) {
                sb.append(i == 0 ? "" : "; ").append(checks.get(i));
            }
            sb.append('.');
        }
        screenSound.setHelp(sb.toString());

        p.addRow(c.getString(R.string.sys_tv_remote), "", new Runnable() {
            public void run() { host.showPanel(KeyBindings.build(c, host)); }
        }).opens().setHelp(c.getString(R.string.sys_tv_remote_help));

        final Panel.Row all = p.addRow(c.getString(R.string.home_amazon_settings),
                AppModel.amazonLauncherOn(c) ? "" : c.getString(R.string.sys_needs_launcher), null).opens();
        all.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openAll(c, host); }
        });
        all.setHelp((locked.isEmpty() ? c.getString(R.string.sys_all_help)
                : c.getString(R.string.sys_all_help_incl, join(locked)))
                + (AppModel.amazonLauncherOn(c) ? "" : c.getString(R.string.sys_all_help_off)));

        p.addHeader(c.getString(R.string.sys_header_topbar));
        final Panel.Row place = p.addRow(c.getString(R.string.sys_quick_settings),
                SettingsPanel.onOff(c, Prefs.shortcutsPlace(c) != Prefs.SHORTCUTS_OFF), null).asSwitch();
        place.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = Prefs.shortcutsPlace(c) == Prefs.SHORTCUTS_OFF;
                Prefs.setShortcutsPlace(c, n ? Prefs.SHORTCUTS_BAR : Prefs.SHORTCUTS_OFF);
                place.setValue(SettingsPanel.onOff(c, n));
                AppModel.reload(c);
                host.rebuildGrid();
            }
        });
        place.setHelp(c.getString(R.string.sys_quick_help));
        p.addRow(c.getString(R.string.sys_choose_quick), "", new Runnable() {
            public void run() { host.showPanel(buildChoose(c, host)); }
        }).opens().setHelp(c.getString(R.string.sys_choose_help));
        final Panel.Row phone = p.addRow(c.getString(R.string.sys_rail_phone),
                SettingsPanel.onOff(c, Prefs.railPhone(c)), null).asSwitch();
        phone.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.railPhone(c);
                Prefs.setRailPhone(c, n);
                phone.setValue(SettingsPanel.onOff(c, n));
                host.rebuildGrid();
            }
        });
        phone.setHelp(c.getString(R.string.sys_rail_phone_help));

        p.addHeader(c.getString(R.string.sys_header_launcher));
        if (AppModel.amazonLauncherOn(c)) {
            p.addRow(c.getString(R.string.sys_header_launcher), c.getString(R.string.ui_on), null)
                    .setHelp(c.getString(R.string.sys_launcher_on_help));
        } else {
            p.addRow(c.getString(R.string.sys_header_launcher), c.getString(R.string.ui_off), null)
                    .setHelp(c.getString(R.string.sys_launcher_off_help));
        }
        p.addRow(c.getString(R.string.home_back), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    private static View buildChoose(final Context c, final SettingsPanel.Host host) {
        final Panel p = new Panel(c, c.getString(R.string.sys_choose_title), null, Panel.SHEET);
        p.addHeader(c.getString(R.string.sys_header_topbar));
        final java.util.Set<String> on = Prefs.shortcuts(c);
        List<String[]> open = available(c, null);
        for (int i = 0; i < open.size(); i++) {
            final String id = open.get(i)[ID];
            final Panel.Row row = p.addRow(title(c, id), SettingsPanel.onOff(c, on.contains(id)), null).asSwitch();
            row.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean n = !Prefs.shortcuts(c).contains(id);
                    Prefs.setShortcut(c, id, n);
                    row.setValue(SettingsPanel.onOff(c, n));
                    AppModel.reload(c);
                    host.rebuildGrid();
                }
            });
            row.setHelp(help(c, open.get(i)[ID]));
        }
        if (Prefs.shortcutsPlace(c) == Prefs.SHORTCUTS_OFF) {
            p.addNote(c.getString(R.string.sys_quick_off));
        }
        p.addRow(c.getString(R.string.home_back), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    // ---------------------------------------------------------------- Amazon Settings, via the launcher

    /**
     * The warning, then the button: what is about to happen and what it costs. Homestead
     * runs on more than one TV, and not every one protects com.amazon.tv.launcher the way
     * this one does - where the launcher can be switched on and off freely, this is the way
     * back into Amazon's own settings without hunting for the launcher row below. Where it
     * cannot, the attempt below simply fails and says so.
     */
    private static View buildOpenRest(final Context c, final SettingsPanel.Host host) {
        final Panel p = new Panel(c, c.getString(R.string.home_amazon_settings), null, Panel.SHEET);

        p.addHeader(c.getString(R.string.sys_what_happens));
        p.addNote(c.getString(R.string.sys_step1));
        p.addNote(c.getString(R.string.sys_step2));
        p.addNote(c.getString(R.string.sys_step3));
        p.addHeader(c.getString(R.string.sys_cost));
        p.addNote(c.getString(R.string.sys_cost_note));

        p.addRow(c.getString(R.string.sys_switch_on), "", new Runnable() {
            public void run() { openRest(c); }
        });
        p.addRow(c.getString(R.string.ui_cancel), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    private static void openRest(final Context c) {
        if (!AdbBridge.canRun(c)) {
            Toast.makeText(c, R.string.home_restore_failed, Toast.LENGTH_LONG).show();
            return;
        }
        // Kept in the list of what this app switched off, so the restore tile offers to undo it.
        Prefs.setRestoreWatch(c, true);
        Toast.makeText(c, R.string.home_connecting, Toast.LENGTH_SHORT).show();
        AdbBridge.runRetry(new String[]{ AdbBridge.enableCommand(AppModel.AMAZON_LAUNCHER) },
                new AdbBridge.Callback() {
            public void onDone(boolean ok, String out) {
                if (Debloat.stateOf(c, AppModel.AMAZON_LAUNCHER) != Debloat.ENABLED) {
                    // Either no such TV (the package refused, protected) or the retries ran
                    // out; either way there is nothing more to try here.
                    Toast.makeText(c, R.string.home_restore_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                openAllSettings(c);
            }
        });
    }

    /**
     * Amazon's full settings menu: straight away with the launcher on, behind the warning
     * with it off. Also what the top bar's "System settings" pill does with the launcher off.
     */
    static void openAll(Context c, SettingsPanel.Host host) {
        if (AppModel.amazonLauncherOn(c)) openAllSettings(c);
        else host.showPanel(buildOpenRest(c, host));
    }

    /** Amazon's own settings menu, which the launcher serves and needs no permission to start. */
    private static void openAllSettings(Context c) {
        Intent i = new Intent();
        i.setClassName(AppModel.AMAZON_LAUNCHER, AppModel.AMAZON_LAUNCHER + ".ui.MainSettingsActivity");
        SetupPanel.start(c, i);
    }

    // ---------------------------------------------------------------- small helpers


    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
