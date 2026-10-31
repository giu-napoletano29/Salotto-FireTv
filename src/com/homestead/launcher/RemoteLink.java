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

import java.io.UnsupportedEncodingException;

/**
 * Opens a web address on the TV for the phone remote: the same as tapping a link, so a
 * YouTube address opens in the YouTube app and any other in the browser. Only http and https
 * addresses go through, and the address reaches the shell only after every character outside
 * the set a URL is made of has been percent-encoded, so nothing in it can end the quotes it is
 * placed in or mean anything to the shell. It runs over the on-device ADB link, because an app
 * in the background cannot start another one on newer Android.
 */
final class RemoteLink {

    static final int MAX = 1500;

    private RemoteLink() { }

    /** The address ready to use, or null if it is not an http(s) address of a sensible size. */
    static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() == 0 || s.length() > MAX) return null;
        String lower = s.toLowerCase(java.util.Locale.US);
        int start = lower.startsWith("https://") ? 8 : lower.startsWith("http://") ? 7 : -1;
        if (start < 0 || s.length() == start || s.charAt(start) == '/') return null;
        StringBuilder out = new StringBuilder(s.length() + 16);
        byte[] bytes;
        try { bytes = s.getBytes("UTF-8"); } catch (UnsupportedEncodingException e) { return null; }
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            if (safe(b)) out.append((char) b);
            else out.append('%').append(Character.toUpperCase(Character.forDigit(b >> 4, 16)))
                    .append(Character.toUpperCase(Character.forDigit(b & 15, 16)));
        }
        return out.toString();
    }

    /** The characters of a URL; the single quote is not one of them (it is what ends the quotes). */
    private static boolean safe(int b) {
        if (b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z' || b >= '0' && b <= '9') return true;
        return "-._~:/?#[]@!$&()*+,;=%".indexOf(b) >= 0;
    }

    /** Starts the address on the TV. {@code cb} gets true if the system found something to open it. */
    static void open(Context c, String url, final AdbBridge.Callback cb) {
        AdbBridge.run("am start -a android.intent.action.VIEW -d '" + url + "'", new AdbBridge.Callback() {
            public void onDone(boolean ok, String out) {
                // The log opens with the command itself, which holds the address: look past it.
                String said = out == null ? "" : out;
                int nl = said.indexOf('\n');
                if (said.startsWith("$ ") && nl >= 0) said = said.substring(nl + 1);
                boolean found = ok && said.contains("Starting")
                        && !said.contains("Error") && !said.contains("Exception");
                cb.onDone(found, out);
            }
        });
    }
}
