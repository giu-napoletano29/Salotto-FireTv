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
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The launcher UI, built against a plain Context so the same screen can be hosted by an
 * Activity or a WindowManager overlay.
 *
 * Plain, minimal shape on purpose: a greeting and clock top-left, one round Settings button
 * top-right, a translucent dock of the most-used apps, then a single uniform grid for
 * everything else - inputs, Settings, TV Health, Amazon Home and the rest of the apps - with
 * no section headers. "Show Inputs &amp; System" and "Show Non-TV Applications" in Settings
 * still gate exactly what they always did; there just is no longer a labelled band for them.
 *
 * Sections are plain focusable views in a ScrollView rather than a GridView: no recycling,
 * so each view is its item and focus survives a re-sort by key. Data and bitmaps come from
 * the process-wide AppModel.
 */
public class HomeGrid implements SettingsPanel.Host {

    public interface Listener {
        /** Called before the item is acted on, so an overlay host can dismiss itself. */
        void onPick(AppModel.Item it);
    }

    /**
     * Set as a panel root's tag to handle Back itself: return true to consume it, false to
     * let the panel close.
     */
    public interface BackHandler { boolean onBack(); }

    // ---------------------------------------------------------------- layout metrics
    // Margins and gaps match AppModel's own grid math, so every tile - dock or grid - lines
    // up on the same rhythm regardless of column count.
    private static final int MARGIN_DP = 32;
    private static final int GAP_DP = 16;
    private static final int CORNER_DP = 6;
    /** Space between the square tiles under the dock; tighter than the dock's own gap. */
    private static final int GRID_GAP_DP = 12;
    /** The corners of the square tiles under the dock, as a share of the side: Tizen's rounded squares. */
    private static final float SQUARE_CORNER = 0.24f;
    private static final int GRID_ROW_GAP_DP = 24;
    /** Kept free under the last row of square tiles. */
    private static final int GRID_BOTTOM_DP = 24;
    /** Room kept under the focused tile when the page scrolls to it. */
    private static final int BOTTOM_ROOM_DP = 32;
    /** Every tile is at full strength; focus is the white rim alone. */
    private static final float UNFOCUSED_ALPHA = 1f;
    private static final int TOP_RESERVE_DP = 152;
    /** How far the dock sits above the bottom of the first screen. */
    private static final int DOCK_LIFT_DP = 56;
    private static final int TOPBAR_SIDE_DP = 32;
    private static final int TOPBAR_TOP_DP = 26;
    /** The clock sits under the pill row: its top margin plus the pills' own height and a gap. */
    private static final int CLOCK_TOP_DP = 28;
    private static final int DOCK_COUNT = 4;
    // Dock tile height per Prefs.DOCK_SIZE_NAMES; tiles are 16:9, so Large and up scroll
    // sideways past the screen edge rather than shrinking to fit.
    private static final int[] DOCK_H_DP = AppModel.DOCK_H_DP;
    private static final int DOCK_PAD_DP = 6;
    private static final int DOCK_BOTTOM_DP = 6;
    private static final int ICON_SZ_DP = 26;
    /** The icon inside a top-bar pill; the pills are small, so it is smaller than ICON_SZ_DP. */
    private static final int PILL_ICON_DP = 17;
    // One flat neutral card colour for every non-banner tile - hierarchy comes from the
    // dock's translucent band standing apart from the grid, not from a different hue per
    // tile type.
    private static final int CARD_COLOR = 0xFF1B1B1E;
    private static final int DOCK_BAND_COLOR = 0x24FFFFFF;
    private static final int PILL_COLOR = 0x26FFFFFF;
    private static final int PILL_COLOR_FOCUSED = 0x40FFFFFF;
    // The ambient glow behind the dock: a big soft radial blob, recoloured (not moved) to
    // whichever tile's own art is focused - most of it sits off the top-left of the screen,
    // the same way the reference look only ever shows the bottom-right curve of it.
    private static final int GLOW_SIZE_DP = 900;
    private static final int GLOW_TOP_DP = -420;
    private static final int GLOW_LEFT_DP = -260;
    private static final int GLOW_ALPHA = 0x8A;
    // A short, calm tween for the pills' focus change.
    private static final int FOCUS_MS = 140;
    private static final android.view.animation.Interpolator FOCUS_INTERP =
            new android.view.animation.DecelerateInterpolator(1.5f);
    // Tile focus feel: the "springs up and settles" motion of a premium TV launcher - a
    // gentle overshoot in, a softer relax out. See style() below.
    private static final float FOCUS_SCALE = 1.04f;
    private static final int FOCUS_LIFT_DP = 10;    // z-lift, so a focused card casts a shadow
    private static final long FOCUS_IN_MS = 220L;   // grow + settle
    private static final long FOCUS_OUT_MS = 160L;  // relax back
    private static final android.view.animation.Interpolator FOCUS_IN_INTERP =
            new android.view.animation.OvershootInterpolator(2.2f);
    private static final android.view.animation.Interpolator FOCUS_OUT_INTERP =
            new android.view.animation.DecelerateInterpolator(1.4f);

    private final Context ctx;
    private final Listener listener;

    public final FrameLayout root;
    private final ScrollView scroll;
    private final LinearLayout column;
    private final TextView greeting;
    private final TextView clock;
    private final TextView dateLine;
    private final FrameLayout inputsPill;
    private final TextView inputsLabel;
    /** The quick Fire TV settings pills, left of Inputs and Settings; rebuilt with the grid. */
    private final LinearLayout shortcutRow;
    private final LinearLayout pillRow;
    /**
     * The side rail - Network, Apps, System settings, Settings - icons alone at rest, widening
     * with labels while one has focus, the way VIDAA's does. It replaces the two fixed pills
     * the top bar used to end with.
     */
    private LinearLayout rail;
    private final List<FrameLayout> railItems = new ArrayList<FrameLayout>();
    private final List<TextView> railLabels = new ArrayList<TextView>();
    private boolean railOpen;
    private int railLast;
    /** The rail item that opened the page now showing, so closing it lands back there. */
    private View railOrigin;
    private static final int RAIL_OPEN_DP = 264;
    private static final int RAIL_ICON_DP = 24;
    private final List<TextView> shortcutLabels = new ArrayList<TextView>();
    private final List<View> shortcutPills = new ArrayList<View>();
    private String pillsKey = "";
    /** How many quick settings the side rail shows above the Appstore and the two settings pages. */
    private static final int RAIL_SHORTCUTS = 4;
    /** A soft, colour-shifting glow behind the dock - see setGlow(). */
    private final View glow;
    private int glowColor;
    /** The dock's title line, showing whichever dock tile is focused; null when no dock. */
    private TextView dockTitleView;
    /**
     * True once this build has already pointed the Settings pill's down-focus at the first
     * tile below it. ScrollView intercepts an UP/DOWN press at its own edge before normal
     * focus search would reach a sibling outside it (the pill lives on root, not in scroll),
     * so the very first row - the dock's, if there is one, else the grid's - gets an
     * explicit nextFocusUpId back to the pill instead of relying on geometry alone.
     */
    private boolean topRowWired;

    private final Map<String, View> cellsByKey = new HashMap<String, View>();
    /** buildCard() cells and whether each is an input/Live TV tile, for repainting their glass. */
    private final Map<View, Boolean> cardCells = new HashMap<View, Boolean>();
    /** The corner radius of each cell, where it is not the usual one (the square tiles). */
    private final Map<View, Integer> corners = new HashMap<View, Integer>();
    /** While the square tiles are built: their corner radius in pixels; 0 the rest of the time. */
    private int gridCorner;
    /** Keys of the tiles currently in the dock, so focusing one can scroll to the top. */
    private final Set<String> dockKeys = new HashSet<String>();
    /** The horizontally scrolling rows (the dock, if present), in build order. */
    private final List<HorizontalScrollView> rows = new ArrayList<HorizontalScrollView>();
    private View focusedCell;
    private String focusedKey;
    /** AppModel.order() as of the last build, to spot a re-sort done by the other grid. */
    private String builtOrder;
    private boolean restoreShown;
    /** What holds the restore tile; it slides the tile out when the shared clock runs out. */
    private FrameLayout restoreSlot;
    private final Runnable restoreTimeUp = new Runnable() {
        public void run() { slideOutRestore(); }
    };
    /**
     * Bumped whenever either grid changes a layout setting or hides a tile; each grid builds
     * against the value it saw, so the other one can tell it is out of date.
     */
    private static int layoutGen;
    private int builtGen = -1;
    /** Settings / TV Health render inside this window; Back pops one level. */
    private final List<View> panels = new ArrayList<View>();
    /** For each open panel, the view that had focus when the next one opened over it. */
    private final List<View> panelFocus = new ArrayList<View>();
    /**
     * What the panels opened here call back into. A panel keeps the host it was built with,
     * so when the overlay hands its panels to the activity, this is pointed at the
     * activity's grid and the overlay starts a new one.
     */
    private PanelHost panelHost = new PanelHost(this);
    /** The last key released here, logged when a panel opens from a tile. */
    private int lastKey;
    private int lastKeyDevice;
    private int lastKeySource;
    private long lastKeyAt;
    public HomeGrid(Context c, Listener l) {
        this.ctx = c;
        this.listener = l;
        // Before anything below, including the pills' own icons: AppModel.loaded is a
        // process-wide static, but nothing guarantees some earlier HomeGrid in this process
        // already reached it, and the pill needs the Settings item's icon immediately.
        AppModel.ensureLoaded(c);
        Backgrounds.ensureFolder(c);

        // Overridden so Back and Menu reach us before the focused child consumes them; a
        // key listener on the root would never fire.
        root = new FrameLayout(c) {
            @Override protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                armIdle();
                watchNetwork();
            }
            @Override protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                cancelIdle();
                unwatchNetwork();
            }
            @Override public void onWindowFocusChanged(boolean has) {
                super.onWindowFocusChanged(has);
                if (has) {
                    armIdle();
                    forceRepaint();   // back from standby or another app: see forceRepaint()
                } else {
                    cancelIdle();
                }
            }
            @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
                // The press that wakes the idle clock does nothing else.
                if (idleView != null) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) hideIdle();
                    return true;
                }
                armIdle();
                if (e.getAction() == android.view.KeyEvent.ACTION_UP) {
                    lastKey = e.getKeyCode();
                    lastKeyDevice = e.getDeviceId();
                    lastKeySource = e.getSource();
                    lastKeyAt = android.os.SystemClock.uptimeMillis();
                }
                // Back from the side rail returns to the start of the home screen (Canali TV,
                // or the first tile), rather than doing nothing.
                if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK && panels.isEmpty()
                        && railHasFocus()) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_UP) takeStartFocus();
                    return true;
                }
                if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK && !panels.isEmpty()) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_UP) {
                        View top = panels.get(panels.size() - 1);
                        Object t = top.getTag();
                        if (t instanceof BackHandler && ((BackHandler) t).onBack()) return true;
                        closePanel();
                    }
                    return true;
                }
                // Menu hides the focused tile, matching the stock Fire TV launcher's
                // convention (long-press also works).
                if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_MENU && panels.isEmpty()) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_UP) showItemMenu();
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        root.setClipChildren(false);

        // Added first, so it draws behind everything else: a big soft colour blob that
        // recolours (never moves) to whichever tile's own art is focused, the same ambient
        // glow the cleanyourtv default theme sits its dock on.
        glow = new View(c);
        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        int glowSize = Math.round(screenH * 1.05f);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(glowSize, glowSize);
        glowLp.leftMargin = Math.round(screenW * 0.12f) - glowSize / 2;
        glowLp.topMargin = Math.round(screenH * 0.42f) - glowSize / 2;
        root.addView(glow, glowLp);

        final Context scrollCtx = c;
        scroll = new ScrollView(c) {
            // Scrolling to a focused tile leaves room under it, so the last row never sits
            // against the bottom of the screen.
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(
                    android.graphics.Rect rect) {
                rect.bottom += AppModel.dp(scrollCtx, BOTTOM_ROOM_DP);
                return super.computeScrollDeltaToGetChildRectOnScreen(rect);
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(dp(AppModel.RAIL_DP + AppModel.RAIL_GAP_DP), dp(TOP_RESERVE_DP),
                dp(MARGIN_DP), dp(MARGIN_DP));
        scroll.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Top-right, under the pills: a small tracked-out greeting over a large plain clock,
        // then the date as a quiet caption - right-aligned, the way Tizen and VIDAA both put
        // theirs, which also leaves the left edge to the side rail.
        LinearLayout topLeft = new LinearLayout(c);
        topLeft.setOrientation(LinearLayout.VERTICAL);
        topLeft.setGravity(Gravity.RIGHT);

        greeting = new TextView(c);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        greeting.setLetterSpacing(0.12f);
        greeting.setTypeface(Fonts.get(c, Fonts.MEDIUM));
        // Wrap-content and one line: a match-parent TextView doesn't re-measure its parent
        // on setText, so the column kept its old width and "GOOD AFTERNOON" was cut short.
        greeting.setSingleLine(true);
        topLeft.addView(greeting, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        clock = new TextView(c);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        clock.setTypeface(Fonts.get(c, Fonts.LIGHT));
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockLp.topMargin = dp(2);
        // A small network icon sits left of the digits, as on the VIDAA home: the Wi-Fi mark
        // while there is a connection, an amber crossed one when there is not.
        LinearLayout clockRow = new LinearLayout(c);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        netIcon = new ImageView(c);
        netIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(NET_ICON_DP), dp(NET_ICON_DP));
        nlp.rightMargin = dp(14);
        nlp.topMargin = dp(6);
        clockRow.addView(netIcon, nlp);
        clockRow.addView(clock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topLeft.addView(clockRow, clockLp);
        showNet(isOnline());

        dateLine = new TextView(c);
        dateLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dateLine.setTypeface(Fonts.get(c, Fonts.MEDIUM));
        // A caption under the big numerals, not a second line of the same weight.
        dateLine.setAllCaps(true);
        dateLine.setLetterSpacing(0.14f);
        // Same as the greeting: wrap-content and one line, or a longer date is cut short.
        dateLine.setSingleLine(true);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = dp(2);
        topLeft.addView(dateLine, dateLp);

        FrameLayout.LayoutParams topLeftLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        topLeftLp.topMargin = dp(CLOCK_TOP_DP);
        topLeftLp.rightMargin = dp(TOPBAR_SIDE_DP);
        root.addView(topLeft, topLeftLp);

        // Top-right: the quick settings, then Inputs where the TV has any - frosted pills over
        // the glow the way the reference dock's own top bar sits over its ambient colour.
        // Settings and System settings are on the side rail instead.
        inputsLabel = new TextView(c);
        inputsPill = buildPill(inputsLabel, ctx.getString(R.string.home_inputs_pill),
                inputsIcon(dp(ICON_SZ_DP)), new Runnable() {
                    public void run() { jumpToInputs(); }
                });
        inputsPill.setId(View.generateViewId());

        pillRow = new LinearLayout(c);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setClipChildren(false);
        shortcutRow = new LinearLayout(c);
        shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
        shortcutRow.setClipChildren(false);
        pillRow.addView(shortcutRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams inputsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillRow.addView(inputsPill, inputsLp);

        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        pillLp.topMargin = dp(TOPBAR_TOP_DP);
        pillLp.rightMargin = dp(TOPBAR_SIDE_DP);
        root.addView(pillRow, pillLp);
        // The side rail carries Network, Apps and both settings pages now, so the top bar is
        // gone. The pills stay built (the quick-settings code still drives them) but hidden
        // and unfocusable, which keeps Up from the dock from landing on one.
        pillRow.setVisibility(View.GONE);

        buildRail();

        applyBackground();
        build();
        updateClock();
    }

    // ---------------------------------------------------------------- network status

    private static final int NET_ICON_DP = 26;
    private ImageView netIcon;
    private Bitmap netOnBmp, netOffBmp;
    private android.net.ConnectivityManager.NetworkCallback netCallback;

    private boolean isOnline() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return true;   // cannot tell: do not raise a false alarm
        }
    }

    private void showNet(boolean up) {
        if (netOnBmp == null) {
            int px = dp(NET_ICON_DP);
            netOnBmp = ShortcutIcons.render("network", px);
            // The same mark in amber with a slash through it.
            netOffBmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(netOffBmp);
            android.graphics.Paint tint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            tint.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFE8C468,
                    android.graphics.PorterDuff.Mode.SRC_IN));
            cv.drawBitmap(netOnBmp, 0, 0, tint);
            android.graphics.Paint line = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            line.setColor(0xFFE8C468);
            line.setStrokeWidth(px * 0.09f);
            line.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            cv.drawLine(px * 0.14f, px * 0.14f, px * 0.86f, px * 0.86f, line);
        }
        netIcon.setImageBitmap(up ? netOnBmp : netOffBmp);
        netIcon.setAlpha(up ? 0.85f : 1f);
    }

    /** Follows the connection while the home screen is on screen. */
    private void watchNetwork() {
        if (netCallback != null) return;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            netCallback = new android.net.ConnectivityManager.NetworkCallback() {
                private void refresh() {
                    root.post(new Runnable() {
                        public void run() { showNet(isOnline()); }
                    });
                }
                @Override public void onAvailable(android.net.Network n) { refresh(); }
                @Override public void onLost(android.net.Network n) { refresh(); }
            };
            cm.registerNetworkCallback(new android.net.NetworkRequest.Builder().build(), netCallback);
            showNet(isOnline());
        } catch (Throwable t) {
            netCallback = null;
        }
    }

    private void unwatchNetwork() {
        if (netCallback == null) return;
        try {
            ((android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE))
                    .unregisterNetworkCallback(netCallback);
        } catch (Throwable ignored) { }
        netCallback = null;
    }

    // ---------------------------------------------------------------- quick settings

    /**
     * The quick Fire TV settings: up to RAIL_SHORTCUTS screens the user chose, which become
     * the first entries of the side rail. (The top bar's pills are no longer shown, so only
     * the rail is rebuilt, and only when the choice changes.)
     */
    private void rebuildShortcutPills() {
        boolean inputs = false;
        List<AppModel.Item> all = AppModel.allItems();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).type == AppModel.TYPE_INPUT) inputs = true;
        }
        inputsPill.setVisibility(inputs ? View.VISIBLE : View.GONE);

        boolean on = Prefs.shortcutsPlace(ctx) != Prefs.SHORTCUTS_OFF;
        java.util.Set<String> want = Prefs.shortcuts(ctx);
        List<String[]> screens = SystemSettingsPanel.available(ctx, null);
        StringBuilder key = new StringBuilder(on ? "on" : "off");
        for (int i = 0; i < screens.size(); i++) {
            if (want.contains(screens.get(i)[SystemSettingsPanel.ID])) {
                key.append(',').append(screens.get(i)[SystemSettingsPanel.ID]);
            }
        }
        if (hasInputs()) key.append(",inputs");
        if (Prefs.railPhone(ctx)) key.append(",phone");
        // Same entries as before: leave the rail, so one with focus keeps it through a reload.
        if (key.toString().equals(pillsKey)) return;
        pillsKey = key.toString();

        List<String[]> chosen = new ArrayList<String[]>();
        for (int i = 0; on && i < screens.size() && chosen.size() < RAIL_SHORTCUTS; i++) {
            if (want.contains(screens.get(i)[SystemSettingsPanel.ID])) chosen.add(screens.get(i));
        }
        rebuildRail(chosen);
    }

    private void addShortcutPill(String text, Bitmap icon, Runnable action) {
        TextView label = new TextView(ctx);
        FrameLayout pill = buildPill(label, text, icon, action);
        pill.setId(View.generateViewId());
        paintText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        shortcutRow.addView(pill, lp);
        shortcutLabels.add(label);
        shortcutPills.add(pill);
    }

    /** A plain monitor glyph for the Inputs pill - this is chrome, not a grid item, so it is
     *  drawn here rather than added to AppModel's own icon set. */
    private Bitmap inputsIcon(int px) {
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Paint stroke = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(android.graphics.Paint.Style.STROKE);
        stroke.setStrokeWidth(px * 0.09f);
        stroke.setColor(Color.WHITE);
        android.graphics.RectF screen = new android.graphics.RectF(
                px * 0.10f, px * 0.14f, px * 0.90f, px * 0.68f);
        cv.drawRoundRect(screen, px * 0.08f, px * 0.08f, stroke);

        android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        cv.drawRect(px * 0.42f, px * 0.70f, px * 0.58f, px * 0.80f, fill);
        android.graphics.RectF base = new android.graphics.RectF(
                px * 0.26f, px * 0.80f, px * 0.74f, px * 0.86f);
        cv.drawRoundRect(base, px * 0.02f, px * 0.02f, fill);
        return bmp;
    }

    /** Jumps focus straight to the first Live TV / HDMI tile - the Inputs pill's one job. */
    private void jumpToInputs() {
        List<AppModel.Item> all = AppModel.visibleItems(ctx);
        for (int i = 0; i < all.size(); i++) {
            int t = all.get(i).type;
            if (AppModel.isLive(t) || t == AppModel.TYPE_INPUT) {
                selectKey(all.get(i).key());
                return;
            }
        }
    }

    // ---------------------------------------------------------------- side rail

    private static final int RAIL_REST = 0x8C000000, RAIL_OPEN = 0xF0000000;
    /** How strong the white line and glow on the rail's right edge are, closed and open. */
    private static final float EDGE_CLOSED = 0.18f, EDGE_OPEN = 0.03f;
    private RailGlass railBg;

    /**
     * The rail's background, glass without blur (a real blur needs API 31): a tinted
     * translucent fill with a soft sheen from the top, a faint white glow inside its right
     * edge and a thin white line on that edge that fades out towards the top and bottom.
     * The fill colour is animated by the rail's open/close.
     */
    private static final class RailGlass extends Drawable {
        private final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float line, glowW;
        private int fill;
        private int tint;
        private float open;
        /** How strong the edge glow and line are: full when closed, much fainter when open. */
        private int edgeAlpha = Math.round(255f * EDGE_CLOSED);   // as closed, until it opens
        private android.graphics.LinearGradient sheen, rim, glow;

        RailGlass(float line, int fill) {
            this.line = line;
            this.glowW = line * 12f;
            this.fill = fill;
        }

        /** open is 0 (closed) to 1 (open). */
        /** The background's main colour, dark: the fill takes its hue from it. */
        void setHue(int rgb) {
            tint = rgb & 0x00FFFFFF;
            invalidateSelf();
        }

        void setFill(int c, float open) {
            fill = c;
            this.open = open;
            edgeAlpha = Math.round(255f * (EDGE_CLOSED - (EDGE_CLOSED - EDGE_OPEN) * open));
            invalidateSelf();
        }

        @Override protected void onBoundsChange(android.graphics.Rect r) {
            android.graphics.Shader.TileMode clamp = android.graphics.Shader.TileMode.CLAMP;
            sheen = new android.graphics.LinearGradient(0, r.top, 0, r.bottom,
                    0x26FFFFFF, 0x08FFFFFF, clamp);
            rim = new android.graphics.LinearGradient(0, r.top, 0, r.bottom,
                    new int[]{ 0x00FFFFFF, 0x99FFFFFF, 0x99FFFFFF, 0x00FFFFFF },
                    new float[]{ 0f, 0.28f, 0.72f, 1f }, clamp);
            glow = new android.graphics.LinearGradient(r.right - glowW, 0, r.right, 0,
                    0x00FFFFFF, 0x2EFFFFFF, clamp);
        }

        @Override public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            if (b.isEmpty()) return;
            paint.setShader(null);
            // The fill's alpha is animated. Its colour is the wallpaper's dark tone while the
            // rail is closed, and open it is the settings pages' own: that tone at a quarter
            // strength over black.
            float k = 1f - 0.75f * open;
            int rgb = (Math.round(((tint >> 16) & 0xFF) * k) << 16)
                    | (Math.round(((tint >> 8) & 0xFF) * k) << 8)
                    | Math.round((tint & 0xFF) * k);
            paint.setColor((fill & 0xFF000000) | rgb);
            cv.drawRect(b, paint);
            // The glass sheen is for the closed rail; open, the fill is the plain page colour.
            paint.setShader(sheen);
            paint.setAlpha(Math.round(255f * (1f - open)));
            cv.drawRect(b, paint);
            paint.setAlpha(edgeAlpha);
            paint.setShader(glow);
            cv.drawRect(b.right - glowW, b.top, b.right, b.bottom, paint);
            paint.setShader(rim);
            cv.drawRect(b.right - line, b.top, b.right, b.bottom, paint);
            paint.setShader(null);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    private android.animation.ValueAnimator railAnim;

    private void buildRail() {
        rail = new LinearLayout(ctx);
        rail.setOrientation(LinearLayout.VERTICAL);
        // Two groups, as on Tizen: the way in to things (search, apps, quick settings) from the
        // top, the settings pages from the bottom, with a gap that takes the rest.
        rail.setGravity(Gravity.TOP);
        rail.setPadding(0, dp(36), 0, dp(36));
        railBg = new RailGlass(Math.max(2f, dp(1)), RAIL_REST);
        rail.setBackground(railBg);

        // The entries come with the quick settings: see rebuildRail().
        root.addView(rail, new FrameLayout.LayoutParams(dp(AppModel.RAIL_DP),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
    }

    /**
     * The rail's entries: the quick settings the user chose (up to RAIL_SHORTCUTS, none when
     * they are off), then the Appstore, System settings and Settings, which
     * are always there. Rebuilt when the choice changes.
     */
    private void rebuildRail(List<String[]> quick) {
        boolean hadFocus = railHasFocus();
        rail.removeAllViews();
        railItems.clear();
        railLabels.clear();
        for (int i = 0; i < quick.size(); i++) {
            final String[] s = quick.get(i);
            addRailItem(ShortcutIcons.render(s[SystemSettingsPanel.ID], dp(RAIL_ICON_DP)),
                    SystemSettingsPanel.title(ctx, s[SystemSettingsPanel.ID]), new Runnable() {
                        public void run() {
                            logOpen(s[SystemSettingsPanel.TITLE] + " (rail)");
                            SetupPanel.start(ctx, SystemSettingsPanel.intentFor(
                                    s[SystemSettingsPanel.ACTION]));
                        }
                    });
        }
        // On a TV with inputs (HDMI, composite): the way to switch source, after the quick
        // settings and before the Appstore.
        if (hasInputs()) {
            // The TV's own menu is opened through the debugging link, which drops after a
            // short idle: bring it up while the item has focus, so OK finds it ready.
            addRailItem(inputsIcon(dp(RAIL_ICON_DP)), ctx.getString(R.string.rail_inputs),
                    new Runnable() {
                        public void run() { openInputs(); }
                    },
                    new Runnable() {
                        public void run() {
                            if (tvHasInputMenu() && AdbBridge.canRun(ctx)) {
                                AdbBridge.init(ctx);
                                AdbBridge.connect();
                            }
                        }
                    });
        }
        addRailItem(ShortcutIcons.render("search", dp(RAIL_ICON_DP)),
                ctx.getString(R.string.rail_search), new Runnable() {
                    public void run() {
                        logOpen("Search (rail)");
                        showPanel(SearchPanel.build(ctx, panelHost));
                    }
                });
        addRailItem(ShortcutIcons.render("applications", dp(RAIL_ICON_DP)),
                ctx.getString(R.string.rail_apps), new Runnable() {
                    public void run() {
                        logOpen("Appstore (rail)");
                        openAppstore();
                    }
                });
        // The gap between the two groups. It is not focusable, so the arrows step over it.
        rail.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
        // Shown whether the control is on or off (that page is where it is switched on); the
        // System settings page can take the icon off the rail.
        if (Prefs.railPhone(ctx)) {
            addRailItem(ShortcutIcons.render("phone", dp(RAIL_ICON_DP)),
                    ctx.getString(R.string.remote_title), new Runnable() {
                        public void run() {
                            logOpen("Phone control (rail)");
                            showPanel(RemotePanel.build(ctx, panelHost, new Runnable() {
                                public void run() { }
                            }));
                        }
                    });
        }
        addRailItem(ShortcutIcons.render("settings", dp(RAIL_ICON_DP)),
                ctx.getString(R.string.sys_title), new Runnable() {
                    public void run() {
                        logOpen("System settings (rail)");
                        showPanel(SystemSettingsPanel.build(ctx, panelHost));
                    }
                });
        addRailItem(ShortcutIcons.render("preferences", dp(RAIL_ICON_DP)),
                ctx.getString(R.string.home_settings_pill), new Runnable() {
                    public void run() {
                        logOpen("Settings (rail)");
                        showPanel(SettingsPanel.build(ctx, panelHost));
                    }
                });

        // Nothing to the left of the rail, and Up/Down stop at its ends rather than hunting
        // for the nearest focusable thing (a pill, a tile) somewhere else on the screen.
        for (int i = 0; i < railItems.size(); i++) {
            View v = railItems.get(i);
            v.setNextFocusLeftId(v.getId());
            // Each item names its neighbours: with the gap between the two groups the focus
            // search would find a tile of the grid nearer than the next item of the rail.
            v.setNextFocusUpId(i == 0 ? v.getId() : railItems.get(i - 1).getId());
            v.setNextFocusDownId(i == railItems.size() - 1 ? v.getId() : railItems.get(i + 1).getId());
        }
        // Rebuilt while open (a choice made from a page opened on the rail): labels show.
        if (railOpen) {
            for (int i = 0; i < railLabels.size(); i++) railLabels.get(i).setAlpha(1f);
        }
        railLast = Math.min(railLast, railItems.size() - 1);
        if (hadFocus && panels.isEmpty()) focusRail();
    }

    private void addRailItem(Bitmap icon, String text, final Runnable action) {
        addRailItem(icon, text, action, null);
    }

    /** onFocus, if given, runs each time the item gains focus. */
    private void addRailItem(Bitmap icon, String text, final Runnable action,
                             final Runnable onFocus) {
        final FrameLayout cell = new FrameLayout(ctx);
        cell.setId(View.generateViewId());
        cell.setFocusable(true);
        cell.setFocusableInTouchMode(true);
        final int index = railItems.size();

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Centres the icon in the collapsed rail (64dp wide).
        row.setPadding(dp(20), 0, dp(8), 0);

        ImageView iv = new ImageView(ctx);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(icon);
        row.addView(iv, new LinearLayout.LayoutParams(dp(RAIL_ICON_DP), dp(RAIL_ICON_DP)));

        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTypeface(Fonts.get(ctx, Fonts.MEDIUM));
        label.setTextColor(0xFFF0F1F3);
        label.setSingleLine(true);
        label.setAlpha(0f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = dp(16);
        row.addView(label, llp);
        railLabels.add(label);

        cell.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                railStyle(cell, has);
                if (has) {
                    railLast = index;
                    setRailOpen(true);
                    if (onFocus != null) onFocus.run();
                } else {
                    // Moving between two rail items passes through "no focus" for a moment.
                    root.post(new Runnable() {
                        public void run() { if (!railHasFocus()) setRailOpen(false); }
                    });
                }
            }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                railOrigin = cell;
                action.run();
                // Only a page that opened (synchronously) is closed back to this item.
                if (panels.isEmpty()) railOrigin = null;
            }
        });
        railStyle(cell, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(2);
        lp.bottomMargin = dp(2);
        rail.addView(cell, lp);
        railItems.add(cell);
    }

    private void railStyle(FrameLayout cell, boolean focused) {
        // A plain band across the rail's whole width: no rounding, no border, and see-through
        // enough that the rail's own dark still reads through it.
        cell.setBackgroundColor(focused ? 0x40FFFFFF : 0x00000000);
    }

    private boolean railHasFocus() {
        for (int i = 0; i < railItems.size(); i++) {
            if (railItems.get(i).hasFocus()) return true;
        }
        return false;
    }

    /** Opens the rail over the grid with its labels, or folds it back to icons. */
    private void setRailOpen(boolean open) {
        if (open == railOpen) return;
        railOpen = open;
        if (railAnim != null) railAnim.cancel();
        final int closed = dp(AppModel.RAIL_DP), wide = dp(RAIL_OPEN_DP);
        final int from = rail.getLayoutParams().width;
        final int to = open ? wide : closed;
        railAnim = android.animation.ValueAnimator.ofInt(from, to);
        railAnim.setDuration(open ? 200 : 160);
        railAnim.setInterpolator(FOCUS_INTERP);
        railAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator va) {
                int w = (Integer) va.getAnimatedValue();
                float f = Math.max(0f, Math.min(1f, (w - closed) / (float) (wide - closed)));
                rail.getLayoutParams().width = w;
                rail.requestLayout();
                railBg.setFill((Integer) new android.animation.ArgbEvaluator()
                        .evaluate(f, RAIL_REST, RAIL_OPEN), f);
                for (int i = 0; i < railLabels.size(); i++) railLabels.get(i).setAlpha(f);
            }
        });
        railAnim.start();
    }

    /** Focus onto the rail, on the item last used. */
    private void focusRail() {
        if (railItems.isEmpty()) return;
        railItems.get(Math.min(railLast, railItems.size() - 1)).requestFocus();
    }

    // ---------------------------------------------------------------- inputs

    /** Key code of the remote's INPUT button (KeyEvent.KEYCODE_TV_INPUT). */
    private static final int KEYCODE_TV_INPUT = 178;
    /** Fire TV's own quick-settings app, which draws the TV's input list when INPUT is pressed. */
    private static final String QUICK_SETTINGS_PKG = "com.amazon.tv.quicksettings.ui";

    /** Whether this TV has inputs to switch between: HDMI, composite, a tuner. */
    private boolean hasInputs() {
        List<AppModel.Item> all = AppModel.allItems();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).type == AppModel.TYPE_INPUT) return true;
        }
        return false;
    }

    /** Whether the TV itself has an input menu the INPUT button opens. */
    private boolean tvHasInputMenu() {
        try {
            ctx.getPackageManager().getPackageInfo(QUICK_SETTINGS_PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The Inputs entry: the TV's own input list where there is one, as the INPUT button opens
     * it. There is no link for it (it answers only the key), so the key is sent over the same
     * on-device ADB link the Screen & sound page uses; with no link, or on a TV without that
     * menu, Homestead's own list opens instead.
     */
    private void openInputs() {
        logOpen("Inputs (rail)");
        if (tvHasInputMenu() && AdbBridge.canRun(ctx)) {
            // The link may have gone idle and shut down: the first command after that often
            // finds it still starting, so it is tried again (a few times) before giving up.
            AdbBridge.runRetry(new String[]{ "input keyevent " + KEYCODE_TV_INPUT },
                    new AdbBridge.Callback() {
                        public void onDone(boolean ok, String output) {
                            if (ok) return;
                            android.util.Log.w("Homestead", "TV input menu not opened: " + output);
                            Toast.makeText(ctx, R.string.inputs_native_failed,
                                    Toast.LENGTH_LONG).show();
                            showPanel(buildInputsPanel());
                        }
                    });
            return;
        }
        if (tvHasInputMenu()) {
            // The TV has its own menu but there is no link to press the key through.
            Toast.makeText(ctx, R.string.inputs_native_failed, Toast.LENGTH_LONG).show();
        }
        showPanel(buildInputsPanel());
    }

    /** Homestead's own input list: one row per input, a connected one marked. */
    private View buildInputsPanel() {
        final Panel p = new Panel(ctx, ctx.getString(R.string.rail_inputs), null, Panel.SHEET);
        List<AppModel.Item> all = AppModel.allItems();
        for (int i = 0; i < all.size(); i++) {
            final AppModel.Item it = all.get(i);
            if (it.type != AppModel.TYPE_INPUT && !AppModel.isLive(it.type)) continue;
            if (it.type == AppModel.TYPE_CHANNELS) continue;   // not a source of the TV
            Panel.Row r = p.addRow(it.label, it.connected ? ctx.getString(R.string.home_connected) : "",
                    new Runnable() {
                        public void run() {
                            closePanel();
                            if (listener != null) listener.onPick(it);
                            act(it);
                        }
                    });
            if (it.connected) r.setValueColor(0xFFA7D28F);
        }
        p.addRow(ctx.getString(R.string.home_back), "", new Runnable() {
            public void run() { closePanel(); }
        });
        return p.root;
    }

    /** The Amazon Appstore - where new apps come from; managing installed ones is in System settings. */
    private void openAppstore() {
        android.content.Intent i = ctx.getPackageManager().getLaunchIntentForPackage("com.amazon.venezia");
        if (i != null) SetupPanel.start(ctx, i);
    }

    private FrameLayout buildPill(final TextView label, String text, Bitmap icon,
                                   final Runnable action) {
        final FrameLayout cell = new FrameLayout(ctx);
        cell.setFocusable(true);
        cell.setFocusableInTouchMode(true);
        cell.setClipChildren(false);
        cell.setPadding(dp(12), dp(6), dp(15), dp(6));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        if (icon != null) {
            ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    dp(PILL_ICON_DP), dp(PILL_ICON_DP));
            ilp.rightMargin = dp(7);
            row.addView(iv, ilp);
        }

        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Fonts.get(ctx, Fonts.MEDIUM));
        row.addView(label);

        cell.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { pillStyle(cell, has); }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        pillStyle(cell, false);
        return cell;
    }

    /**
     * A frosted-glass look: a soft gradient fill (brighter at the top, like light catching
     * the top of a glass surface) plus a thin bright rim, rather than one flat translucent
     * tone - this device has no real-time blur (RenderEffect needs API 31; this TV is 30),
     * so the glass read comes from the gradient and rim alone.
     */
    private void pillStyle(FrameLayout cell, boolean focused) {
        cell.animate().cancel();
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{ 0x4DFFFFFF, 0x33FFFFFF } : new int[]{ 0x33FFFFFF, 0x1ABEBEBE });
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), focused ? 0x66FFFFFF : 0x29FFFFFF);
        cell.setBackground(bg);
        float scale = focused ? 1.05f : 1f;
        cell.animate().scaleX(scale).scaleY(scale)
                .setDuration(FOCUS_MS).setInterpolator(FOCUS_INTERP).start();
    }

    /**
     * Recolours the ambient glow to the focused tile's own banner colour, or turns it off
     * for a tile with no art (Settings, an HDMI input, TV Health...). Set directly, not
     * animated - a cross-fade here would mean caching two gradients per move for a cosmetic
     * touch the eye mostly reads as "the colour changed", not "it faded".
     */
    private void setGlow(int accent) {
        if (accent == glowColor) return;
        glowColor = accent;
        if (accent == 0) {
            glow.setBackground(null);
            return;
        }
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        d.setGradientRadius(glow.getLayoutParams().width / 2f);
        d.setColors(new int[]{ (GLOW_ALPHA << 24) | (accent & 0x00FFFFFF), accent & 0x00FFFFFF });
        glow.setBackground(d);
    }

    // ---------------------------------------------------------------- background / theme

    /** The chosen look; the overlay and the activity re-read it when they come up. */
    private Backgrounds.Preset look;
    /** What is painted now, so an unchanged setting costs only a file stat. */
    private String painted;

    /**
     * Read the background setting and repaint what depends on it. Cheap; no rebuild. A
     * photo is decoded off the main thread, and this runs again once it has loaded.
     */
    public void applyBackground() {
        Backgrounds.Preset p = Backgrounds.current(ctx);
        Bitmap photo = null;
        String state = p.name;
        if (!Backgrounds.photoChosen(ctx)) {
            Backgrounds.drop();
        } else {
            java.io.File f = Backgrounds.photoFile(ctx);
            if (f == null) Backgrounds.drop();
            String key = Backgrounds.keyOf(f);
            photo = Backgrounds.photoFor(key);
            if (f != null && photo == null) {
                Backgrounds.load(ctx, f, new Runnable() {
                    public void run() { applyBackground(); }
                });
                // Keep the previous photo up until the replaced one has loaded.
                photo = Backgrounds.lastPhoto();
                key = "loading";
            }
            state = "photo|" + (photo == null ? "none" : key) + "|" + Backgrounds.dimAlpha(ctx);
        }
        if (state.equals(painted)) return;
        painted = state;
        look = p;
        // Drawn once and kept as a single opaque picture (see Backgrounds.bake): the same one
        // is reused whenever the state is unchanged, as on every resume.
        Drawable wall = Backgrounds.bakedOrNull(ctx, state);
        if (wall == null) {
            wall = Backgrounds.bake(ctx, state, photo != null
                    ? Backgrounds.withScrim(ctx, Backgrounds.photoDrawable(ctx, photo),
                            Backgrounds.scrimColorFor(photo))
                    : Backgrounds.withScrim(ctx, Backgrounds.drawable(p), p.from));
        }
        root.setBackground(wall);
        // The rail takes a little of the background's colour: a photo's own dark tone, or a
        // preset's main colour taken down to a dark shade of it.
        int railTone;
        if (photo != null) {
            railTone = Backgrounds.scrimColorFor(photo);
        } else {
            int t = p.tint();
            railTone = Color.rgb(Color.red(t) * 35 / 100, Color.green(t) * 35 / 100,
                    Color.blue(t) * 35 / 100);
        }
        railBg.setHue(railTone);
        paintText(greeting);
        paintText(clock);
        paintText(dateLine);
        paintText(inputsLabel);
        for (int i = 0; i < shortcutLabels.size(); i++) paintText(shortcutLabels.get(i));
        for (int i = 0; i < column.getChildCount(); i++) {
            View v = column.getChildAt(i);
            if (v instanceof TextView) paintText((TextView) v);
        }
        // The ring and the outline are per look, so every cell is repainted (not animated:
        // this is a palette swap, not a focus change).
        for (View cell : cellsByKey.values()) style(cell, cell == focusedCell, false);
        for (Map.Entry<View, Boolean> e : cardCells.entrySet()) {
            Drawable bg = e.getKey().getBackground();
            if (bg instanceof GradientDrawable) ((GradientDrawable) bg).setColor(cardColor(e.getValue()));
        }
    }

    /**
     * On a photo background, cards are translucent "glass" panels so the picture shows
     * through, matching the premium top-shelf look; the outline() hairline (the photo look
     * carries one) gives them a crisp lit edge. Presets keep the flat card.
     */
    private int cardColor(boolean textTile) {
        if (look == Backgrounds.PHOTO_LOOK) return textTile ? 0xA6222831 : 0xA6161A20;
        // A dark glass tinted with the look's own colour, so the cards belong to the
        // background instead of sitting on it as flat grey.
        int t = look.tint();
        float k = textTile ? 0.66f : 0.74f;
        int r = Math.round(Color.red(t) * (1 - k) + 0x0A * k);
        int g = Math.round(Color.green(t) * (1 - k) + 0x0E * k);
        int b = Math.round(Color.blue(t) * (1 - k) + 0x14 * k);
        // Opaque: a translucent card shows the shadow it casts when it lifts on focus, as a
        // darker rectangle inside it.
        return Color.rgb(r, g, b);
    }

    /** Header and clock colour; on a photo, a soft shadow keeps them readable. */
    private void paintText(TextView t) {
        t.setTextColor(look.text);
        if (look == Backgrounds.PHOTO_LOOK) t.setShadowLayer(dp(4), 0, dp(1), 0xCC000000);
        else t.setShadowLayer(0, 0, 0, 0);
    }

    private int dp(int v) { return AppModel.dp(ctx, v); }

    // ---------------------------------------------------------------- public API

    public void updateClock() {
        int mode = Prefs.clockMode(ctx);
        if (mode == Prefs.CLOCK_OFF) {
            clock.setText("");
            greeting.setText("");
            dateLine.setText("");
            return;
        }
        Date now = new Date();
        String fmt = (mode == Prefs.CLOCK_12H) ? "h:mm a" : "HH:mm";
        clock.setText(new SimpleDateFormat(fmt, Locale.getDefault()).format(now));
        // The greeting over the clock and the date under it follow their own settings.
        boolean g = Prefs.greeting(ctx);
        greeting.setVisibility(g ? View.VISIBLE : View.GONE);
        if (g) greeting.setText(greetingFor(Calendar.getInstance()).toUpperCase(Locale.getDefault()));
        int df = Prefs.dateFormat(ctx);
        dateLine.setVisibility(df != Prefs.DATE_OFF ? View.VISIBLE : View.GONE);
        if (df == Prefs.DATE_LONG) {
            String pat = android.text.format.DateFormat.getBestDateTimePattern(
                    Locale.getDefault(), "EEEEdMMMM");
            dateLine.setText(new SimpleDateFormat(pat, Locale.getDefault()).format(now));
        } else if (df == Prefs.DATE_SHORT) {
            dateLine.setText(android.text.format.DateFormat.getDateFormat(ctx).format(now));
        }
    }

    private String greetingFor(Calendar cal) {
        int h = cal.get(Calendar.HOUR_OF_DAY);
        if (h < 5) return ctx.getString(R.string.home_greeting_night);
        if (h < 12) return ctx.getString(R.string.home_greeting_morning);
        if (h < 18) return ctx.getString(R.string.home_greeting_afternoon);
        return ctx.getString(R.string.home_greeting_evening);
    }

    // ---------------------------------------------------------------- idle clock

    private final android.os.Handler idleHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private FrameLayout idleView;
    private LinearLayout idleBox;
    private TextView idleClock, idleDate;
    private int idleTicks;
    private final Runnable idleStart = new Runnable() {
        public void run() { showIdle(); }
    };
    private final Runnable idleTick = new Runnable() {
        public void run() { tickIdle(); }
    };
    private static final long IDLE_TICK_MS = 15000L;

    /** Start the wait again; called on every key and whenever this window comes back. */
    private void armIdle() {
        idleHandler.removeCallbacks(idleStart);
        int m = Prefs.idleMinutes(ctx);
        if (m > 0 && idleView == null) idleHandler.postDelayed(idleStart, m * 60000L);
    }

    private void cancelIdle() {
        idleHandler.removeCallbacks(idleStart);
        idleHandler.removeCallbacks(idleTick);
        if (idleView != null) {
            root.removeView(idleView);
            idleView = null;
        }
    }

    /** A big clock over everything, nearly black; it moves a little each minute. */
    private void showIdle() {
        if (idleView != null || !root.isAttachedToWindow() || !root.hasWindowFocus()
                || root.getWindowVisibility() != View.VISIBLE) return;
        FrameLayout v = new FrameLayout(ctx);
        v.setClickable(true);
        v.setFocusable(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientRadius(dp(900));
        bg.setGradientCenter(0.5f, 0.5f);
        // Opaque, so the home screen does not show through behind the digits: the look's colour
        // at a fifth of its strength in the middle, near black at the edges.
        int tint = look == null ? 0xFF000000 : look.tint();
        int mid = Color.rgb(Color.red(tint) / 5, Color.green(tint) / 5, Color.blue(tint) / 5);
        bg.setColors(new int[]{ mid, 0xFF020304 });
        bg.setDither(true);
        v.setBackground(bg);
        // With a photo or painting as the background, the idle clock sits on a blurred copy
        // of it, dimmed hard: still a quiet, dark screen (and no bright static picture for a
        // TV to burn in), but recognisably the same wallpaper.
        Bitmap shot = Backgrounds.photoChosen(ctx) ? Backgrounds.lastPhoto() : null;
        Bitmap soft = shot != null ? Backgrounds.blurred(shot) : null;
        if (soft != null) {
            v.setBackground(new android.graphics.drawable.LayerDrawable(
                    new Drawable[]{ Backgrounds.fill(soft),
                            new android.graphics.drawable.ColorDrawable(0xC2000000) }));
        }

        idleBox = new LinearLayout(ctx);
        idleBox.setOrientation(LinearLayout.VERTICAL);
        idleBox.setGravity(Gravity.CENTER_HORIZONTAL);
        idleClock = new TextView(ctx);
        idleClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120);
        idleClock.setTypeface(Fonts.get(ctx, Fonts.THIN));
        idleClock.setTextColor(0xE6FFFFFF);
        idleClock.setIncludeFontPadding(false);
        idleBox.addView(idleClock);
        idleDate = new TextView(ctx);
        idleDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        idleDate.setTextColor(0x99FFFFFF);
        idleDate.setLetterSpacing(0.04f);
        idleBox.addView(idleDate);
        v.addView(idleBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        idleView = v;
        idleTicks = 0;
        root.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(600L).start();
        tickIdle();
    }

    private void tickIdle() {
        if (idleView == null) return;
        Date now = new Date();
        String fmt = (Prefs.clockMode(ctx) == Prefs.CLOCK_12H) ? "h:mm" : "HH:mm";
        idleClock.setText(new SimpleDateFormat(fmt, Locale.getDefault()).format(now));
        String pat = android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "EEEEdMMMM");
        idleDate.setText(new SimpleDateFormat(pat, Locale.getDefault()).format(now));
        // Every minute the pair drifts to another spot, so no pixel stays lit for long.
        if (idleTicks % 4 == 0) {
            java.util.Random r = new java.util.Random();
            idleBox.animate().translationX((r.nextFloat() - 0.5f) * dp(260))
                    .translationY((r.nextFloat() - 0.5f) * dp(170))
                    .setDuration(2500L).start();
        }
        idleTicks++;
        idleHandler.removeCallbacks(idleTick);
        idleHandler.postDelayed(idleTick, IDLE_TICK_MS);
    }

    private void hideIdle() {
        idleHandler.removeCallbacks(idleTick);
        if (idleView != null) {
            root.removeView(idleView);
            idleView = null;
            forceRepaint();
        }
        armIdle();
    }

    /**
     * Redraws the whole window, now and again once a window transition is over. After the idle
     * clock or a wake from standby the Stick can leave part of an old frame on screen - half a
     * wallpaper, the digits of the idle clock showing through - because the system redraws
     * only what it thinks changed; the dock and clock repaint, the wallpaper behind them does
     * not. Invalidating the root marks all of it changed.
     */
    public void forceRepaint() {
        refreshBackground();
        root.invalidate();
        root.postDelayed(new Runnable() {
            public void run() {
                refreshBackground();
                root.invalidate();
                // Bounds are only given at the next draw, so look a little later.
                root.postDelayed(new Runnable() {
                    public void run() { logBackground("after draw"); }
                }, 1500L);
            }
        }, 700L);
    }

    /** "Class[bounds]{layers}" of a drawable, for the log. */
    private static String describe(Drawable d) {
        if (d == null) return "none";
        StringBuilder sb = new StringBuilder(d.getClass().getSimpleName())
                .append(d.getBounds().toShortString());
        if (d instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable l = (android.graphics.drawable.LayerDrawable) d;
            sb.append('{');
            for (int i = 0; i < l.getNumberOfLayers(); i++) {
                sb.append(i == 0 ? "" : " ").append(describe(l.getDrawable(i)));
            }
            sb.append('}');
        }
        return sb.toString();
    }

    /**
     * Builds the wallpaper drawable again and hands it to the root, as choosing the wallpaper
     * anew does - which is what has put right a wallpaper left black or half-drawn after a
     * start or a wake. A fresh drawable gets its own bounds and its own draw ops.
     */
    private void refreshBackground() {
        painted = null;
        applyBackground();
    }

    /** What the root and its wallpaper look like now, for a log taken after a glitch. */
    private void logBackground(String when) {
        Drawable bg = root.getBackground();
        Bitmap shot = Backgrounds.lastPhoto();
        android.util.Log.i("Homestead", "wallpaper " + when + ": root " + root.getWidth() + "x"
                + root.getHeight() + " dir " + root.getLayoutDirection() + ", drawable "
                + describe(bg) + ", photo "
                + (shot == null ? "none" : shot.getWidth() + "x" + shot.getHeight()
                        + (shot.isRecycled() ? " RECYCLED" : "")));
    }

    /** Focus for a fresh Home press: Live TV when it comes first, else as takeFocus(). */
    public void takeStartFocus() {
        // The restore tile is the one thing worth seeing before anything else.
        if (AppModel.restorePopupLive() && selectKey(AppModel.RESTORE_KEY)) {
            scroll.scrollTo(0, 0);
            return;
        }
        if (liveTvFirst() && (selectKey(AppModel.LIVETV_KEY) || selectKey(AppModel.CHANNELS_KEY))) {
            scroll.scrollTo(0, 0);
            return;
        }
        takeFocus();
    }

    /**
     * Live TV first is in effect: switched on, and this TV has the tile. Without the tile
     * the switch is not offered in Settings, so a stale "on" must not keep the layout.
     */
    private boolean liveTvFirst() {
        return Prefs.liveTvFirst(ctx) && AppModel.hasLiveTv();
    }

    /**
     * The dock has no header above it, so focusing one of its tiles scrolls the page to the
     * top. Repeated after layout, which would otherwise scroll it flush with the screen edge.
     */
    private void keepAtTop() {
        scroll.scrollTo(0, 0);
        scroll.post(new Runnable() {
            public void run() { scroll.scrollTo(0, 0); }
        });
    }

    /**
     * Scrolls the dock so the focused tile keeps the row's padding on both sides. The scroll
     * view on its own stops flush with the tile's unscaled edge, which clips the zoom and the
     * focus rim of an end tile against the band at the larger dock sizes.
     */
    private void revealInDock(final View cell) {
        cell.post(new Runnable() {
            public void run() {
                if (!(cell.getParent() instanceof ViewGroup)) return;
                ViewGroup row = (ViewGroup) cell.getParent();
                if (!(row.getParent() instanceof HorizontalScrollView)) return;
                HorizontalScrollView hs = (HorizontalScrollView) row.getParent();
                int x = hs.getScrollX();
                int left = cell.getLeft() - row.getPaddingLeft();
                int right = cell.getRight() + row.getPaddingRight();
                if (left < x) hs.smoothScrollTo(left, 0);
                else if (right > x + hs.getWidth()) hs.smoothScrollTo(right - hs.getWidth(), 0);
            }
        });
    }

    public void takeFocus() {
        if (focusedKey != null && selectKey(focusedKey)) return;
        List<AppModel.Item> items = AppModel.visibleItems(ctx);
        if (!items.isEmpty()) selectKey(items.get(0).key());
    }

    // ------------------------------------------------------------ SettingsPanel.Host

    public boolean isPanelOpen() { return !panels.isEmpty(); }

    /** The model item under the focus, or null. */
    private AppModel.Item focusedItem() {
        if (focusedKey == null) return null;
        List<AppModel.Item> all = AppModel.allItems();
        for (int i = 0; i < all.size(); i++) {
            if (focusedKey.equals(all.get(i).key())) return all.get(i);
        }
        return null;
    }

    /**
     * The restore tile is always fixed. Settings and TV Health can be hidden like any other
     * tile: the side rail always has Settings and System settings, and TV Health is on the
     * Settings page.
     */
    private boolean fixedTile(AppModel.Item t) {
        return t.type == AppModel.TYPE_RESTORE;
    }

    /** An app the user installed, which the system lets them remove. */
    private boolean removable(AppModel.Item t) {
        if (t.type != AppModel.TYPE_APP || t.pkg == null || t.pkg.equals(ctx.getPackageName())) {
            return false;
        }
        try {
            int f = ctx.getPackageManager().getApplicationInfo(t.pkg, 0).flags;
            return (f & (android.content.pm.ApplicationInfo.FLAG_SYSTEM
                    | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * The Menu button (or a long press) on a tile: a small menu for what to do with it, hide
     * it from the home screen or uninstall the app, instead of hiding it at once.
     * @return true if there was a menu to show.
     */
    public boolean showItemMenu() {
        final AppModel.Item target = focusedItem();
        if (target == null || fixedTile(target) || !panels.isEmpty()) return false;
        final Panel p = new Panel(ctx, target.label, null, Panel.SHEET);
        p.addRow(ctx.getString(R.string.hg_hide), "", new Runnable() {
            public void run() {
                closePanel();
                hideItem(target);
            }
        }).setHelp(ctx.getString(R.string.hg_hide_help));
        if (target.type == AppModel.TYPE_APP && target.pkg != null) {
            p.addRow(ctx.getString(R.string.hg_app_info), "", new Runnable() {
                public void run() {
                    closePanel();
                    logOpen("App info " + target.label);
                    Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                            Uri.parse("package:" + target.pkg));
                    if (!SetupPanel.start(ctx, i)) {
                        Toast.makeText(ctx, ctx.getString(R.string.hg_no_info, target.label),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }).setHelp(ctx.getString(R.string.hg_info_help, target.label));
        }
        if (removable(target)) {
            p.addRow(ctx.getString(R.string.hg_uninstall), "", new Runnable() {
                public void run() {
                    closePanel();
                    uninstall(target);
                }
            }).setHelp(ctx.getString(R.string.hg_uninstall_help, target.label));
        }
        p.addRow(ctx.getString(R.string.ui_cancel), "", new Runnable() {
            public void run() { closePanel(); }
        });
        showPanel(p.root);
        return true;
    }

    private void uninstall(AppModel.Item t) {
        logOpen("Uninstall " + t.label);
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + t.pkg));
        if (!SetupPanel.start(ctx, i)) {
            Toast.makeText(ctx, ctx.getString(R.string.hg_no_uninstall, t.label), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Hide whatever tile currently has focus, without asking.
     * @return true if something was hidden.
     */
    public boolean hideFocused() {
        AppModel.Item target = focusedItem();
        if (target == null || fixedTile(target)) return false;
        hideItem(target);
        return true;
    }

    private void hideItem(AppModel.Item target) {
        Prefs.setHidden(ctx, target.key(), true);
        try {
            Toast.makeText(ctx, ctx.getString(R.string.hg_hidden_toast, target.label),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { }
        layoutGen++;
        rebuild();
    }

    /** Open one of this launcher's own pages, as asked by a TV remote button. */
    public void openPage(String what) {
        if (KeyBindings.CHANNELS.equals(what)) {
            showPanel(ChannelsPanel.build(ctx, panelHost));
        } else if (KeyBindings.SETTINGS.equals(what)) {
            showPanel(SettingsPanel.build(ctx, panelHost));
        }
    }

    public void showPanel(final View v) {
        // Stop the grid, the pill row above it, and any panel underneath, from stealing
        // D-pad focus - otherwise Up from a page's first row can escape to a pill hidden
        // behind the dimmed background, which looks like focus has vanished.
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        pillRow.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rail.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        View under = panels.isEmpty() ? null : panels.get(panels.size() - 1);
        // Taken first: blocking and hiding the page below can clear its focus.
        if (under != null) panelFocus.set(panelFocus.size() - 1, under.findFocus());
        if (under instanceof ViewGroup) {
            ((ViewGroup) under).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        // Only the top page is drawn, so a sheet over a sheet does not dim the grid twice.
        if (under != null) under.setVisibility(View.INVISIBLE);
        root.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panels.add(v);
        panelFocus.add(null);
        v.post(new Runnable() { public void run() { v.requestFocus(); } });
    }

    public void closePanel() {
        if (panels.isEmpty()) return;
        View top = panels.remove(panels.size() - 1);
        panelFocus.remove(panelFocus.size() - 1);
        root.removeView(top);
        if (panels.isEmpty()) {
            scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            pillRow.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            rail.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            // Back to the rail item that opened the page, else the grid as before.
            View origin = railOrigin;
            railOrigin = null;
            if (origin != null && origin.isAttachedToWindow()) origin.requestFocus();
            else if (origin != null) focusRail();   // the rail was rebuilt under the page
            else takeFocus();
            // The restore tile was under the page; its time on screen starts now.
            if (restoreSlot != null) {
                if (AppModel.restorePopupLive()) armRestore();
                else dropRestore();
            }
        } else {
            final View below = panels.get(panels.size() - 1);
            below.setVisibility(View.VISIBLE);
            if (below instanceof ViewGroup) {
                ((ViewGroup) below).setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            }
            // Back on the row that opened the closed page, if it is still there.
            final View was = panelFocus.get(panelFocus.size() - 1);
            below.post(new Runnable() {
                public void run() {
                    if (was == null || !was.isAttachedToWindow() || !was.requestFocus()) {
                        below.requestFocus();
                    }
                }
            });
        }
    }

    /**
     * Back to the plain grid. A Home press lands here, not on a page left open earlier.
     * The first-run wizard stays, since losing it halfway through setup is worse.
     */
    public void closePanels() {
        while (!panels.isEmpty()) {
            if (!Prefs.onboarded(ctx)
                    && panels.get(panels.size() - 1).getTag() instanceof BackHandler) {
                return;
            }
            closePanel();
        }
    }

    /**
     * Take over the panels open in another grid, as they are. The overlay hands them to the
     * activity just before it lifts, so a page opened in the first seconds after Home stays
     * open on the same row. Skipped if a panel is already open here.
     * @return true if panels were moved.
     */
    public boolean adoptPanels(HomeGrid from) {
        if (from == this || from.panels.isEmpty() || !panels.isEmpty()) return false;
        // Settings changed on those pages since this grid last caught up.
        syncOrder();
        applyBackground();
        updateClock();
        View top = from.panels.get(from.panels.size() - 1);
        // Taken first: leaving its window clears the focus.
        View focus = top.findFocus();
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (int i = 0; i < from.panels.size(); i++) {
            View v = from.panels.get(i);
            from.root.removeView(v);
            root.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            panels.add(v);
            panelFocus.add(from.panelFocus.get(i));
        }
        from.panels.clear();
        from.panelFocus.clear();
        from.scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        from.panelHost.grid = this;
        from.panelHost = new PanelHost(from);
        if (focus == null || !focus.requestFocus()) top.requestFocus();
        return true;
    }

    /** Passes a panel's calls on to the grid it is in now; see panelHost. */
    private static final class PanelHost implements SettingsPanel.Host {
        HomeGrid grid;
        PanelHost(HomeGrid g) { grid = g; }
        public void rebuildGrid() { grid.rebuildGrid(); }
        public void showPanel(View v) { grid.showPanel(v); }
        public void closePanel() { grid.closePanel(); }
    }

    /** A setting changed from a panel in this grid; the other grid catches up via syncOrder(). */
    public void rebuildGrid() {
        layoutGen++;
        AppModel.invalidateMetrics();
        applyBackground();
        rebuild();
    }

    /** Carried across the overlay -> activity handoff so the highlight does not jump. */
    public String getSelectedKey() { return focusedKey; }

    /** Also carried across the handoff; restoring focus alone does not restore scroll. */
    public int getScrollY() { return scroll.getScrollY(); }

    /** Horizontal offset of each scrolling row (the dock, if present). */
    public int[] getRowScrollX() {
        int[] out = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) out[i] = rows.get(i).getScrollX();
        return out;
    }

    /**
     * Apply scroll offsets after layout: scrollTo() before layout is clamped to 0. The
     * second post covers a late re-layout caused by restoring focus.
     */
    public void applyScroll(final int y, final int[] rowX) {
        if (y <= 0 && rowX == null) return;
        Runnable apply = new Runnable() {
            public void run() {
                if (y > 0) scroll.scrollTo(0, y);
                if (rowX != null) {
                    for (int i = 0; i < rows.size() && i < rowX.length; i++) {
                        if (rowX[i] > 0) rows.get(i).scrollTo(rowX[i], 0);
                    }
                }
            }
        };
        final Runnable again = apply;
        scroll.post(new Runnable() {
            public void run() { again.run(); scroll.post(again); }
        });
    }

    /** Re-select by identity. */
    public boolean selectKey(String key) {
        if (key == null) return false;
        View v = cellsByKey.get(key);
        if (v == null) return false;
        v.requestFocus();
        return true;
    }

    /** Re-sort; rebuilds only if the order changed. @return true if rebuilt. */
    public boolean resort() {
        String keep = focusedKey;
        int y = scroll.getScrollY();
        int[] rx = getRowScrollX();
        if (!AppModel.resort(ctx)) return false;
        build();
        if (panels.isEmpty() && keep != null) selectKey(keep);
        applyScroll(y, rx);
        return true;
    }

    /**
     * The overlay and the activity each have a grid over one shared model, so a re-sort or
     * a settings change made in one leaves the other stale. Rebuild if that happened.
     * @return true if rebuilt.
     */
    public boolean syncOrder() {
        if (AppModel.order().equals(builtOrder) && builtGen == layoutGen) return false;
        android.util.Log.i("Homestead", "grid out of date, rebuilding");
        rebuild();
        return true;
    }

    /** Rebuild the views over the current model; nothing is re-decoded. */
    public void rebuild() {
        String keep = focusedKey;
        int y = scroll.getScrollY();
        int[] rx = getRowScrollX();
        View focus = root.findFocus();
        boolean onPill = focus != null && (focus == inputsPill
                || shortcutPills.contains(focus));
        boolean onRail = railHasFocus();
        build();
        // Leave focus alone while a panel is open, or the grid would take it from the panel.
        // And on a pill or the rail: a reload of the model must not pull it back to a tile.
        if (panels.isEmpty() && onRail) {
            // The rail is not rebuilt, so its focus is still where it was.
        } else if (panels.isEmpty() && onPill) {
            if (!focus.isAttachedToWindow()) {
                View anchor = topAnchor();
                if (anchor != null) anchor.requestFocus(); else takeFocus();
            }
        } else if (panels.isEmpty()) {
            if (keep == null || !selectKey(keep)) takeFocus();
        }
        applyScroll(y, rx);
        // A restore tile that appears while someone is browsing does not take the focus:
        // a Select meant for the tile under the remote would land on it. It is at the top,
        // and a fresh Home press selects it (takeStartFocus()).
    }

    // ---------------------------------------------------------------- layout

    /**
     * Layout reads metadata from other packages, so one malformed package must not leave a
     * blank home screen. On any failure, draw a minimal row with Settings and TV Health.
     */
    private void build() {
        builtOrder = AppModel.order();
        builtGen = layoutGen;
        updateClock();
        try {
            rebuildShortcutPills();
            buildSections();
        } catch (Throwable t) {
            android.util.Log.e("Homestead", "grid build failed, drawing fallback", t);
            try { buildFallback(); } catch (Throwable ignored) { }
        }
    }

    /** Enough to get to Settings, using nothing that can throw. */
    private void buildFallback() {
        column.removeAllViews();
        cellsByKey.clear();
        cardCells.clear();
        corners.clear();
        rows.clear();
        dockKeys.clear();
        dockTitleView = null;
        topRowWired = false;
        focusedCell = null;
        restoreSlot = null;

        List<AppModel.Item> safe = new ArrayList<AppModel.Item>();
        List<AppModel.Item> all = AppModel.allItems();
        for (int i = 0; i < all.size(); i++) {
            int t = all.get(i).type;
            if (t == AppModel.TYPE_SETTINGS || t == AppModel.TYPE_HEALTH
                    || t == AppModel.TYPE_AMAZON_HOME) {
                safe.add(all.get(i));
            }
        }
        if (!safe.isEmpty()) addTileGrid(safe);
    }

    private void buildSections() {
        column.removeAllViews();
        cellsByKey.clear();
        cardCells.clear();
        corners.clear();
        rows.clear();
        dockKeys.clear();
        dockTitleView = null;
        topRowWired = false;
        focusedCell = null;
        restoreSlot = null;

        List<AppModel.Item> inputs = new ArrayList<AppModel.Item>();
        List<AppModel.Item> nonTv = new ArrayList<AppModel.Item>();
        List<AppModel.Item> tv = new ArrayList<AppModel.Item>();

        // Live TV first takes the Live TV tile out of its section and puts it first in the
        // dock, wider than the rest, instead of the old full-width tile above everything.
        boolean tvFirst = liveTvFirst();
        AppModel.Item liveTv = null;
        AppModel.Item restore = null;

        List<AppModel.Item> all = AppModel.visibleItems(ctx);
        restoreShown = false;
        for (int i = 0; i < all.size(); i++) {
            AppModel.Item it = all.get(i);
            if (it.type == AppModel.TYPE_RESTORE) {
                restoreShown = true;
                restore = it;
            } else if (tvFirst && AppModel.isLive(it.type) && liveTv == null) {
                liveTv = it;
            } else if (it.type == AppModel.TYPE_INPUT || AppModel.isLive(it.type)
                    || it.type == AppModel.TYPE_AMAZON_HOME
                    || it.type == AppModel.TYPE_SETTINGS || it.type == AppModel.TYPE_HEALTH) {
                inputs.add(it);
            } else if (it.tvApp && it.banner != null) {
                tv.add(it);
            } else {
                nonTv.add(it);
            }
        }

        // Same gating "Show Inputs & System" / "Show Non-TV Applications" always had -
        // there is just no labelled band left to turn off along with the tiles.
        if (!Prefs.showInputs(ctx)) {
            List<AppModel.Item> keep = new ArrayList<AppModel.Item>();
            for (int i = 0; i < inputs.size(); i++) {
                int t = inputs.get(i).type;
                if (t == AppModel.TYPE_SETTINGS || t == AppModel.TYPE_HEALTH) keep.add(inputs.get(i));
            }
            inputs = keep;
        }
        if (!Prefs.showNonTv(ctx)) nonTv.clear();

        // Dock: Live TV first (if on), then the top few most-used TV apps. AppModel.resort()
        // already put the most relevant ones first, so the dock is just "take the front".
        int dockSlots = Math.max(0, DOCK_COUNT - (liveTv != null ? 1 : 0));
        int dockAppCount = Math.min(dockSlots, tv.size());
        List<AppModel.Item> dockApps = tv.subList(0, dockAppCount);
        List<AppModel.Item> tvGrid = tv.subList(dockAppCount, tv.size());

        // The restore tile comes first, above the dock: it only exists while something has
        // to be put back, and slides out by itself.
        if (restore != null) addRestoreTile(restore);
        if (liveTv != null || !dockApps.isEmpty()) {
            addDock(liveTv, dockApps);
        }

        List<AppModel.Item> gridItems = new ArrayList<AppModel.Item>();
        gridItems.addAll(inputs);
        gridItems.addAll(tvGrid);
        gridItems.addAll(nonTv);
        if (!gridItems.isEmpty()) addTileGrid(gridItems);
    }

    /** The system undid something Debloat had switched off: one amber tile above everything. */
    private void addRestoreTile(AppModel.Item it) {
        FrameLayout cell = newCell(it);
        // Like the dock tiles: focusing it scrolls the page back to the top.
        dockKeys.add(it.key());
        stopAt(cell, true, true);
        GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { 0xFF9A6A12, 0xFF4A3208 });
        card.setCornerRadius(dp(8));
        cell.setBackground(card);

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(24), dp(12), dp(24), dp(12));

        TextView title = new TextView(ctx);
        title.setText(it.label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Fonts.get(ctx, Fonts.MEDIUM));
        text.addView(title);

        List<String> pkgs = AppModel.reverted(ctx);
        TextView hint = new TextView(ctx);
        hint.setText(ctx.getString(pkgs.size() == 1 ? R.string.home_restore_hint_one
                : R.string.home_restore_hint_many, revertedNames(ctx, pkgs)));
        hint.setTextColor(0xE6FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setMaxLines(2);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(hint);

        // Two lines of hint do not fit a banner cell's height at every density, so the tile
        // grows to its text and is never shorter than a cell.
        cell.setMinimumHeight(AppModel.cellH(ctx));
        cell.addView(text, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));

        // The clock starts when a window really shows the tile, scrolled into view: the
        // activity's window can be built while it is in the background, and the page can
        // be scrolled down when the tile appears.
        final android.view.ViewTreeObserver.OnScrollChangedListener inView =
                new android.view.ViewTreeObserver.OnScrollChangedListener() {
                    public void onScrollChanged() {
                        if (!AppModel.restoreClockRunning()) armRestore();
                    }
                };
        final FrameLayout slot = new FrameLayout(ctx) {
            @Override protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                // Back on screen after its time ran out elsewhere: gone before it is drawn.
                if (!AppModel.restorePopupLive()) { dropRestore(); return; }
                getViewTreeObserver().addOnScrollChangedListener(inView);
                post(new Runnable() { public void run() { armRestore(); } });
            }
            @Override protected void onWindowVisibilityChanged(int v) {
                super.onWindowVisibilityChanged(v);
                if (v != View.VISIBLE) removeCallbacks(restoreTimeUp);
                else if (!AppModel.restorePopupLive()) dropRestore();
                else armRestore();
            }
            @Override protected void onDetachedFromWindow() {
                removeCallbacks(restoreTimeUp);
                getViewTreeObserver().removeOnScrollChangedListener(inView);
                super.onDetachedFromWindow();
            }
        };
        slot.setClipChildren(false);
        slot.addView(cell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        restoreSlot = slot;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                AppModel.cellW(ctx) * 3 + dp(GAP_DP) * 2, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(GAP_DP);
        column.addView(slot, lp);
    }

    /** The tile is on screen here: start the shared clock if this is the first sight, and
     *  slide out when it runs out, at the same moment as the other grid. A page open over
     *  the grid hides it, so the clock waits for that too (closePanel() arms it again). */
    private void armRestore() {
        FrameLayout slot = restoreSlot;
        if (slot == null || slot.getParent() == null) return;
        if (slot.getWindowVisibility() != View.VISIBLE || !slot.isShown()) return;
        if (!panels.isEmpty() && !AppModel.restoreClockRunning()) return;
        // Not counted until most of it is on screen.
        android.graphics.Rect seen = new android.graphics.Rect();
        if (!AppModel.restoreClockRunning()
                && (!slot.getGlobalVisibleRect(seen) || seen.height() < slot.getHeight() / 2)) {
            return;
        }
        long left = AppModel.startRestorePopup();
        android.util.Log.i("Homestead", "restore tile on screen ("
                + ctx.getClass().getSimpleName() + "), " + left + " ms left");
        slot.removeCallbacks(restoreTimeUp);
        slot.postDelayed(restoreTimeUp, Math.max(0L, left));
    }

    /** Its time ran out while this grid was not showing it: take it out with no animation. */
    private void dropRestore() {
        FrameLayout slot = restoreSlot;
        if (slot == null) return;
        restoreSlot = null;
        cellsByKey.remove(AppModel.RESTORE_KEY);
        slot.setVisibility(View.GONE);
        root.post(new Runnable() {
            public void run() { rebuild(); }
        });
    }

    /** Time is up: the tile slides out to the left, then the rows below close the gap. */
    private void slideOutRestore() {
        final FrameLayout slot = restoreSlot;
        if (slot == null || slot.getParent() == null || slot.getChildCount() == 0) return;
        restoreSlot = null;
        android.util.Log.i("Homestead", "restore tile time up, sliding out ("
                + ctx.getClass().getSimpleName() + ")");
        final View tile = slot.getChildAt(0);
        // Out of reach from here on, so Select cannot land on a tile that is leaving.
        cellsByKey.remove(AppModel.RESTORE_KEY);
        boolean had = tile.hasFocus();
        tile.setFocusable(false);
        if (had && panels.isEmpty()) takeStartFocus();
        tile.animate().translationX(-(slot.getWidth() + dp(MARGIN_DP))).alpha(0f)
                .setDuration(350L)
                .withEndAction(new Runnable() {
                    public void run() { collapseRestore(slot); }
                });
    }

    private void collapseRestore(final FrameLayout slot) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) slot.getLayoutParams();
        final int h = slot.getHeight();
        final int m = lp.bottomMargin;
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(250L);
        a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator va) {
                float f = (Float) va.getAnimatedValue();
                lp.height = Math.round(h * f);
                lp.bottomMargin = Math.round(m * f);
                slot.setLayoutParams(lp);
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) {
                // The model no longer offers the tile, so this drops the empty slot.
                if (slot.getParent() != column) return;
                // Seen at the top of the page, it leaves the page at the top: the rows that
                // moved up would otherwise sit under the edge with their header cut off.
                boolean atTop = scroll.getScrollY() < h;
                rebuild();
                if (atTop && panels.isEmpty()) keepAtTop();
            }
        });
        a.start();
    }

    /** The reverted packages by name, short enough for the tile: "A", "A and B",
     *  "A, B and C", or "A, B and 4 more". */
    private static String revertedNames(Context c, List<String> pkgs) {
        int n = pkgs.size();
        if (n == 0) return "";
        String a = Debloat.titleOf(c, pkgs.get(0));
        if (n == 1) return a;
        String b = Debloat.titleOf(c, pkgs.get(1));
        if (n == 2) return c.getString(R.string.hg_and2, a, b);
        if (n == 3) return c.getString(R.string.hg_and3, a, b, Debloat.titleOf(c, pkgs.get(2)));
        return c.getString(R.string.hg_and_more, a, b, n - 2);
    }

    /** Switch off again whatever the system switched back on; open Setup if there is no link. */
    private void restoreReverted() {
        List<String> pkgs = AppModel.reverted(ctx);
        if (pkgs.isEmpty()) return;
        if (!AdbBridge.canRun(ctx)) {
            Toast.makeText(ctx, R.string.home_restore_failed, Toast.LENGTH_LONG).show();
            showPanel(DebloatPanel.build(ctx, panelHost));
            return;
        }
        final String[] list = pkgs.toArray(new String[pkgs.size()]);
        String[] cmds = new String[list.length];
        for (int i = 0; i < list.length; i++) cmds[i] = AdbBridge.disableCommand(list[i]);
        Toast.makeText(ctx, R.string.home_connecting, Toast.LENGTH_SHORT).show();
        AdbBridge.runRetry(cmds, new AdbBridge.Callback() {
            public void onDone(boolean ok, String out) {
                // Never started: nothing changed, so keep the tile and the record of what
                // is still on, and say so. Forgetting them here would hide the problem.
                if (!AdbBridge.ran(out)) {
                    Toast.makeText(ctx, R.string.home_restore_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                StringBuilder kept = new StringBuilder();
                for (int i = 0; i < list.length; i++) {
                    boolean off = Debloat.stateOf(ctx, list[i]) == Debloat.DISABLED;
                    Prefs.noteDisabled(ctx, list[i], off);
                    if (!off) {
                        if (kept.length() > 0) kept.append(", ");
                        kept.append(Debloat.titleOf(ctx, list[i]));
                    }
                }
                // Fire OS refuses some packages; the record is dropped so the tile does not
                // come back for good, but the user is told.
                if (kept.length() > 0) {
                    Toast.makeText(ctx, ctx.getString(R.string.home_restore_refused, kept),
                            Toast.LENGTH_LONG).show();
                }
                AppModel.reload(ctx);
                rebuild();
            }
        });
    }

    /**
     * A scrolling row of cards. HorizontalScrollView moves the focus for Left and Right itself
     * and reports the key handled, so the system's navigation click, which every other D-pad
     * move makes, never plays; the row plays it when the focus actually moved.
     */
    private static final class CardRow extends HorizontalScrollView {
        CardRow(Context c) { super(c); }

        @Override public boolean arrowScroll(int direction) {
            View before = findFocus();
            boolean handled = super.arrowScroll(direction);
            View after = findFocus();
            if (handled && after != null && after != before && after != this) {
                playSoundEffect(android.view.SoundEffectConstants
                        .getContantForFocusDirection(direction));
            }
            return handled;
        }
    }

    /** The translucent dock band: Live TV (if first) wider, then the most-used TV apps. */
    /**
     * Called for every cell in whichever row is the first one built - every dock cell
     * (the dock, when present, is always row one), or the grid's first row when there is
     * no dock (see the dockKeys.isEmpty() guard at its one call site below). Points the
     * cell's UP back at the Settings pill, and - the first time this runs per build - both
     * pills' DOWN back at that cell; LEFT/RIGHT between the two pills needs no wiring, since
     * they are plain siblings in the same row, not across a ScrollView boundary. See
     * topRowWired.
     */
    private void wireTopRow(View cell) {
        if (cell.getId() == View.NO_ID) cell.setId(View.generateViewId());
        View anchor = topAnchor();
        if (anchor != null) cell.setNextFocusUpId(anchor.getId());
        if (!topRowWired) {
            topRowWired = true;
            inputsPill.setNextFocusDownId(cell.getId());
            for (int i = 0; i < shortcutPills.size(); i++) {
                shortcutPills.get(i).setNextFocusDownId(cell.getId());
            }
            // Right from the rail lands on the first tile of the first row.
            for (int i = 0; i < railItems.size(); i++) {
                railItems.get(i).setNextFocusRightId(cell.getId());
            }
        }
    }

    /**
     * The pill Up from the first row reaches: Inputs when it is there (the rightmost pill),
     * else the last quick setting, else none - with the quick settings off and no inputs,
     * the top bar is empty and Up has nowhere to go.
     */
    private View topAnchor() {
        if (pillRow.getVisibility() != View.VISIBLE) return null;
        if (inputsPill.getVisibility() == View.VISIBLE) return inputsPill;
        if (!shortcutPills.isEmpty()) return shortcutPills.get(shortcutPills.size() - 1);
        return null;
    }

    private void addDock(AppModel.Item liveTv, List<AppModel.Item> dockApps) {
        TextView title = new TextView(ctx);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Fonts.get(ctx, Fonts.MEDIUM));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        paintText(title);
        title.setText(liveTv != null ? liveTv.label
                : dockApps.isEmpty() ? "" : dockApps.get(0).label);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(10);

        // The dock starts halfway down the screen, wallpaper open above it, the way the
        // reference dock sits: a spacer fills the rest of the first half. The grid follows
        // right under it, and keepAtTop() brings the dock back.
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int spacer = screenH / 2 - dp(TOP_RESERVE_DP);
        // The restore tile sits above the spacer while it is up: take its room from the spacer,
        // so the dock stays at the bottom of the screen instead of being pushed down by it.
        if (restoreSlot != null) {
            restoreSlot.measure(View.MeasureSpec.makeMeasureSpec(
                    AppModel.cellW(ctx) * 3 + dp(GAP_DP) * 2, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            spacer -= restoreSlot.getMeasuredHeight() + dp(GAP_DP);
        }
        if (spacer > 0) column.addView(new View(ctx), new LinearLayout.LayoutParams(1, spacer));

        column.addView(title, tlp);
        dockTitleView = title;

        HorizontalScrollView hs = new CardRow(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        rows.add(hs);

        int h = dp(DOCK_H_DP[Prefs.dockSize(ctx)]);
        // 3:2, narrower than the 16:9 banners: they are centre-cropped to fit (see buildTile).
        int baseW = h * 3 / 2;
        int featuredW = baseW + baseW / 2;
        // The whole tiles that fit between Canali TV and the right edge, widened a touch so
        // they fill that space exactly: the last one ends on the grid's right edge and the
        // next starts past it, instead of showing a sliver of itself.
        int gap = dp(GAP_DP);
        int avail = ctx.getResources().getDisplayMetrics().widthPixels
                - dp(AppModel.RAIL_DP + AppModel.RAIL_GAP_DP) - dp(MARGIN_DP);
        int rest = avail;
        if (liveTv != null) rest -= featuredW + gap + Math.max(1, dp(1)) + gap;
        int fit = Math.max(1, (rest + gap) / (baseW + gap));
        int normalW = Math.max(baseW, (rest - gap * (fit - 1)) / fit);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        // The band's inset lives here, inside the scrolling row, so a focused tile scaled to
        // FOCUS_SCALE has room inside the scroll view's clip - and clipToPadding is off,
        // since clipChildren(false) alone still clips to the padding, which cut the focused
        // tile (and its focus rim) off at its unscaled edges. The sides grow with the widest
        // tile, so an end tile's zoom still fits at the larger dock sizes.
        int padV = dp(DOCK_PAD_DP);
        // The first tile sits exactly on the grid's left edge; a focused one grows into the
        // band's room on that side (the gap beside the rail) rather than being pushed in.
        int padH = 0;
        // The band reaches left under the gap beside the rail (see blp below), so a tile
        // scrolled out of view is cut where the rail begins; this padding puts the first
        // tile back on the grid's own left edge.
        // On the right, room for the last tile's growth, or its focus rim is cut off there.
        int padEnd = Math.round(normalW * (FOCUS_SCALE - 1f) / 2f) + dp(1);
        row.setPadding(padH + dp(AppModel.RAIL_GAP_DP), padV, padEnd, padV);
        row.setClipToPadding(false);

        // Live TV first: the first tile of the dock, half as wide again as the others.
        if (liveTv != null) {
            View cell = buildCard(liveTv);
            wireTopRow(cell);
            stopAt(cell, true, dockApps.isEmpty());
            row.addView(cell, new LinearLayout.LayoutParams(featuredW, h));
            dockKeys.add(liveTv.key());
            // Canali TV is a page, not an app: a thin vertical rule sets it apart from the
            // apps beside it, the way the VIDAA dock separates its own first entry.
            if (!dockApps.isEmpty()) {
                View rule = new View(ctx);
                rule.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        Math.max(1, dp(1)), (int) (h * 0.72f));
                rlp.gravity = Gravity.CENTER_VERTICAL;
                rlp.leftMargin = dp(GAP_DP);
                row.addView(rule, rlp);
            }
        }
        for (int i = 0; i < dockApps.size(); i++) {
            AppModel.Item it = dockApps.get(i);
            View cell = buildTile(it);
            wireTopRow(cell);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(normalW, h);
            if (i > 0 || liveTv != null) lp.leftMargin = dp(GAP_DP);
            stopAt(cell, i == 0 && liveTv == null, i == dockApps.size() - 1);
            row.addView(cell, lp);
            dockKeys.add(it.key());
        }
        hs.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The band clips the scroll view to its own edges, so tiles that don't fit (at the
        // larger dock sizes) are cut off cleanly inside it instead of running on past its
        // end. The row's padding keeps a focused tile's zoom inside that clip.
        // No fill: the tiles sit straight on the wallpaper, as they do on VIDAA and Tizen.
        FrameLayout band = new FrameLayout(ctx);
        band.addView(hs, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(DOCK_BOTTOM_DP);
        blp.leftMargin = -dp(AppModel.RAIL_GAP_DP);
        column.addView(band, blp);
    }

    /**
     * The single grid below the dock: square icon cards, side by side - a visual step down
     * from the dock's wide 16:9 tiles, the way Tizen and VIDAA both shrink to a denser,
     * squarer shelf under their own hero row.
     */
    private void addTileGrid(List<AppModel.Item> items) {
        // As many as the setting says, in the dock's row width: squares that fill the row edge to
        // edge, a step under the dock's wide tiles, and low enough that the first row clears
        // the bottom of the screen while the dock is at the top (from 9 on, on 1080p).
        int base = AppModel.columns(ctx);
        int gap = dp(GRID_GAP_DP);
        int rowW = AppModel.cellW(ctx) * base + dp(GAP_DP) * (base - 1);
        int cols = base;
        int w = (rowW - gap * (cols - 1)) / cols;
        gridCorner = Math.round(w * SQUARE_CORNER);
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                // Rows sit further apart than tiles do side by side, so the second row stays
                // below the screen's edge while the dock is at the top.
                if (i > 0) rlp.topMargin = dp(GRID_ROW_GAP_DP);
                column.addView(row, rlp);
            }
            AppModel.Item it = items.get(i);
            View cell = buildSquareCard(it);
            // Only when there is no dock above this grid - otherwise UP from here must
            // reach the dock normally, not jump straight past it to the pill.
            if (i < cols && dockKeys.isEmpty()) wireTopRow(cell);
            boolean rowEnd = i % cols == cols - 1 || i == items.size() - 1;
            stopAt(cell, i % cols == 0, rowEnd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, w);
            if (i % cols != 0) lp.leftMargin = dp(GRID_GAP_DP);
            row.addView(cell, lp);
        }
        // Room under the last row, so the tiles never touch the screen's edge.
        if (row != null) {
            ((LinearLayout.LayoutParams) row.getLayoutParams()).bottomMargin = dp(GRID_BOTTOM_DP);
        }
        gridCorner = 0;
    }

    /** The corner radius for a cell being built: the square tiles' while they are, else the usual. */
    private int corner() { return gridCorner > 0 ? gridCorner : dp(CORNER_DP); }

    // ---------------------------------------------------------------- cells

    private FrameLayout newCell(final AppModel.Item it) {
        final FrameLayout cell = new FrameLayout(ctx);
        cell.setFocusable(true);
        cell.setFocusableInTouchMode(true);
        cell.setClipChildren(false);
        // A rounded-rect outline so a focused tile's elevation shadow is shaped like the
        // card, not the square it would otherwise fall back to.
        final int radius = corner();
        if (gridCorner > 0) corners.put(cell, radius);
        cell.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                // A glass card over a photo is see-through, and its lift shadow would show
                // through it: no shadow for those.
                if (look == Backgrounds.PHOTO_LOOK && cardCells.containsKey(v)) outline.setAlpha(0f);
            }
        });
        cell.setClipToOutline(true);
        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (has) {
                    focusedCell = v;
                    focusedKey = it.key();
                    style(v, true);
                    if (dockKeys.contains(it.key())) {
                        keepAtTop();
                        revealInDock(v);
                        if (dockTitleView != null) dockTitleView.setText(it.label);
                    } else if (dockTitleView != null) {
                        // The icon tiles carry no name: the line above the dock names the one
                        // in focus, whichever row it is in.
                        dockTitleView.setText(it.label);
                    }
                } else {
                    style(v, false, true);
                }
            }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // TV Health opens a panel in this window, so it is not reported as a
                // pick; that would make an overlay host dismiss itself.
                boolean leaves = it.type != AppModel.TYPE_HEALTH && it.type != AppModel.TYPE_CHANNELS;
                if (listener != null && leaves) listener.onPick(it);
                act(it);
            }
        });
        cell.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                return showItemMenu();
            }
        });
        cellsByKey.put(it.key(), cell);
        style(cell, false, false);
        return cell;
    }

    /** The preset's edge around an unfocused cell, or null when the preset has none. */
    private Drawable outline() {
        if (look == null || look.outline == 0) return null;
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(CORNER_DP));
        d.setStroke(dp(1), look.outline);
        d.setColor(Color.TRANSPARENT);
        return d;
    }

    /**
     * The selection cursor: a bright rounded rim over the focused cell. A crisp near-white
     * inner edge reads cleanly on both light banners and a photo background, the Apple-TV
     * look. Drawn as the cell's foreground so it sits above the banner, with the same
     * corner radius as the tile so it never shows sharp corners against rounded art.
     */
    private Drawable focusRim(View cell) {
        GradientDrawable d = new GradientDrawable();
        Integer r = corners.get(cell);
        d.setCornerRadius(r != null ? r : dp(CORNER_DP));
        d.setStroke(dp(3), 0xFFFFFFFF);
        d.setColor(Color.TRANSPARENT);
        return d;
    }

    private void style(View v, boolean focused) { style(v, focused, true); }

    /**
     * Focus treatment, animated for a premium feel: the cell springs to {@link #FOCUS_SCALE}
     * with a gentle overshoot, lifts on the z-axis so it casts a rounded shadow (newCell()
     * gives every cell a rounded outline), and gains the bright selection rim. The rim is
     * set immediately, so the right edge shows even if the overlay -> activity handoff cuts
     * the animation short. animate is false for a cell's first style (nothing was focused a
     * moment ago to animate from) and for the repaint applyBackground() does across every
     * cell at once, where an instant re-skin reads as a palette swap, not a focus move.
     */
    private void style(View v, boolean focused, boolean animate) {
        if (!(v instanceof FrameLayout)) return;
        FrameLayout f = (FrameLayout) v;
        f.animate().cancel();
        // The white rim marks the focused tile; the others have no border at all.
        f.setForeground(focused ? focusRim(f) : null);
        float scale = focused ? FOCUS_SCALE : 1f;
        float z = focused ? dp(FOCUS_LIFT_DP) : 0f;
        float alpha = focused ? 1f : UNFOCUSED_ALPHA;
        // A very light tile (Netflix's white banner) is toned down until it has focus.
        if (f.getTag() == BRIGHT_DIM && f.getChildCount() > 0
                && f.getChildAt(0) instanceof ImageView) {
            ((ImageView) f.getChildAt(0)).setColorFilter(focused ? null : BRIGHT_DIM);
        }
        if (!animate || !f.isAttachedToWindow() || f.getWidth() == 0) {
            f.setScaleX(scale);
            f.setScaleY(scale);
            f.setTranslationZ(z);
            f.setAlpha(alpha);
            return;
        }
        f.animate().scaleX(scale).scaleY(scale).translationZ(z).alpha(alpha)
                .setDuration(focused ? FOCUS_IN_MS : FOCUS_OUT_MS)
                .setInterpolator(focused ? FOCUS_IN_INTERP : FOCUS_OUT_INTERP)
                .withLayer()
                .start();
    }

    /**
     * Keep focus on the first or last cell of a row when the D-pad points past it. Otherwise
     * the focus search picks the nearest cell that way anywhere on the page, often in another
     * row (a scrolled card row has cards off the left edge), which reads as a jump.
     */
    private void stopAt(View cell, final boolean first, final boolean last) {
        if (!first && !last) return;
        cell.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, android.view.KeyEvent e) {
                // Left past the first cell of a row goes to the side rail, not off the page.
                if (first && code == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) focusRail();
                    return true;
                }
                return last && code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
            }
        });
    }

    /**
     * A TV app: full-bleed banner, already rounded in the bitmap - which means its four
     * corners are genuinely transparent pixels (AppModel clips banners to a rounded path on
     * decode), and some apps' own banner art carries large transparent regions besides
     * (a logo on nothing, rather than a full-bleed image). Without an opaque plate under it,
     * either one lets whatever is behind the tile - the dock band, the photo wallpaper -
     * show through unevenly next to tiles whose own art happens to be fully opaque, which
     * reads as a rendering glitch rather than a deliberate look.
     */
    private View buildTile(AppModel.Item it) {
        FrameLayout cell = newCell(it);
        GradientDrawable plate = new GradientDrawable();
        plate.setCornerRadius(dp(CORNER_DP));
        plate.setColor(CARD_COLOR);
        cell.setBackground(plate);
        ImageView img = new ImageView(ctx);
        // The banners are 16:9 and the dock's tiles are narrower: crop the sides, which hold
        // only margin, rather than stretch.
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImageBitmap(it.banner);
        cell.addView(img, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dimIfBright(cell, img, it.banner);
        return cell;
    }

    /** Multiplies a very light picture down to about 70%. Also the tag marking such a tile. */
    private static final android.graphics.PorterDuffColorFilter BRIGHT_DIM =
            new android.graphics.PorterDuffColorFilter(0xFFB4B4B4,
                    android.graphics.PorterDuff.Mode.MULTIPLY);

    /** Tones down the tile's picture while unfocused if it is mostly near-white. */
    private void dimIfBright(FrameLayout cell, ImageView iv, Bitmap b) {
        if (b == null || !isBright(b)) return;
        cell.setTag(BRIGHT_DIM);
        iv.setColorFilter(BRIGHT_DIM);
    }

    /** True when the picture's opaque pixels average above about 70% brightness. */
    private static boolean isBright(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        if (w < 4 || h < 4) return false;
        long sum = 0;
        int n = 0;
        for (int yi = 0; yi < 8; yi++) {
            for (int xi = 0; xi < 12; xi++) {
                int p = b.getPixel((xi * 2 + 1) * w / 24, (yi * 2 + 1) * h / 16);
                if ((p >>> 24) < 0x80) continue;
                sum += (299 * ((p >> 16) & 0xFF) + 587 * ((p >> 8) & 0xFF)
                        + 114 * (p & 0xFF)) / 1000;
                n++;
            }
        }
        return n >= 20 && sum / n > 180;
    }

    /**
     * A square cell for the grid below the dock: just the app's icon, large, centered, no
     * label - a banner would stretch badly into a square shape, and a big icon alone is
     * exactly what Tizen and VIDAA show in their own icon shelf. The card behind it is the
     * app's own colour (from its banner, where it has one) darkened to a background, so
     * each tile still reads as that app's rather than a generic grey one; anything without
     * either a colour or a plain icon (an input, Live TV, Settings...) falls back to the
     * normal flat card instead.
     */
    private View buildSquareCard(AppModel.Item it) {
        boolean textTile = it.type == AppModel.TYPE_INPUT || AppModel.isLive(it.type);
        if (textTile || it.icon == null) return buildCard(it);
        // Settings, TV Health, AirPlay...: a small glyph over a label, not an icon filling the
        // tile edge to edge (that is for an app's own icon).
        if (it.type != AppModel.TYPE_APP) return buildGlyphSquare(it);
        FrameLayout cell = newCell(it);
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(corner());
        int iw = it.icon.getWidth(), ih = it.icon.getHeight();
        boolean squareIcon = Math.abs(iw - ih) <= Math.max(2, Math.max(iw, ih) / 20);
        int plate = squareCardColor(it);
        Bitmap shown = it.icon;
        boolean fill = squareIcon;
        if (!squareIcon) {
            // A wide or tall logo (Prime Video's) would be stretched to fill a square. Its
            // colour is extended past its short sides into a square instead, so the logo
            // stays undistorted and the tile still reads as one piece.
            Bitmap sq = squareFrom(it.icon);
            if (sq != null) {
                shown = sq;
                fill = true;
            } else {
                int edge = it.icon.getPixel(1, ih / 2);
                if ((edge >>> 24) >= 0x80) plate = edge | 0xFF000000;
            }
        }
        card.setColor(plate);
        cell.setBackground(card);
        cardCells.put(cell, Boolean.FALSE);
        ImageView ic = new ImageView(ctx);
        // A square icon is the tile: edge to edge, with no margin around it.
        ic.setScaleType(fill ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
        ic.setImageBitmap(shown);
        cell.addView(ic, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dimIfBright(cell, ic, shown);
        return cell;
    }

    /** A square cell for a tile that is not an app: a glyph with its name under it. */
    private View buildGlyphSquare(AppModel.Item it) {
        FrameLayout cell = newCell(it);
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(corner());
        card.setColor(cardColor(false));
        cell.setBackground(card);
        cardCells.put(cell, Boolean.FALSE);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(6), dp(6), dp(6), dp(6));
        ImageView ic = new ImageView(ctx);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ic.setImageBitmap(it.icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.bottomMargin = dp(6);
        col.addView(ic, ilp);
        TextView tv = new TextView(ctx);
        tv.setText(it.label);
        tv.setTextColor(0xFFD6D8DE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(tv);
        cell.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return cell;
    }

    /**
     * A wide (or tall) icon placed in a square the size of its long side, with the space
     * above and below filled by a gradient from the icon's own top edge colour to its bottom
     * one - so a logo on a coloured strip becomes a logo on a coloured square, the seam
     * invisible when the strip is a smooth fade. Null when an edge is transparent.
     */
    private static Bitmap squareFrom(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        if (w < 4 || h < 4) return null;
        boolean wide = w > h;
        int a = wide ? src.getPixel(w / 2, 1) : src.getPixel(1, h / 2);
        int b = wide ? src.getPixel(w / 2, h - 2) : src.getPixel(w - 2, h / 2);
        if ((a >>> 24) < 0x80 || (b >>> 24) < 0x80) return null;
        a |= 0xFF000000;
        b |= 0xFF000000;
        int s = Math.max(w, h);
        int shortSide = Math.min(w, h);
        float off = (s - shortSide) / 2f;
        float p0 = off / s, p1 = (off + shortSide) / s;
        try {
            Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(out);
            android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new android.graphics.LinearGradient(
                    0, 0, wide ? 0 : s, wide ? s : 0,
                    new int[]{ a, a, b, b }, new float[]{ 0f, p0, p1, 1f },
                    android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, s, s, paint);
            cv.drawBitmap(src, wide ? 0 : off, wide ? off : 0, null);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Darkened toward the app's own colour rather than the background look's, where one exists. */
    private int squareCardColor(AppModel.Item it) {
        if (look == Backgrounds.PHOTO_LOOK) return cardColor(false);
        int t = it.accent != 0 ? it.accent : look.tint();
        float k = 0.55f;
        int r = Math.round(Color.red(t) * (1 - k) + 0x0A * k);
        int g = Math.round(Color.green(t) * (1 - k) + 0x0E * k);
        int b = Math.round(Color.blue(t) * (1 - k) + 0x14 * k);
        return Color.rgb(r, g, b);
    }

    /** An input, Live TV, Settings, TV Health, Amazon Home, or a non-TV app: one flat card. */
    private View buildCard(AppModel.Item it) {
        FrameLayout cell = newCell(it);
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(corner());
        boolean textTile = it.type == AppModel.TYPE_INPUT || AppModel.isLive(it.type);
        card.setColor(cardColor(textTile));
        cell.setBackground(card);
        cardCells.put(cell, Boolean.valueOf(textTile));

        if (textTile) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            // Live TV and Canali TV share this tile: a small antenna over the name. The tuner's
            // Live TV brings its own glyph; Canali TV takes the standard antenna.
            int glyph = it.glyph != 0 ? it.glyph
                    : it.type == AppModel.TYPE_CHANNELS ? R.drawable.ic_antenna : 0;
            if (glyph != 0) {
                ImageView ic = new ImageView(ctx);
                ic.setImageResource(glyph);
                ic.setColorFilter(0xFFBFE9EE,
                        android.graphics.PorterDuff.Mode.SRC_IN);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
                ilp.bottomMargin = dp(6);
                col.addView(ic, ilp);
            }
            TextView tv = new TextView(ctx);
            tv.setText(it.label);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(tv);
            if (it.connected) {
                TextView tag = new TextView(ctx);
                tag.setText(R.string.home_connected);
                tag.setTextColor(0xFF7BD88F);
                tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tag.setGravity(Gravity.CENTER);
                col.addView(tag);
            }
            cell.addView(col, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return cell;
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));

        ImageView ic = new ImageView(ctx);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (it.icon != null) ic.setImageBitmap(it.icon);
        int isz = dp(34);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(isz, isz);
        ilp.rightMargin = dp(10);
        row.addView(ic, ilp);

        TextView tv = new TextView(ctx);
        tv.setText(it.label);
        tv.setTextColor(0xFFB8B8BD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        row.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cell.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return cell;
    }

    // ---------------------------------------------------------------- launching

    /**
     * Says what opened a panel. Device -1 means the key was injected rather than sent by a
     * remote; HDMI-CEC keys also carry the HDMI source.
     */
    private void logOpen(String what) {
        String how = (lastKeyAt == 0L) ? "no key seen"
                : "key " + android.view.KeyEvent.keyCodeToString(lastKey) + " from device "
                        + lastKeyDevice + ", source 0x" + Integer.toHexString(lastKeySource)
                        + ((lastKeySource & android.view.InputDevice.SOURCE_HDMI)
                                == android.view.InputDevice.SOURCE_HDMI ? " (HDMI-CEC)" : "")
                        + ", " + (android.os.SystemClock.uptimeMillis() - lastKeyAt) + "ms ago";
        android.util.Log.i("Homestead", "opening " + what + " (" + how + ")");
    }

    private void act(AppModel.Item it) {
        if (it.type == AppModel.TYPE_SETTINGS) {
            // With the launcher on, Amazon's own Settings; Homestead's are on the Settings
            // pill, and are this tile's own fallback with the launcher off - tried only
            // then, because off, Amazon still answers the intent, with a do-nothing
            // compliance stub (CTSDummySettingsHandler) the catch below would not catch.
            logOpen(it.label);
            if (!AppModel.amazonLauncherOn(ctx) || !SetupPanel.openTvSettings(ctx)) {
                showPanel(SettingsPanel.build(ctx, panelHost));
            }
            return;
        }
        if (it.type == AppModel.TYPE_HEALTH) {
            logOpen(it.label);
            showPanel(HealthPanel.build(ctx, panelHost));
            return;
        }
        if (it.type == AppModel.TYPE_RESTORE) {
            restoreReverted();
            return;
        }
        if (it.type == AppModel.TYPE_CHANNELS) {
            logOpen(it.label);
            showPanel(ChannelsPanel.build(ctx, panelHost));
            return;
        }
        if (it.type == AppModel.TYPE_LIVETV) {
            logOpen(it.label);
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("livetv://showLiveTVButtonContent/"));
                i.setComponent(new ComponentName(AppModel.LIVETV_PKG, AppModel.LIVETV_SVC));
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
                else ctx.startService(i);
            } catch (Throwable t) {
                Toast.makeText(ctx, R.string.home_livetv_failed, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (it.type == AppModel.TYPE_INPUT) {
            Intent i = new Intent(ctx, HdmiActivity.class);
            i.putExtra(HdmiActivity.EXTRA_INPUT_ID, it.inputId);
            i.putExtra(HdmiActivity.EXTRA_LABEL, it.label);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { ctx.startActivity(i); } catch (Throwable t) { }
            return;
        }
        if (it.type == AppModel.TYPE_AMAZON_HOME) {
            HomeRedirectService.suspend(30000L);
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setClassName(AppModel.AMAZON_LAUNCHER, AppModel.AMAZON_HOME_CLS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Throwable t) { }
            return;
        }
        // Fire OS lets only its own Settings/home open some apps' screens, so a direct launch is
        // refused and the tile did nothing. Open the TV's Settings and point the way with a brief
        // bubble, or (for one with no settings home) just show where to go.
        if (AppModel.AIRPLAY_PKG.equals(it.pkg)) {
            openTvSettings(R.string.home_airplay_hint);
            return;
        }
        if (AppModel.PROFILE_PICKER_PKG.equals(it.pkg)) {
            openTvSettings(R.string.home_profile_hint);
            return;
        }
        if (AppModel.AMAZON_KIDS_PKG.equals(it.pkg)) {
            openTvSettings(R.string.home_kids_hint);
            return;
        }
        if (AppModel.WSIW_PKG.equals(it.pkg)) {
            Toast.makeText(ctx, R.string.home_wsiw_hint, Toast.LENGTH_LONG).show();
            return;
        }
        AppModel.noteLaunched(ctx, it);
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(it.pkg, it.cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ctx.startActivity(i);
            return;
        } catch (Throwable t) { }
        try {
            Intent f = ctx.getPackageManager().getLaunchIntentForPackage(it.pkg);
            if (f != null) { f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(f); }
        } catch (Throwable t) { }
    }

    /**
     * Open Fire TV's own Settings menu (the standard LEANBACK_SETTINGS entry any launcher may use) and
     * show a brief bubble saying where to go, for apps Fire OS won't let a third-party launcher open
     * directly. The Home redirect stands down for Settings, so the overlay does not cover it.
     */
    private void openTvSettings(int hintRes) {
        HomeRedirectService.suspend(30000L);
        Toast.makeText(ctx, hintRes, Toast.LENGTH_LONG).show();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); return; } catch (Throwable t) { }
        // Fallback: the launcher's main Settings activity by name.
        try {
            Intent e = new Intent(Intent.ACTION_MAIN)
                    .setClassName(AppModel.AMAZON_LAUNCHER, "com.amazon.tv.launcher.ui.MainSettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(e); return;
        } catch (Throwable t) { }
        try { ctx.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable t) { }
    }
}
