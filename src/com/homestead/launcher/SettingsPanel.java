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
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The Settings pages. Everything here is the launcher's own behaviour - nothing on these
 * pages touches the system, so none of it needs a privileged permission.
 */
public final class SettingsPanel {

    /** Implemented by HomeGrid so a panel can push another panel or apply changes. */
    public interface Host {
        void rebuildGrid();
        void showPanel(View v);
        void closePanel();
    }

    private SettingsPanel() { }

    /**
     * The hub: a sheet over the home screen with the three pages of home-screen options, then
     * the system pages. Changes show on the grid behind it straight away.
     */
    public static View build(final Context c, final Host host) {
        final Panel p = new Panel(c, c.getString(R.string.settings_title),
                c.getString(R.string.st_subtitle, UpdateChecker.installedVersionName(c)),
                Panel.SHEET);

        final Panel.Row look = p.addRow(c.getString(R.string.settings_appearance),
                appearanceSummary(c), null).opens();
        look.setValueColor(0xFF8FA3B8);
        look.setHelp(c.getString(R.string.st_look_help));
        final Panel.Row content = p.addRow(c.getString(R.string.settings_content),
                contentSummary(c), null).opens();
        content.setValueColor(0xFF8FA3B8);
        content.setHelp(c.getString(R.string.st_content_help));
        final Panel.Row behaviour = p.addRow(c.getString(R.string.settings_behaviour),
                behaviourSummary(c), null).opens();
        behaviour.setValueColor(0xFF8FA3B8);
        behaviour.setHelp(c.getString(R.string.st_behaviour_help));
        // Summaries catch up when a sub-page changes something.
        final Runnable summaries = new Runnable() {
            public void run() {
                look.setValue(appearanceSummary(c));
                content.setValue(contentSummary(c));
                behaviour.setValue(behaviourSummary(c));
            }
        };
        look.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildAppearance(c, host, summaries)); }
        });
        content.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildContent(c, host, summaries)); }
        });
        behaviour.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildBehaviour(c, host, summaries)); }
        });

        p.addHeader(c.getString(R.string.settings_header_system));

        boolean ready = SetupPanel.serviceEnabled(c);
        final Panel.Row setup = p.addRow(c.getString(R.string.settings_setup), ready ? c.getString(R.string.st_done)
                : Prefs.homeOffByUser(c) ? c.getString(R.string.st_home_off) : c.getString(R.string.st_not_finished), null).opens();
        setup.setValueColor(ready ? 0xFFA7D28F : 0xFFE8C468);
        setup.setHelp(c.getString(R.string.st_setup_help));
        setup.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(SetupPanel.build(c, host)); }
        });

        if (AppModel.amazonLauncherOn(c)) {
            // With Amazon's launcher off there is nothing for this to hand Home back to.
            SetupPanel.addTurnOffRow(c, p, new Runnable() {
                public void run() {
                    setup.setValue(c.getString(R.string.st_home_off));
                    setup.setValueColor(0xFFE8C468);
                }
            });
            p.addNote(c.getString(R.string.su_turn_off_help));
        }

        p.addHeader(c.getString(R.string.settings_header_device));

        p.addRow(c.getString(R.string.sys_title), "", new Runnable() {
            public void run() { host.showPanel(SystemSettingsPanel.build(c, host)); }
        }).opens().setHelp(c.getString(R.string.st_system_help));

        p.addRow(c.getString(R.string.home_tv_health), c.getString(R.string.st_check), new Runnable() {
            public void run() { host.showPanel(HealthPanel.build(c, host)); }
        }).opens().setHelp(c.getString(R.string.st_health_help));

        final Panel.Row watch = p.addRow(c.getString(R.string.settings_warn_debloat),
                onOff(c, Prefs.restoreWatch(c)), null).asSwitch();
        watch.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.restoreWatch(c);
                Prefs.setRestoreWatch(c, n);
                watch.setValue(onOff(c, n));
                AppModel.reload(c);
                host.rebuildGrid();
            }
        });
        watch.setHelp(c.getString(R.string.st_watch_help));

        final Panel.Row updates = p.addRow(c.getString(R.string.settings_check_updates),
                "v" + UpdateChecker.installedVersionName(c), null);
        updates.setValueColor(0xFF8FA3B8);
        updates.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { UpdatePanel.checkNow(c, host, updates); }
        });

        p.addRow(c.getString(R.string.settings_close), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    // ---------------------------------------------------------------- home screen
    // Three pages under the hub: how it looks, what it shows and in which order, and what
    // it does by itself.

    /** Layout, clock and greeting, and the background. */
    private static View buildAppearance(final Context c, final Host host, final Runnable changed) {
        final Panel p = new Panel(c, c.getString(R.string.settings_appearance), null, Panel.SHEET);

        final Panel.Row look = p.addRow(c.getString(R.string.settings_background),
                Backgrounds.label(c), null).opens();
        look.setValueColor(0xFF8FA3B8);
        look.setHelp(c.getString(R.string.st_background_help));
        look.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildLook(c, host, changed)); }
        });
        // The choice is made on the page this row opens; repaint it on the way back.
        look.cell.setOnFocusChangeListener(chain(look.cell.getOnFocusChangeListener(),
                new Runnable() {
                    public void run() { look.setValue(Backgrounds.label(c)); }
                }));

        p.addHeader(c.getString(R.string.st_tiles));
        final Panel.Row cols = p.addRow(c.getString(R.string.st_columns), String.valueOf(Prefs.columns(c)), null);
        cols.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int n = Prefs.columns(c) + 1;
                if (n > Prefs.COLUMNS_MAX) n = Prefs.COLUMNS_MIN;
                Prefs.setColumns(c, n);
                cols.setValue(String.valueOf(n));
                AppModel.invalidateMetrics();
                host.rebuildGrid();
                changed.run();
            }
        });

        final Panel.Row dock = p.addRow(c.getString(R.string.st_dock),
                Prefs.dockSizeName(c, Prefs.dockSize(c)), null);
        dock.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int n = (Prefs.dockSize(c) + 1) % Prefs.DOCK_SIZE_NAMES.length;
                Prefs.setDockSize(c, n);
                dock.setValue(Prefs.dockSizeName(c, n));
                host.rebuildGrid();
            }
        });
        dock.setHelp(c.getString(R.string.st_dock_help));

        final Panel.Row wide = p.addRow(c.getString(R.string.st_wide), onOff(c, Prefs.nonTvWide(c)), null).asSwitch();
        wide.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.nonTvWide(c);
                Prefs.setNonTvWide(c, n);
                wide.setValue(onOff(c, n));
                AppModel.reload(c);
                host.rebuildGrid();
            }
        });
        wide.setHelp(c.getString(R.string.st_wide_help));

        final Panel.Row fire = p.addRow(c.getString(R.string.st_fire_icons), onOff(c, Prefs.fireOsIcons(c)), null).asSwitch();
        fire.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.fireOsIcons(c);
                Prefs.setFireOsIcons(c, n);
                fire.setValue(onOff(c, n));
                AppModel.reload(c);
                host.rebuildGrid();
            }
        });
        fire.setHelp(c.getString(R.string.st_fire_icons_help));


        p.addHeader(c.getString(R.string.st_clock_greeting));
        final Panel.Row clock = p.addRow(c.getString(R.string.st_clock), clockLabel(c), null);
        clock.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int n = Prefs.clockMode(c) + 1;
                if (n > Prefs.CLOCK_OFF) n = Prefs.CLOCK_24H;
                Prefs.setClockMode(c, n);
                clock.setValue(clockLabel(c));
                host.rebuildGrid();
            }
        });

        final Panel.Row greet = p.addRow(c.getString(R.string.st_greeting), onOff(c, Prefs.greeting(c)), null).asSwitch();
        greet.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.greeting(c);
                Prefs.setGreeting(c, n);
                greet.setValue(onOff(c, n));
                host.rebuildGrid();
            }
        });
        greet.setHelp(c.getString(R.string.st_greeting_help));

        final Panel.Row date = p.addRow(c.getString(R.string.st_date), dateLabel(c), null);
        date.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int n = Prefs.dateFormat(c) + 1;
                if (n > Prefs.DATE_SHORT) n = Prefs.DATE_OFF;
                Prefs.setDateFormat(c, n);
                date.setValue(dateLabel(c));
                host.rebuildGrid();
            }
        });
        date.setHelp(c.getString(R.string.st_date_help));

        p.addRow(c.getString(R.string.home_back), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    /** What is shown, in which order, and what is hidden. */
    private static View buildContent(final Context c, final Host host, final Runnable changed) {
        final Panel p = new Panel(c, c.getString(R.string.settings_content), null, Panel.SHEET);

        final Panel.Row sort = p.addRow(c.getString(R.string.st_sort), sortLabel(c), null);
        sort.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setSort(c, Prefs.sort(c) == Prefs.SORT_LAST_USED
                        ? Prefs.SORT_ALPHA : Prefs.SORT_LAST_USED);
                sort.setValue(sortLabel(c));
                AppModel.resort(c);
                host.rebuildGrid();
            }
        });

        p.addHeader(c.getString(R.string.st_hidden_header));

        final Panel.Row hidden = p.addRow(c.getString(R.string.st_hidden_tiles),
                String.valueOf(Prefs.hiddenCount(c)), null).opens();
        hidden.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildHiddenList(c, host)); }
        });
        // The count changes on the page this row opens; repaint it on the way back.
        hidden.cell.setOnFocusChangeListener(chain(hidden.cell.getOnFocusChangeListener(),
                new Runnable() {
                    public void run() {
                        hidden.setValue(String.valueOf(Prefs.hiddenCount(c)));
                        changed.run();
                    }
                }));

        p.addNote(c.getString(R.string.st_hidden_note));

        p.addHeader(c.getString(R.string.st_sections));

        // Only offered where there is a Live TV or Canali TV tile.
        if (AppModel.hasLiveTv()) {
            final Panel.Row tvFirst = p.addRow(c.getString(R.string.st_tv_first), onOff(c, Prefs.liveTvFirst(c)), null).asSwitch();
            tvFirst.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean n = !Prefs.liveTvFirst(c);
                    Prefs.setLiveTvFirst(c, n);
                    tvFirst.setValue(onOff(c, n));
                    host.rebuildGrid();
                }
            });
            tvFirst.setHelp(c.getString(R.string.st_tv_first_help));
        }

        final Panel.Row secIn = p.addRow(c.getString(R.string.st_show_inputs), onOff(c, Prefs.showInputs(c)), null).asSwitch();
        secIn.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.showInputs(c);
                Prefs.setShowInputs(c, n);
                secIn.setValue(onOff(c, n));
                host.rebuildGrid();
            }
        });

        final Panel.Row secNon = p.addRow(c.getString(R.string.st_show_nontv), onOff(c, Prefs.showNonTv(c)), null).asSwitch();
        secNon.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.showNonTv(c);
                Prefs.setShowNonTv(c, n);
                secNon.setValue(onOff(c, n));
                host.rebuildGrid();
            }
        });

        p.addSectionHelp(c.getString(R.string.st_sections_help));

        p.addHeader(c.getString(R.string.st_apps));
        p.addRow(c.getString(R.string.st_reload), "", new Runnable() {
            public void run() {
                AppModel.reload(c);
                host.rebuildGrid();
            }
        }).setHelp(c.getString(R.string.st_reload_help));

        p.addRow(c.getString(R.string.home_back), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    /** What the home screen does by itself. */
    private static View buildBehaviour(final Context c, final Host host, final Runnable changed) {
        final Panel p = new Panel(c, c.getString(R.string.settings_behaviour), null, Panel.SHEET);

        final Panel.Row idle = p.addRow(c.getString(R.string.st_idle), idleLabel(c, Prefs.idleMinutes(c)), null);
        idle.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int cur = Prefs.idleMinutes(c);
                int next = Prefs.IDLE_CHOICES[0];
                for (int i = 0; i < Prefs.IDLE_CHOICES.length; i++) {
                    if (Prefs.IDLE_CHOICES[i] == cur) {
                        next = Prefs.IDLE_CHOICES[(i + 1) % Prefs.IDLE_CHOICES.length];
                        break;
                    }
                }
                Prefs.setIdleMinutes(c, next);
                idle.setValue(idleLabel(c, next));
                changed.run();
            }
        });
        idle.setHelp(c.getString(R.string.st_idle_help));

        final Panel.Row remote = p.addRow(c.getString(R.string.remote_title),
                onOff(c, Prefs.remoteOn(c)), null).opens();
        remote.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.showPanel(RemotePanel.build(c, host, new Runnable() {
                    public void run() {
                        remote.setValue(onOff(c, Prefs.remoteOn(c)));
                        changed.run();
                    }
                }));
            }
        });
        remote.setHelp(c.getString(R.string.st_remote_help));

        p.addRow(c.getString(R.string.home_back), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    private static String appearanceSummary(Context c) {
        return c.getString(R.string.st_sum_look, Prefs.columns(c), Backgrounds.label(c));
    }

    private static String contentSummary(Context c) {
        int n = Prefs.hiddenCount(c);
        return n == 0 ? c.getString(R.string.st_sum_none_hidden) : c.getString(R.string.st_sum_hidden, n);
    }

    private static String behaviourSummary(Context c) {
        return c.getString(R.string.st_sum_idle, idleLabel(c, Prefs.idleMinutes(c)))
                + (Prefs.remoteOn(c) ? c.getString(R.string.st_sum_remote) : "");
    }

    /** Runs extra work after a row's own focus handling, when it gains focus. */
    private static View.OnFocusChangeListener chain(final View.OnFocusChangeListener first,
                                                    final Runnable onGain) {
        return new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (first != null) first.onFocusChange(v, has);
                if (has) onGain.run();
            }
        };
    }

    // ---------------------------------------------------------------- background

    private static View buildLook(final Context c, final Host host, final Runnable changed) {
        final Panel p = new Panel(c, c.getString(R.string.settings_background), null, Panel.SHEET);

        // One row per look, the one in use marked. Selecting one applies it at once, and the
        // home screen beside the page shows it.
        p.addSectionHelp(c.getString(R.string.st_bg_help));
        final List<Panel.Row> rows = new ArrayList<Panel.Row>();
        final List<String> ids = new ArrayList<String>();
        final Runnable mark = new Runnable() {
            public void run() {
                String now = Backgrounds.currentId(c);
                boolean noFile = Backgrounds.photoFile(c) == null;
                for (int i = 0; i < rows.size(); i++) {
                    boolean on = ids.get(i).equals(now);
                    String v = on ? c.getString(R.string.keys_in_use) : "";
                    if (noFile && ids.get(i).equals(Backgrounds.PHOTO_ID)) {
                        v = on ? c.getString(R.string.st_in_use_nofile) : c.getString(R.string.st_nofile);
                    }
                    rows.get(i).setValue(v);
                    rows.get(i).setValueColor(on ? IN_USE : 0xFF8FA3B8);
                }
            }
        };
        for (int i = 0; i <= Backgrounds.PRESETS.length; i++) {
            boolean photo = i == Backgrounds.PRESETS.length;
            final Backgrounds.Preset look = photo ? Backgrounds.PHOTO_LOOK : Backgrounds.PRESETS[i];
            final Panel.Row r = p.addRow(look.name(c), "", null);
            android.graphics.Bitmap shot = photo ? Backgrounds.lastPhoto() : null;
            r.setSwatch(shot != null
                    ? new android.graphics.drawable.BitmapDrawable(c.getResources(), shot)
                    : swatch(c, look));
            r.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Backgrounds.choose(c, look.id);
                    mark.run();
                    host.rebuildGrid();
                    changed.run();
                }
            });
            rows.add(r);
            ids.add(look.id);
        }
        rows.get(ids.indexOf("black")).setHelp(c.getString(R.string.st_black_help));
        rows.get(rows.size() - 1).setHelp(c.getString(R.string.st_photo_help, BackgroundProvider.AUTHORITY));

        // Public-domain paintings that come with the app, shown the way a photo is.
        p.addHeader(c.getString(R.string.st_paintings));
        for (int i = 0; i < Backgrounds.ARTS.length; i++) {
            final Backgrounds.Art art = Backgrounds.ARTS[i];
            final Panel.Row r = p.addRow(art.name, "", null);
            android.graphics.Bitmap thumb = Backgrounds.artThumb(c, art);
            if (thumb != null) {
                r.setSwatch(new android.graphics.drawable.BitmapDrawable(c.getResources(), thumb));
            }
            r.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Backgrounds.choose(c, art.id);
                    mark.run();
                    host.rebuildGrid();
                    changed.run();
                }
            });
            rows.add(r);
            ids.add(art.id);
        }
        mark.run();

        p.addHeader(c.getString(R.string.st_photo));
        final Panel.Row dim = p.addRow(c.getString(R.string.st_dimming), Backgrounds.dimLabel(c), null);
        dim.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setPhotoDim(c, (Prefs.photoDim(c) + 1) % Backgrounds.DIM_NAMES.length);
                dim.setValue(Backgrounds.dimLabel(c));
                host.rebuildGrid();
            }
        });
        dim.setHelp(c.getString(R.string.st_dimming_help));

        p.addRow(c.getString(R.string.st_choose_photo), "", new Runnable() {
            public void run() { host.showPanel(PhotoPanel.build(c, host)); }
        }).opens().setHelp(c.getString(R.string.st_choose_photo_help));

        p.addRow(c.getString(R.string.home_back), "", new Runnable() {
            public void run() { host.closePanel(); }
        });

        // Start on the look in use. showPanel focuses the first row once the page is up,
        // so this goes one step after that.
        final View start = rows.get(Math.max(0, ids.indexOf(Backgrounds.currentId(c)))).cell;
        p.root.post(new Runnable() {
            public void run() {
                p.root.post(new Runnable() { public void run() { start.requestFocus(); } });
            }
        });
        return p.root;
    }

    private static final int IN_USE = 0xFFA7D28F;

    /** A preset's colours as a small rounded sample, edged so dark ones show on the page. */
    private static android.graphics.drawable.Drawable swatch(Context c, Backgrounds.Preset look) {
        android.graphics.drawable.GradientDrawable d = Backgrounds.drawable(look);
        d.setCornerRadius(AppModel.dp(c, 5));
        d.setStroke(AppModel.dp(c, 1), 0x59FFFFFF);
        return d;
    }

    // ---------------------------------------------------------------- hidden list

    private static View buildHiddenList(final Context c, final Host host) {
        final Panel p = new Panel(c, c.getString(R.string.st_hidden_tiles),
                c.getString(R.string.st_hidden_sub), Panel.SHEET);

        Set<String> keys = Prefs.hidden(c);
        if (keys.isEmpty()) {
            p.addNote(c.getString(R.string.st_nothing_hidden));
        } else {
            // Resolve keys to human labels through the full model, not the visible list.
            List<AppModel.Item> all = AppModel.allItems();
            List<String> rows = new ArrayList<String>();
            final List<String> rowKeys = new ArrayList<String>();
            for (String k : keys) {
                String label = k;
                for (int i = 0; i < all.size(); i++) {
                    if (k.equals(all.get(i).key())) { label = all.get(i).label; break; }
                }
                // Live TV leaves the model when its app is switched off; still name it.
                if (label.equals(AppModel.LIVETV_KEY)) label = c.getString(R.string.home_livetv);
                if (label.equals(AppModel.CHANNELS_KEY)) label = c.getString(R.string.home_channels);
                rows.add(label);
                rowKeys.add(k);
            }
            // Stable, readable order.
            for (int i = 0; i < rows.size(); i++) {
                for (int j = i + 1; j < rows.size(); j++) {
                    if (rows.get(j).compareToIgnoreCase(rows.get(i)) < 0) {
                        Collections.swap(rows, i, j);
                        Collections.swap(rowKeys, i, j);
                    }
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                final String key = rowKeys.get(i);
                final Panel.Row r = p.addRow(rows.get(i), c.getString(R.string.st_hidden_value), null);
                r.cell.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Prefs.setHidden(c, key, false);
                        r.setValue(c.getString(R.string.st_shown_value));
                        r.cell.setEnabled(false);
                        host.rebuildGrid();
                    }
                });
            }
            p.addRow(c.getString(R.string.st_show_all_hidden), "", new Runnable() {
                public void run() {
                    Prefs.clearHidden(c);
                    host.rebuildGrid();
                    host.closePanel();
                }
            });
        }
        p.addRow(c.getString(R.string.home_back), "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    // ---------------------------------------------------------------- labels

    static String onOff(Context c, boolean b) { return c.getString(b ? R.string.ui_on : R.string.ui_off); }

    static String sortLabel(Context c) {
        return Prefs.sort(c) == Prefs.SORT_ALPHA ? "A – Z" : c.getString(R.string.st_last_used);
    }

    static String clockLabel(Context c) {
        switch (Prefs.clockMode(c)) {
            case Prefs.CLOCK_12H: return c.getString(R.string.st_12h);
            case Prefs.CLOCK_OFF: return c.getString(R.string.ui_off);
            default: return c.getString(R.string.st_24h);
        }
    }

    static String idleLabel(Context c, int minutes) {
        return minutes <= 0 ? c.getString(R.string.ui_off) : minutes + " min";
    }

    static String dateLabel(Context c) {
        switch (Prefs.dateFormat(c)) {
            case Prefs.DATE_LONG: return c.getString(R.string.st_weekday_month);
            case Prefs.DATE_SHORT: return c.getString(R.string.st_numbers);
            default: return c.getString(R.string.ui_off);
        }
    }
}
