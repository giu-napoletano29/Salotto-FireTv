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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * The phone remote: a small web server, so a phone on the same Wi-Fi can search the TV's apps
 * and channels and open them on the TV. It listens only while the switch in Settings is on
 * (off until chosen), and it is stopped the moment the switch is turned off.
 *
 * What it costs while on: one thread blocked in accept(), which uses no processor, and two
 * workers that exist only to serve a request. It reads and writes nothing until a phone asks.
 *
 * What protects it, since the port stays open:
 *  - only addresses of a private network are served (a router that forwards the port from
 *    the internet is not enough to reach it);
 *  - the page itself is public but holds no data; everything under /api/ needs a token, sent
 *    as a header (so another site open in the phone cannot use it);
 *  - a phone gets a token by pairing: it scans the QR code, or types the six digits, that the
 *    "Pair a phone" screen on the TV shows. The code changes every minute, works once, and
 *    exists only while that screen is open, so nobody can pair without being at the TV. The
 *    token is a session that ends after the time set in Settings;
 *  - wrong codes are counted per address and overall, and answered with 429 for a while;
 *  - a request whose Host or Origin is not this TV's own address is refused;
 *  - the phone can press only the keys of RemoteKeys, type text, open an http(s) address
 *    (RemoteLink) and change only what RemoteSettings lists; it cannot run commands.
 * It is plain HTTP: someone who can read the Wi-Fi traffic can read the code while it is
 * valid, and a token. See SECURITY.md.
 *
 * Everything that touches the app (search, opening) runs on the main thread, one request at a
 * time; the server threads only parse and answer.
 */
final class RemoteServer {

    private static final String TAG = "Homestead";

    /** First port tried; the next few are used if it is taken. */
    static final int PORT = 8765;
    private static final int PORT_TRIES = 6;
    private static final int MAX_HEADERS = 8192;
    private static final int MAX_BODY = 2048;
    private static final int SEARCH_MAX = 20;
    /** How long a built index is reused: a burst of keystrokes from the phone costs one build. */
    private static final long INDEX_MS = 15000L;
    private static final int FAILS_PER_ADDRESS = 5;
    private static final long FAIL_WINDOW_MS = 60000L;
    private static final int FAILS_OVERALL = 40;
    private static final long FAIL_WINDOW_ALL_MS = 10L * 60L * 1000L;

    private static final Pattern HOST_OK = Pattern.compile(
            "^(\\d{1,3}(\\.\\d{1,3}){3}|\\[[0-9a-fA-F:]+\\]|localhost|[a-zA-Z0-9-]+\\.local)(:\\d{1,5})?$");

    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final SecureRandom RANDOM = new SecureRandom();

    private static ServerSocket server;
    private static ExecutorService pool;
    private static int port;
    private static Context app;
    private static byte[] page;

    // Pairing: a code valid only while the pair screen is open, for PAIR_MS, once. The one
    // before it is still taken for PAIR_GRACE_MS, so a phone that scanned just before the
    // code changed is not turned away.
    private static final long PAIR_MS = 60000L;
    private static final long PAIR_GRACE_MS = 10000L;
    private static boolean pairing;
    private static String pairCode, pairPrevious;
    private static long pairPreviousUntil;
    private static Runnable pairedListener;

    // Main thread only.
    private static SearchIndex index;
    private static long indexAt;

    private static final Map<String, long[]> fails = new HashMap<String, long[]>();
    private static long failsAllStart;
    private static int failsAll;

    private RemoteServer() { }

    // ---------------------------------------------------------------- on and off

    /** Makes the server match the switch: listening if it is on, closed if not. Cheap to repeat. */
    static void ensure(Context c) {
        Context a = c.getApplicationContext();
        if (a == null) a = c;
        if (Prefs.remoteOn(a)) start(a); else stop();
    }

    private static void start(Context a) {
        synchronized (LOCK) {
            if (server != null && !server.isClosed()) return;
            app = a;
            ServerSocket s = null;
            for (int i = 0; i < PORT_TRIES && s == null; i++) {
                try {
                    ServerSocket t = new ServerSocket();
                    t.setReuseAddress(true);
                    t.bind(new InetSocketAddress(PORT + i));
                    s = t;
                    port = PORT + i;
                } catch (IOException ignored) { }
            }
            if (s == null) {
                Log.w(TAG, "phone remote: no free port from " + PORT);
                return;
            }
            server = s;
            // Phones open a few connections ahead of the request, and one that never sends
            // anything holds its worker until the read times out: more than two workers, or a
            // real request waits behind them.
            pool = Executors.newFixedThreadPool(4, new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "remote-worker");
                    t.setDaemon(true);
                    return t;
                }
            });
            final ServerSocket ss = s;
            Thread t = new Thread(new Runnable() {
                public void run() { acceptLoop(ss); }
            }, "remote-accept");
            t.setDaemon(true);
            t.start();
            Log.i(TAG, "phone remote: listening on port " + port);
        }
    }

    static void stop() {
        synchronized (LOCK) {
            if (server == null) return;
            try { server.close(); } catch (IOException ignored) { }
            server = null;
            if (pool != null) pool.shutdownNow();
            pool = null;
            RemoteKeys.stop();
            Log.i(TAG, "phone remote: stopped");
        }
    }

    static boolean running() {
        synchronized (LOCK) {
            return server != null && !server.isClosed();
        }
    }

    /** "http://192.168.1.114:8765" while listening and on a network; null otherwise. */
    static String address() {
        int p;
        synchronized (LOCK) {
            if (server == null || server.isClosed()) return null;
            p = port;
        }
        String ip = localIp();
        return ip == null ? null : "http://" + ip + ":" + p;
    }

    private static String localIp() {
        String first = null;
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String n = ni.getName();
                    if (n.startsWith("wlan") || n.startsWith("eth")) return a.getHostAddress();
                    if (first == null) first = a.getHostAddress();
                }
            }
        } catch (Throwable ignored) { }
        return first;
    }

    // ---------------------------------------------------------------- connections

    private static void acceptLoop(ServerSocket ss) {
        while (!ss.isClosed()) {
            try {
                final Socket c = ss.accept();
                ExecutorService p = pool;
                if (p == null) { closeQuietly(c); break; }
                try {
                    p.execute(new Runnable() {
                        public void run() { serve(c); }
                    });
                } catch (RejectedExecutionException e) {
                    closeQuietly(c);
                }
            } catch (IOException e) {
                if (ss.isClosed()) break;
                try { Thread.sleep(300L); } catch (InterruptedException ie) { break; }
            }
        }
    }

    private static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) { }
    }

    private static void serve(Socket s) {
        try {
            s.setSoTimeout(3000);
            if (!privatePeer(s.getInetAddress())) return;
            Req r = readRequest(new BufferedInputStream(s.getInputStream(), 4096));
            Resp resp = r == null ? text(400, "Bad request")
                    : route(r, s.getInetAddress().getHostAddress());
            write(s.getOutputStream(), resp);
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(s);
        }
    }

    /** Loopback, or an address of a private network (10.x, 172.16-31.x, 192.168.x, link-local, fc00::/7). */
    private static boolean privatePeer(InetAddress a) {
        if (a == null) return false;
        if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) return true;
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    // ---------------------------------------------------------------- HTTP

    private static final class Req {
        String method, path, query = "", body = "";
        final Map<String, String> headers = new HashMap<String, String>();
    }

    private static final class Resp {
        final int code;
        final String type;
        final byte[] body;
        final boolean html;
        Resp(int code, String type, byte[] body, boolean html) {
            this.code = code; this.type = type; this.body = body; this.html = html;
        }
    }

    private static Req readRequest(InputStream in) throws IOException {
        String line = readLine(in, 2048);
        if (line == null) return null;
        String[] parts = line.split(" ");
        if (parts.length < 2) return null;
        Req r = new Req();
        r.method = parts[0];
        String target = parts[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        if (q >= 0) r.query = target.substring(q + 1);
        int total = line.length();
        while (true) {
            String h = readLine(in, 2048);
            if (h == null) return null;
            if (h.length() == 0) break;
            total += h.length();
            if (total > MAX_HEADERS) return null;
            int c = h.indexOf(':');
            if (c > 0) {
                r.headers.put(h.substring(0, c).trim().toLowerCase(Locale.US), h.substring(c + 1).trim());
            }
        }
        int len = 0;
        String cl = r.headers.get("content-length");
        if (cl != null) {
            try { len = Integer.parseInt(cl); } catch (NumberFormatException e) { return null; }
        }
        if (len < 0 || len > MAX_BODY) return null;
        byte[] b = new byte[len];
        int got = 0;
        while (got < len) {
            int n = in.read(b, got, len - got);
            if (n < 0) return null;
            got += n;
        }
        r.body = new String(b, "UTF-8");
        return r;
    }

    /** One line without its line end; null at the end of the stream or past {@code max}. */
    private static String readLine(InputStream in, int max) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(64);
        while (true) {
            int ch = in.read();
            if (ch < 0) return null;
            if (ch == '\n') break;
            if (ch != '\r') buf.write(ch);
            if (buf.size() > max) return null;
        }
        return new String(buf.toByteArray(), "UTF-8");
    }

    private static void write(OutputStream out, Resp r) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(r.code).append(' ').append(reason(r.code)).append("\r\n");
        h.append("Content-Type: ").append(r.type).append("\r\n");
        h.append("Content-Length: ").append(r.body.length).append("\r\n");
        h.append("Cache-Control: no-store\r\n");
        h.append("X-Content-Type-Options: nosniff\r\n");
        h.append("Referrer-Policy: no-referrer\r\n");
        if (r.html) {
            h.append("X-Frame-Options: DENY\r\n");
            h.append("Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; "
                    + "style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; "
                    + "form-action 'none'\r\n");
        }
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes("UTF-8"));
        out.write(r.body);
        out.flush();
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 429: return "Too Many Requests";
            case 503: return "Service Unavailable";
            default: return "Error";
        }
    }

    private static Resp text(int code, String s) {
        return new Resp(code, "text/plain; charset=utf-8", bytes(s), false);
    }

    private static Resp json(int code, JSONObject o) {
        return new Resp(code, "application/json; charset=utf-8", bytes(o.toString()), false);
    }

    private static Resp error(int code, String what) {
        try { return json(code, new JSONObject().put("error", what)); }
        catch (Exception e) { return text(code, what); }
    }

    private static byte[] bytes(String s) {
        try { return s.getBytes("UTF-8"); } catch (Exception e) { return new byte[0]; }
    }

    // ---------------------------------------------------------------- routes

    private static Resp route(Req r, String ip) {
        String host = r.headers.get("host");
        if (host == null || !HOST_OK.matcher(host).matches()) return text(403, "Forbidden");
        String origin = r.headers.get("origin");
        if (origin != null && !origin.equals("http://" + host)) return text(403, "Forbidden");

        boolean get = "GET".equals(r.method), post = "POST".equals(r.method);
        if (get && ("/".equals(r.path) || "/index.html".equals(r.path))) return pageResp();
        if (!r.path.startsWith("/api/")) return text(404, "Not found");
        if (post && "/api/pair".equals(r.path)) return pair(r, ip);

        String token = bearer(r);
        long until = Prefs.remoteTokenExpiry(app, token);
        if (until < 0) return error(401, "auth");
        try {
            if (get && "/api/ping".equals(r.path)) {
                if ("1".equals(param(r.query, "warm"))) {
                    onMain(new Callable<Boolean>() {
                        public Boolean call() { RemoteKeys.warm(app); return Boolean.TRUE; }
                    });
                }
                return json(200, new JSONObject().put("ok", true).put("name", "Salotto")
                        .put("version", UpdateChecker.installedVersionName(app))
                        .put("expires", until));
            }
            if (get && "/api/search".equals(r.path)) return search(param(r.query, "q"));
            if (post && "/api/open".equals(r.path)) {
                return open(new JSONObject(r.body).optString("id", ""));
            }
            if (post && "/api/key".equals(r.path)) {
                return key(new JSONObject(r.body).optString("key", ""));
            }
            if (post && "/api/text".equals(r.path)) {
                JSONObject o = new JSONObject(r.body);
                return typeText(o.optString("text", ""), o.optInt("del", 0));
            }
            if (post && "/api/link".equals(r.path)) {
                return link(new JSONObject(r.body).optString("url", ""));
            }
            if (get && "/api/settings".equals(r.path)) return settings();
            if (post && "/api/setting".equals(r.path)) {
                JSONObject o = new JSONObject(r.body);
                return setting(o.optString("id", ""), o.optString("value", ""));
            }
            if (post && "/api/logout".equals(r.path)) {
                Prefs.removeRemoteToken(app, token);
                return json(200, new JSONObject().put("ok", true));
            }
        } catch (Exception e) {
            return error(400, "request");
        }
        return text(404, "Not found");
    }

    private static Resp pageResp() {
        try {
            if (page == null) {
                InputStream in = app.getResources().openRawResource(R.raw.remote);
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    page = out.toByteArray();
                } finally {
                    in.close();
                }
            }
            return new Resp(200, "text/html; charset=utf-8", page, true);
        } catch (Throwable t) {
            return text(503, "Unavailable");
        }
    }

    private static String bearer(Req r) {
        String a = r.headers.get("authorization");
        if (a == null || !a.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return a.substring(7).trim();
    }

    private static String param(String query, String name) {
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            int e = pairs[i].indexOf('=');
            String k = e < 0 ? pairs[i] : pairs[i].substring(0, e);
            if (!k.equals(name)) continue;
            try {
                return e < 0 ? "" : URLDecoder.decode(pairs[i].substring(e + 1), "UTF-8");
            } catch (Exception ex) {
                return "";
            }
        }
        return "";
    }

    private static Resp pair(Req r, String ip) {
        long now = SystemClock.elapsedRealtime();
        if (blocked(ip, now)) return error(429, "wait");
        String code = "";
        try { code = new JSONObject(r.body).optString("code", ""); } catch (Exception ignored) { }
        if (!takePairCode(code)) {
            noteFail(ip, now);
            return error(401, "code");
        }
        synchronized (fails) { fails.remove(ip); }
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length; i++) sb.append(String.format(Locale.US, "%02x", raw[i] & 0xFF));
        String token = sb.toString();
        Prefs.addRemoteToken(app, token);
        Runnable l;
        synchronized (PAIR) { l = pairedListener; }
        if (l != null) MAIN.post(l);
        try {
            return json(200, new JSONObject().put("token", token)
                    .put("expires", Prefs.remoteTokenExpiry(app, token)));
        } catch (Exception e) {
            return error(503, "token");
        }
    }

    // ---------------------------------------------------------------- pairing

    private static final Object PAIR = new Object();
    private static long pairUntil;

    /** Opens pairing and returns the first code. Called when the pair screen opens. */
    static String pairStart() {
        synchronized (PAIR) {
            pairing = true;
            pairPrevious = null;
            return rotate();
        }
    }

    /** A new code; the one before it is still taken for a few seconds. */
    static String pairRotate() {
        synchronized (PAIR) {
            return rotate();
        }
    }

    /** Closes pairing: no code works until the screen is opened again. */
    static void pairStop() {
        synchronized (PAIR) {
            pairing = false;
            pairCode = null;
            pairPrevious = null;
            pairedListener = null;
        }
    }

    /** Runs on the main thread when a phone has paired; the screen uses it to close itself. */
    static void setPairedListener(Runnable l) {
        synchronized (PAIR) { pairedListener = l; }
    }

    /** What the QR code holds: the TV's address with the code, or null with no network. */
    static String pairUrl() {
        String a = address();
        synchronized (PAIR) {
            return a == null || pairCode == null ? null : a + "/?c=" + pairCode;
        }
    }

    static String pairCode() {
        synchronized (PAIR) { return pairCode; }
    }

    /** Under PAIR. */
    private static String rotate() {
        long now = SystemClock.elapsedRealtime();
        pairPrevious = pairCode;
        pairPreviousUntil = now + PAIR_GRACE_MS;
        pairUntil = now + PAIR_MS + PAIR_GRACE_MS;
        pairCode = String.format(Locale.US, "%06d", RANDOM.nextInt(1000000));
        return pairCode;
    }

    /** True for the current code, or the one before it just after a change; each code works once. */
    private static boolean takePairCode(String code) {
        synchronized (PAIR) {
            long now = SystemClock.elapsedRealtime();
            if (!pairing || code == null || code.length() != 6 || now > pairUntil) return false;
            boolean current = pairCode != null
                    && MessageDigest.isEqual(code.getBytes(), pairCode.getBytes());
            boolean previous = pairPrevious != null && now < pairPreviousUntil
                    && MessageDigest.isEqual(code.getBytes(), pairPrevious.getBytes());
            if (!current && !previous) return false;
            rotate();
            pairPrevious = null;     // the code just used must not work again
            return true;
        }
    }

    private static boolean blocked(String ip, long now) {
        synchronized (fails) {
            if (failsAll >= FAILS_OVERALL && now - failsAllStart < FAIL_WINDOW_ALL_MS) return true;
            long[] f = fails.get(ip);
            return f != null && f[0] >= FAILS_PER_ADDRESS && now - f[1] < FAIL_WINDOW_MS;
        }
    }

    private static void noteFail(String ip, long now) {
        synchronized (fails) {
            if (fails.size() > 200) fails.clear();
            long[] f = fails.get(ip);
            if (f == null || now - f[1] >= FAIL_WINDOW_MS) f = new long[]{ 0L, now };
            f[0]++;
            fails.put(ip, f);
            if (failsAll == 0 || now - failsAllStart >= FAIL_WINDOW_ALL_MS) {
                failsAll = 0;
                failsAllStart = now;
            }
            failsAll++;
        }
    }

    private static Resp search(final String query) throws Exception {
        String out = onMain(new Callable<String>() {
            public String call() throws Exception {
                List<SearchIndex.Hit> hits = query.trim().length() == 0
                        ? SearchIndex.recent(app, 8) : index().find(query, SEARCH_MAX);
                JSONArray arr = new JSONArray();
                for (int i = 0; i < hits.size(); i++) {
                    SearchIndex.Hit h = hits.get(i);
                    arr.put(new JSONObject().put("id", h.id).put("title", h.title)
                            .put("hint", h.hint == null ? "" : h.hint));
                }
                return new JSONObject().put("hits", arr).toString();
            }
        });
        if (out == null) return error(503, "busy");
        return new Resp(200, "application/json; charset=utf-8", bytes(out), false);
    }

    private static Resp open(final String id) throws Exception {
        if (!(id.startsWith("app:") || id.startsWith("ch:")) || id.length() > 200) {
            return error(400, "id");
        }
        Boolean ok = onMain(new Callable<Boolean>() {
            public Boolean call() { return SearchIndex.open(app, id); }
        });
        if (ok == null) return error(503, "busy");
        return json(200, new JSONObject().put("ok", ok.booleanValue()));
    }

    private static Resp key(final String name) throws Exception {
        String res = onMain(new Callable<String>() {
            public String call() { return RemoteKeys.press(app, name); }
        });
        if (res == null) return error(503, "busy");
        if (RemoteKeys.UNKNOWN.equals(res)) return error(400, "key");
        return json(200, new JSONObject().put("ok", RemoteKeys.OK.equals(res)).put("result", res));
    }

    private static Resp typeText(final String text, final int del) throws Exception {
        String res = onMain(new Callable<String>() {
            public String call() { return RemoteKeys.type(app, text, del); }
        });
        if (res == null) return error(503, "busy");
        if (RemoteKeys.UNKNOWN.equals(res)) return error(400, "text");
        return json(200, new JSONObject().put("ok", RemoteKeys.OK.equals(res)).put("result", res));
    }

    /** Opens an http(s) address on the TV and says whether the system found a way to open it. */
    private static Resp link(String raw) throws Exception {
        final String url = RemoteLink.clean(raw);
        if (url == null) return error(400, "url");
        final CountDownLatch done = new CountDownLatch(1);
        final boolean[] result = new boolean[1];
        Boolean started = onMain(new Callable<Boolean>() {
            public Boolean call() {
                if (!AdbBridge.canRun(app)) return Boolean.FALSE;
                RemoteLink.open(app, url, new AdbBridge.Callback() {
                    public void onDone(boolean ok, String out) {
                        result[0] = ok;
                        done.countDown();
                    }
                });
                return Boolean.TRUE;
            }
        });
        if (started == null) return error(503, "busy");
        if (!started.booleanValue()) {
            return json(200, new JSONObject().put("ok", false).put("result", RemoteKeys.NEEDS_LINK));
        }
        boolean in = done.await(8, TimeUnit.SECONDS);
        return json(200, new JSONObject().put("ok", in && result[0])
                .put("result", in ? (result[0] ? RemoteKeys.OK : "not_opened") : "timeout"));
    }

    private static Resp settings() throws Exception {
        String out = onMain(new Callable<String>() {
            public String call() throws Exception { return RemoteSettings.schema(app); }
        });
        if (out == null) return error(503, "busy");
        return new Resp(200, "application/json; charset=utf-8", bytes(out), false);
    }

    private static Resp setting(final String id, final String value) throws Exception {
        String res = onMain(new Callable<String>() {
            public String call() { return RemoteSettings.apply(app, id, value); }
        });
        if (res == null) return error(503, "busy");
        if (RemoteSettings.BAD.equals(res)) return error(400, "setting");
        return json(200, new JSONObject().put("ok", RemoteSettings.OK.equals(res)
                || RemoteSettings.PENDING.equals(res)).put("result", res));
    }

    /** Main thread only. */
    private static SearchIndex index() {
        long now = SystemClock.elapsedRealtime();
        if (index == null || now - indexAt > INDEX_MS) {
            index = new SearchIndex(app);
            indexAt = now;
        }
        return index;
    }

    /** Runs a job on the main thread and waits for it; null if it did not finish in time. */
    private static <T> T onMain(Callable<T> job) {
        FutureTask<T> f = new FutureTask<T>(job);
        MAIN.post(f);
        try {
            return f.get(3, TimeUnit.SECONDS);
        } catch (Throwable t) {
            f.cancel(false);
            return null;
        }
    }
}
