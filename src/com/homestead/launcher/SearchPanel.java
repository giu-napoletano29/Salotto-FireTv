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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Search on the TV: a keyboard of letters on the left that the D-pad walks over, the matches
 * on the right. It is drawn by hand instead of using the system keyboard because this page
 * can sit in an accessibility overlay, which the system keyboard does not reliably reach.
 *
 * Typing with a remote is slow, so the page also says how to type from a phone
 * (RemoteServer). Nothing here runs unless the page is open.
 */
final class SearchPanel {

    private static final String KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    // Nine to a row: 36 keys make four rows, and with the row of Space, Delete and Clear the whole
    // keyboard still fits under the title on a 1080p screen.
    private static final int PER_ROW = 9;
    private static final int KEY_DP = 46, GAP_DP = 6;
    private static final int SHOWN = 12;
    private static final int KEY_BG = 0x14FFFFFF, KEY_FOCUS = 0x66FFFFFF;

    private SearchPanel() { }

    static View build(final Context c, final SettingsPanel.Host host) {
        final SearchIndex index = new SearchIndex(c);
        final StringBuilder typed = new StringBuilder();
        final Runnable[] refreshRef = new Runnable[1];

        // A keyboard that is not the on-screen one (the phone's Send tab, a USB keyboard) types
        // here too: letters, digits, Space and Delete. Everything else, the arrows and OK
        // included, goes on to whatever has the focus.
        FrameLayout root = new FrameLayout(c) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && refreshRef[0] != null) {
                    int code = e.getKeyCode();
                    int ch = e.getUnicodeChar();
                    boolean typedKey = true;
                    if (code == KeyEvent.KEYCODE_DEL) {
                        if (typed.length() > 0) typed.setLength(typed.length() - 1);
                    } else if (code == KeyEvent.KEYCODE_SPACE) {
                        if (typed.length() > 0 && typed.charAt(typed.length() - 1) != ' ') typed.append(' ');
                    } else if (e.isPrintingKey() && ch > 0 && Character.isLetterOrDigit(ch)) {
                        typed.append(Character.toLowerCase((char) ch));
                    } else {
                        typedKey = false;
                    }
                    if (typedKey) {
                        refreshRef[0].run();
                        return true;
                    }
                } else if (e.getAction() == KeyEvent.ACTION_UP && (e.getKeyCode() == KeyEvent.KEYCODE_SPACE
                        || e.getKeyCode() == KeyEvent.KEYCODE_DEL)) {
                    return true;   // the press was taken on the way down
                }
                return super.dispatchKeyEvent(e);
            }
        };
        root.setClickable(true);
        root.setFocusable(false);
        root.setBackground(ChannelsPanel.pageBackground(c));
        View veil = new View(c);
        veil.setBackgroundColor(0xA6000000);
        root.addView(veil, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int margin = AppModel.dp(c, 64);
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(margin, AppModel.dp(c, 34), margin, AppModel.dp(c, 24));
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- left: what was typed, the keys, and how to type faster
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        page.addView(left, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = label(c, c.getString(R.string.search_title), 30, Color.WHITE, true);
        left.addView(title);

        final TextView query = label(c, "", 26, Color.WHITE, false);
        query.setSingleLine(true);
        query.setPadding(AppModel.dp(c, 14), AppModel.dp(c, 10), AppModel.dp(c, 14), AppModel.dp(c, 10));
        GradientDrawable qbg = new GradientDrawable();
        qbg.setCornerRadius(AppModel.dp(c, 8));
        qbg.setColor(0x26FFFFFF);
        query.setBackground(qbg);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = AppModel.dp(c, 14);
        qlp.bottomMargin = AppModel.dp(c, 14);
        left.addView(query, qlp);

        // ---- right: the matches
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rlp.leftMargin = AppModel.dp(c, 48);
        page.addView(right, rlp);

        final TextView heading = label(c, "", 15, 0xFF9BB0C6, false);
        right.addView(heading);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(AppModel.dp(c, 4), AppModel.dp(c, 10), AppModel.dp(c, 4), AppModel.dp(c, 30));
        final LinearLayout results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        sv.addView(results, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Runnable refresh = new Runnable() {
            public void run() {
                String q = typed.toString();
                query.setText(q.length() == 0 ? c.getString(R.string.search_prompt) : q + "▏");
                query.setTextColor(q.length() == 0 ? 0xFF7F8A96 : Color.WHITE);
                List<SearchIndex.Hit> hits = q.trim().length() == 0
                        ? SearchIndex.recent(c, SHOWN) : index.find(q, SHOWN);
                heading.setText(q.trim().length() == 0
                        ? (hits.isEmpty() ? "" : c.getString(R.string.search_recent))
                        : (hits.isEmpty() ? c.getString(R.string.search_none)
                                : c.getString(R.string.search_results)));
                results.removeAllViews();
                for (int i = 0; i < hits.size(); i++) {
                    results.addView(resultRow(c, host, hits.get(i)));
                }
            }
        };

        refreshRef[0] = refresh;

        // ---- the keys
        int key = AppModel.dp(c, KEY_DP), gap = AppModel.dp(c, GAP_DP);
        LinearLayout row = null;
        for (int i = 0; i < KEYS.length(); i++) {
            if (i % PER_ROW == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                left.addView(row, rowParams(gap));
            }
            final char ch = KEYS.charAt(i);
            row.addView(keyView(c, String.valueOf(ch), new Runnable() {
                public void run() { typed.append(Character.toLowerCase(ch)); refresh.run(); }
            }), cellParams(key, key, gap));
        }
        LinearLayout last = new LinearLayout(c);
        last.setOrientation(LinearLayout.HORIZONTAL);
        left.addView(last, rowParams(gap));
        int wide = (PER_ROW * (key + gap) - gap - 2 * gap) / 3;
        last.addView(keyView(c, c.getString(R.string.search_space), new Runnable() {
            public void run() {
                if (typed.length() > 0 && typed.charAt(typed.length() - 1) != ' ') typed.append(' ');
                refresh.run();
            }
        }), cellParams(wide, key, gap));
        last.addView(keyView(c, "⌫", new Runnable() {
            public void run() {
                if (typed.length() > 0) typed.setLength(typed.length() - 1);
                refresh.run();
            }
        }), cellParams(wide, key, gap));
        last.addView(keyView(c, c.getString(R.string.search_clear), new Runnable() {
            public void run() { typed.setLength(0); refresh.run(); }
        }), cellParams(wide, key, 0));

        // ---- how to type faster
        String address = RemoteServer.address();
        TextView tip = label(c, address != null
                ? c.getString(R.string.search_phone_on, address)
                : c.getString(R.string.search_phone_off), 14, 0xFF9BB0C6, false);
        tip.setLineSpacing(AppModel.dp(c, 2), 1f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                key * PER_ROW + gap * (PER_ROW - 1), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = AppModel.dp(c, 16);
        left.addView(tip, tlp);

        refresh.run();
        return root;
    }

    private static LinearLayout.LayoutParams rowParams(int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = gap;
        return lp;
    }

    private static LinearLayout.LayoutParams cellParams(int w, int h, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.rightMargin = right;
        return lp;
    }

    private static TextView label(Context c, String s, int sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Fonts.get(c, medium ? Fonts.MEDIUM : Fonts.REGULAR));
        return t;
    }

    /** One key: a rounded square that brightens on focus; OK presses it. */
    private static View keyView(Context c, String text, final Runnable press) {
        final TextView k = label(c, text, 18, Color.WHITE, true);
        k.setGravity(Gravity.CENTER);
        k.setSingleLine(true);
        k.setFocusable(true);
        k.setFocusableInTouchMode(true);
        k.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AppModel.dp(c, 8));
        bg.setColor(KEY_BG);
        k.setBackground(bg);
        k.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { bg.setColor(has ? KEY_FOCUS : KEY_BG); }
        });
        k.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { press.run(); }
        });
        return k;
    }

    /** One match: its name, and where it lives (the channel's group) on the right. */
    private static View resultRow(final Context c, final SettingsPanel.Host host,
            final SearchIndex.Hit h) {
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setFocusable(true);
        line.setFocusableInTouchMode(true);
        line.setClickable(true);
        line.setPadding(AppModel.dp(c, 18), AppModel.dp(c, 12), AppModel.dp(c, 18), AppModel.dp(c, 12));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AppModel.dp(c, 8));
        bg.setColor(0x0DFFFFFF);
        line.setBackground(bg);
        line.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { bg.setColor(has ? 0x40FFFFFF : 0x0DFFFFFF); }
        });

        TextView name = label(c, h.title, 18, Color.WHITE, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (h.hint != null && h.hint.length() > 0) {
            line.addView(label(c, h.hint, 14, 0xFF9BB0C6, false));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = AppModel.dp(c, 6);
        line.setLayoutParams(lp);
        line.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.closePanel();
                SearchIndex.open(c, h.id);
            }
        });
        return line;
    }
}
