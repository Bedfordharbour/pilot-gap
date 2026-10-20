package au.bhe.pilotgap;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * The relay connection, run in Android's own code (app 1.1.0; Ryan 4a, 20 Oct).
 * Field test 20 Oct 08:26-08:36: a connection made here stayed up for 10 min while the app was behind
 * other apps, but the page's own connection died about 100 s after the app went to the background
 * every time. So the page now hands its (already scrambled) messages to this class, which carries
 * them to the relay; it never sees positions in plain form.
 * Route: secure WebSocket to broker.hivemq.com:8884/mqtt (same as the page), MQTT 3.1.1, QoS 0.
 * IPv4 addresses are tried first (20 Oct: the relay's IPv6 address refused port 8884 on mobile).
 */
class NativeRelay implements Runnable {
    interface Listener {
        void onConnected();
        void onClosed(String why);
        void onMessage(String topic, String payload, boolean retained);
    }
    static final int KEEPALIVE_S = 30, PING_MS = 15000, DEAD_MS = 45000, TIMEOUT_MS = 10000;
    static final int[] RETRY_MS = {2000, 5000, 10000, 15000};

    final Listener ls;
    final String host, path, filter, clientId;
    final int port;
    final boolean tls;
    final SecureRandom rnd = new SecureRandom();
    final Object writeLock = new Object();
    volatile boolean run = true, up;
    int id;
    volatile Socket sock;
    volatile OutputStream out;
    volatile long lastRx;
    Thread thread, pinger;
    // counts since the last summary (once a minute in the trip log)
    int msgsIn, msgsOut, connects, connectFails, pingsOk;
    long upSince, lastRtt = -1, pingSent;
    String lastError = "", via = "";

    NativeRelay(Listener l, String filter, String clientId) { this(l, "broker.hivemq.com", 8884, "/mqtt", true, filter, clientId); }
    NativeRelay(Listener l, String host, int port, String path, boolean tls, String filter, String clientId) {
        ls = l; this.host = host; this.port = port; this.path = path; this.tls = tls; this.filter = filter; this.clientId = clientId;
    }

    void start() { thread = new Thread(this, "pilotgap-relay"); thread.setDaemon(true); thread.start(); }

    void stop() {
        run = false;
        try { synchronized (writeLock) { if (out != null && up) wsSend(out, new byte[]{(byte) 0xE0, 0}); } } catch (Exception ignored) { }   // DISCONNECT
        closeSock();
        if (thread != null) thread.interrupt();
        if (pinger != null) pinger.interrupt();
    }

    void closeSock() { try { if (sock != null) sock.close(); } catch (Exception ignored) { } }

    /** Called from the page (any thread). Returns false if not connected. */
    boolean publish(String topic, String payload, boolean retain) {
        if (!up || out == null) return false;
        try {
            byte[] t = topic.getBytes(StandardCharsets.UTF_8), p = payload.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write(0x30 | (retain ? 1 : 0));
            writeLen(b, 2 + t.length + p.length);
            b.write(t.length >> 8); b.write(t.length & 0xff); b.write(t); b.write(p);
            synchronized (writeLock) { wsSend(out, b.toByteArray()); }
            synchronized (this) { msgsOut++; }
            return true;
        } catch (Exception e) { closeSock(); return false; }
    }

    synchronized String summary() {
        String s = (up ? "up " + ((System.currentTimeMillis() - upSince) / 1000) + " s via " + via : "down")
            + ", msgs in " + msgsIn + " out " + msgsOut + ", pings " + pingsOk + (lastRtt >= 0 ? " (last " + lastRtt + " ms)" : "")
            + ", connects " + connects + " ok " + connectFails + " failed"
            + (!up && lastError.length() > 0 ? " (" + lastError + ")" : "");
        msgsIn = 0; msgsOut = 0; pingsOk = 0; connects = 0; connectFails = 0;
        return s;
    }

    static void writeLen(ByteArrayOutputStream b, int n) {
        do { int d = n % 128; n /= 128; if (n > 0) d |= 0x80; b.write(d); } while (n > 0);
    }

    // ---- WebSocket (client frames masked, binary) ----
    void wsSend(OutputStream o, byte[] data) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x82);
        if (data.length < 126) b.write(0x80 | data.length);
        else if (data.length < 65536) { b.write(0x80 | 126); b.write(data.length >> 8); b.write(data.length & 0xff); }
        else throw new IOException("message too long");
        byte[] mask = new byte[4]; rnd.nextBytes(mask); b.write(mask, 0, 4);
        for (int i = 0; i < data.length; i++) b.write(data[i] ^ mask[i & 3]);
        o.write(b.toByteArray()); o.flush();
    }
    void wsControl(OutputStream o, int op, byte[] data) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x80 | op); b.write(0x80 | data.length);
        byte[] mask = new byte[4]; rnd.nextBytes(mask); b.write(mask, 0, 4);
        for (int i = 0; i < data.length; i++) b.write(data[i] ^ mask[i & 3]);
        o.write(b.toByteArray()); o.flush();
    }

    /** The bytes of all incoming binary WebSocket frames, as one stream (MQTT packets can span frames). */
    class WsIn extends InputStream {
        final InputStream in; byte[] buf = new byte[0]; int pos;
        WsIn(InputStream in) { this.in = in; }
        int rd() throws IOException { int c = in.read(); if (c < 0) throw new EOFException("closed by relay"); return c; }
        @Override public int read() throws IOException {
            while (pos >= buf.length) {
                int b0 = rd(), b1 = rd(), op = b0 & 0x0f;
                long len = b1 & 0x7f;
                if (len == 126) len = (rd() << 8) | rd();
                else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | rd(); }
                if (len > 1 << 20) throw new IOException("frame too long");
                byte[] m = null; if ((b1 & 0x80) != 0) { m = new byte[4]; for (int i = 0; i < 4; i++) m[i] = (byte) rd(); }
                byte[] p = new byte[(int) len];
                for (int i = 0; i < len; i++) { p[i] = (byte) rd(); if (m != null) p[i] ^= m[i & 3]; }
                lastRx = System.currentTimeMillis();
                if (op == 8) throw new EOFException("closed by relay");
                if (op == 9) { synchronized (writeLock) { wsControl(out, 10, p); } continue; }
                if (op == 10) continue;
                buf = p; pos = 0;
            }
            return buf[pos++] & 0xff;
        }
    }

    Socket open() throws Exception {
        InetAddress[] all = InetAddress.getAllByName(host);
        List<InetAddress> order = new ArrayList<>();
        for (InetAddress a : all) if (a instanceof Inet4Address) order.add(a);
        for (InetAddress a : all) if (!(a instanceof Inet4Address)) order.add(a);
        Exception last = null;
        for (InetAddress a : order) {
            Socket t = new Socket();
            try { t.connect(new InetSocketAddress(a, port), TIMEOUT_MS); via = a.getHostAddress(); return t; }
            catch (Exception e) { last = e; try { t.close(); } catch (Exception ignored) { } }
        }
        throw last != null ? last : new Exception("no address for " + host);
    }

    @Override public void run() {
        int fails = 0;
        while (run) {
            String why = null;
            try {
                Socket raw = open(), s = raw;
                sock = raw;   // closed on any failure below
                if (tls) {
                    SSLSocket ss = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
                    sock = ss;
                    ss.setSoTimeout(TIMEOUT_MS);
                    ss.startHandshake();
                    if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ss.getSession())) throw new Exception("certificate does not match " + host);
                    s = ss;
                }
                s.setSoTimeout(TIMEOUT_MS);
                OutputStream o = s.getOutputStream();
                InputStream raw_in = s.getInputStream();
                byte[] k = new byte[16]; rnd.nextBytes(k);
                String req = "GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString(k) + "\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: mqtt\r\n\r\n";
                o.write(req.getBytes(StandardCharsets.US_ASCII)); o.flush();
                StringBuilder hdr = new StringBuilder();
                while (!hdr.toString().endsWith("\r\n\r\n")) { int c = raw_in.read(); if (c < 0) throw new EOFException("closed during upgrade"); hdr.append((char) c); if (hdr.length() > 8192) throw new Exception("reply too long"); }
                String first = hdr.substring(0, Math.max(0, hdr.indexOf("\r\n")));
                if (!first.contains(" 101")) throw new Exception("upgrade refused: " + first);
                out = o;
                WsIn in = new WsIn(raw_in);
                // CONNECT (clean session, keepalive)
                byte[] id = clientId.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream c = new ByteArrayOutputStream();
                c.write(0x10); writeLen(c, 10 + 2 + id.length);
                c.write(new byte[]{0, 4, 'M', 'Q', 'T', 'T', 4, 2, 0, (byte) KEEPALIVE_S}, 0, 10);
                c.write(id.length >> 8); c.write(id.length & 0xff); c.write(id, 0, id.length);
                synchronized (writeLock) { wsSend(o, c.toByteArray()); }
                int h = in.read(); int len = readLen(in); byte[] ack = readN(in, len);
                if (h != 0x20 || len < 2 || ack[1] != 0) throw new Exception("relay refused (" + (len >= 2 ? ack[1] : -1) + ")");
                // SUBSCRIBE to the trip's channel, QoS 0
                byte[] f = filter.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream sb = new ByteArrayOutputStream();
                sb.write(0x82); writeLen(sb, 2 + 2 + f.length + 1);
                sb.write(0); sb.write(1); sb.write(f.length >> 8); sb.write(f.length & 0xff); sb.write(f, 0, f.length); sb.write(0);
                synchronized (writeLock) { wsSend(o, sb.toByteArray()); }
                s.setSoTimeout(0);   // the pinger closes the socket if the relay goes quiet
                lastRx = System.currentTimeMillis();
                synchronized (this) { up = true; upSince = lastRx; connects++; lastError = ""; }
                fails = 0;
                startPinger(s);
                ls.onConnected();
                while (run) {
                    int b = in.read(), type = b >> 4;
                    int n = readLen(in);
                    byte[] body = readN(in, n);
                    if (type == 3) {   // PUBLISH
                        int tl = ((body[0] & 0xff) << 8) | (body[1] & 0xff), qos = (b >> 1) & 3, off = 2 + tl + (qos > 0 ? 2 : 0);
                        String topic = new String(body, 2, tl, StandardCharsets.UTF_8);
                        String payload = new String(body, off, body.length - off, StandardCharsets.UTF_8);
                        synchronized (this) { msgsIn++; }
                        ls.onMessage(topic, payload, (b & 1) != 0);
                    } else if (type == 9) {   // SUBACK: 0x80 means the relay refused the subscription
                        if (body.length >= 3 && (body[2] & 0xff) == 0x80) throw new Exception("relay refused the subscription");
                    } else if (type == 13) {   // PINGRESP
                        synchronized (this) { pingsOk++; lastRtt = System.currentTimeMillis() - pingSent; }
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                why = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            } finally {
                boolean wasUp;
                synchronized (this) { wasUp = up; up = false; if (!wasUp && why != null) { connectFails++; lastError = why; } }
                out = null;
                if (pinger != null) pinger.interrupt();
                closeSock();
                if (wasUp && run) ls.onClosed(why == null ? "closed" : why);
            }
            if (!run) break;
            try { Thread.sleep(RETRY_MS[Math.min(fails++, RETRY_MS.length - 1)]); } catch (InterruptedException e) { break; }
        }
    }

    void startPinger(Socket s) {
        pinger = new Thread(() -> {
            try {
                while (run && sock == s) {
                    Thread.sleep(PING_MS);
                    if (System.currentTimeMillis() - lastRx > DEAD_MS) { synchronized (this) { lastError = "relay silent " + (DEAD_MS / 1000) + " s"; } closeSock(); return; }
                    pingSent = System.currentTimeMillis();
                    synchronized (writeLock) { if (out != null) wsSend(out, new byte[]{(byte) 0xC0, 0}); }
                }
            } catch (Exception e) { if (sock == s) closeSock(); }
        }, "pilotgap-ping");
        pinger.setDaemon(true); pinger.start();
    }

    static int readLen(InputStream in) throws IOException {
        int v = 0, m = 1, d;
        do { d = in.read(); if (d < 0) throw new EOFException(); v += (d & 0x7f) * m; m *= 128; if (m > 128 * 128 * 128 * 128) throw new IOException("bad length"); } while ((d & 0x80) != 0);
        return v;
    }
    static byte[] readN(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) { int c = in.read(); if (c < 0) throw new EOFException(); b[i] = (byte) c; }
        return b;
    }
}
