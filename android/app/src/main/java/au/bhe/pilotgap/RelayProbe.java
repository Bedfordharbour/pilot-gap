package au.bhe.pilotgap;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Test build (Ryan 4b, 19 Oct; fixed 20 Oct): a second, separate connection to the same relay, run by
 * the background service in Android's own code instead of the web page. It uses exactly the same
 * route as the page (secure WebSocket to broker.hivemq.com:8884/mqtt) but only connects and pings;
 * it carries no positions. If it stays up while the page's connection dies in the background, the
 * relay should move into the service. If it dies too, the phone is blocking background network.
 * 1.0.5's probe never connected (it did not tell the server its name, and logged no reason).
 */
class RelayProbe implements Runnable {
    interface Sink { void log(String msg); }
    static final int KEEPALIVE_S = 30, PING_MS = 20000, RETRY_MS = 30000, TIMEOUT_MS = 10000;

    final Sink sink;
    final String host, path;
    final int port;
    final boolean tls;
    volatile boolean run = true, up;
    volatile Socket sock;
    Thread thread;
    int pingsOk, pingsFailed, connects, connectFails;
    long lastRtt = -1, upSince;
    String lastError = "", via = "";
    long lastErrorLogged;
    final SecureRandom rnd = new SecureRandom();

    RelayProbe(Sink s) { this(s, "broker.hivemq.com", 8884, "/mqtt", true); }
    RelayProbe(Sink s, String host, int port, String path, boolean tls) { sink = s; this.host = host; this.port = port; this.path = path; this.tls = tls; }
    void start() { thread = new Thread(this, "pilotgap-probe"); thread.setDaemon(true); thread.start(); }
    void stop() { run = false; try { if (sock != null) sock.close(); } catch (Exception ignored) { } if (thread != null) thread.interrupt(); }

    /** Counts since the last call, for the once-a-minute line. */
    synchronized String summary() {
        String out = (up ? "up " + ((System.currentTimeMillis() - upSince) / 1000) + " s" : "down")
            + ", pings " + pingsOk + " ok " + pingsFailed + " failed"
            + (lastRtt >= 0 ? ", last " + lastRtt + " ms" : "")
            + ", connects " + connects + " ok " + connectFails + " failed"
            + (!up && lastError.length() > 0 ? " (" + lastError + ")" : "");
        pingsOk = 0; pingsFailed = 0; connects = 0; connectFails = 0;
        return out;
    }

    // ---- WebSocket framing (client frames are masked) ----
    void wsSend(OutputStream o, byte[] data, int opcode) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x80 | opcode);
        if (data.length < 126) b.write(0x80 | data.length);
        else { b.write(0x80 | 126); b.write(data.length >> 8); b.write(data.length & 0xff); }
        byte[] mask = new byte[4]; rnd.nextBytes(mask); b.write(mask);
        for (int i = 0; i < data.length; i++) b.write(data[i] ^ mask[i & 3]);
        o.write(b.toByteArray()); o.flush();
    }
    static int rd(InputStream in) throws Exception { int c = in.read(); if (c < 0) throw new EOFException("closed by server"); return c; }
    /** Next binary payload; answers WebSocket pings, fails on close. */
    byte[] wsRead(InputStream in, OutputStream o) throws Exception {
        while (true) {
            int b0 = rd(in), b1 = rd(in), op = b0 & 0x0f;
            long len = b1 & 0x7f;
            if (len == 126) len = (rd(in) << 8) | rd(in);
            else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | rd(in); }
            byte[] m = null; if ((b1 & 0x80) != 0) { m = new byte[4]; for (int i = 0; i < 4; i++) m[i] = (byte) rd(in); }
            byte[] p = new byte[(int) len];
            for (int i = 0; i < len; i++) { p[i] = (byte) rd(in); if (m != null) p[i] ^= m[i & 3]; }
            if (op == 8) throw new EOFException("closed by server");
            if (op == 9) { wsSend(o, p, 10); continue; }
            if (op == 2 || op == 0) return p;
        }
    }

    void fail(Exception e) {
        String why = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        boolean wasUp;
        synchronized (this) { wasUp = up; if (up) pingsFailed++; else connectFails++; up = false; lastError = why; }
        long now = System.currentTimeMillis();
        if (wasUp) sink.log("connection lost (" + why + ")");
        else if (now - lastErrorLogged > 300000) { lastErrorLogged = now; sink.log("could not connect (" + why + ")"); }
    }

    @Override public void run() {
        while (run) {
            try {
                // Try IPv4 first, then the rest (20 Oct, app 1.0.6: the relay's IPv6 address refused port 8884 on mobile)
                InetAddress[] all = InetAddress.getAllByName(host);
                java.util.List<InetAddress> order = new java.util.ArrayList<>();
                for (InetAddress ad : all) if (ad instanceof Inet4Address) order.add(ad);
                for (InetAddress ad : all) if (!(ad instanceof Inet4Address)) order.add(ad);
                Socket raw = null; Exception last = null;
                for (InetAddress ad : order) {
                    Socket t = new Socket();
                    try { t.connect(new InetSocketAddress(ad, port), TIMEOUT_MS); raw = t; via = ad.getHostAddress(); break; }
                    catch (Exception ce) { last = ce; try { t.close(); } catch (Exception ignored) { } }
                }
                if (raw == null) throw last != null ? last : new Exception("no address for " + host);
                Socket s = raw;
                if (tls) {
                    SSLSocket ss = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);   // tells the server its name (SNI)
                    ss.setSoTimeout(TIMEOUT_MS);
                    ss.startHandshake();
                    if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ss.getSession())) throw new Exception("certificate does not match " + host);
                    s = ss;
                }
                sock = s;
                s.setSoTimeout(TIMEOUT_MS);
                OutputStream o = s.getOutputStream();
                InputStream in = s.getInputStream();
                // WebSocket upgrade, sub-protocol "mqtt" (same as the page)
                byte[] k = new byte[16]; rnd.nextBytes(k);
                String req = "GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString(k) + "\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: mqtt\r\n\r\n";
                o.write(req.getBytes(StandardCharsets.US_ASCII)); o.flush();
                StringBuilder hdr = new StringBuilder();
                while (!hdr.toString().endsWith("\r\n\r\n")) { hdr.append((char) rd(in)); if (hdr.length() > 8192) throw new Exception("reply too long"); }
                String first = hdr.substring(0, Math.max(0, hdr.indexOf("\r\n")));
                if (!first.contains(" 101")) throw new Exception("upgrade refused: " + first);
                // MQTT 3.1.1 CONNECT: "MQTT", level 4, clean session, keepalive
                byte[] id = ("pgprobe_" + Integer.toHexString(rnd.nextInt())).getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream c = new ByteArrayOutputStream();
                c.write(0x10); c.write(10 + 2 + id.length);
                c.write(new byte[]{0, 4, 'M', 'Q', 'T', 'T', 4, 2, 0, (byte) KEEPALIVE_S});
                c.write(id.length >> 8); c.write(id.length & 0xff); c.write(id);
                wsSend(o, c.toByteArray(), 2);
                byte[] ack = wsRead(in, o);
                if (ack.length < 4 || (ack[0] & 0xff) != 0x20 || ack[3] != 0) throw new Exception("relay refused (" + (ack.length >= 4 ? ack[3] : -1) + ")");
                synchronized (this) { up = true; upSince = System.currentTimeMillis(); connects++; lastError = ""; }
                sink.log("connected via " + via);
                while (run) {
                    Thread.sleep(PING_MS);
                    long t0 = System.currentTimeMillis();
                    wsSend(o, new byte[]{(byte) 0xC0, 0}, 2);
                    byte[] r = wsRead(in, o);
                    if (r.length < 2 || (r[0] & 0xff) != 0xD0) throw new Exception("no ping reply");
                    synchronized (this) { pingsOk++; lastRtt = System.currentTimeMillis() - t0; }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                fail(e);
            } finally {
                try { if (sock != null) sock.close(); } catch (Exception ignored) { }
            }
            try { Thread.sleep(RETRY_MS); } catch (InterruptedException e) { break; }
        }
    }
}
