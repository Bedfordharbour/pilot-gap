package au.bhe.pilotgap;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Test build (Ryan 4b, 19 Oct): a second, separate connection to the same relay, run by the
 * background service in Android's own code instead of the web page. It only connects and pings
 * (MQTT over TLS, port 8883); it carries no positions. If it stays up while the page's connection
 * dies in the background, the relay should move into the service. If it dies too, the phone
 * itself is blocking background network (e.g. Samsung power saving).
 */
class RelayProbe implements Runnable {
    interface Sink { void log(String msg); }
    static final String HOST = "broker.hivemq.com";
    static final int PORT = 8883, KEEPALIVE_S = 30, PING_MS = 20000, RETRY_MS = 30000, TIMEOUT_MS = 10000;

    final Sink sink;
    volatile boolean run = true, up;
    volatile Socket sock;
    Thread thread;
    int pingsOk, pingsFailed, connects, connectFails;
    long lastRtt = -1, upSince;

    RelayProbe(Sink s) { sink = s; }
    void start() { thread = new Thread(this, "pilotgap-probe"); thread.setDaemon(true); thread.start(); }
    void stop() { run = false; try { if (sock != null) sock.close(); } catch (Exception ignored) { } if (thread != null) thread.interrupt(); }

    /** Counts since the last call, for the once-a-minute line. */
    synchronized String summary() {
        String out = (up ? "up " + ((System.currentTimeMillis() - upSince) / 1000) + " s" : "down")
            + ", pings " + pingsOk + " ok " + pingsFailed + " failed"
            + (lastRtt >= 0 ? ", last " + lastRtt + " ms" : "")
            + ", connects " + connects + " ok " + connectFails + " failed";
        pingsOk = 0; pingsFailed = 0; connects = 0; connectFails = 0;
        return out;
    }

    @Override public void run() {
        Random rnd = new Random();
        while (run) {
            try {
                SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
                sock = s;
                s.connect(new InetSocketAddress(HOST, PORT), TIMEOUT_MS);
                s.setSoTimeout(TIMEOUT_MS);
                s.startHandshake();
                OutputStream o = s.getOutputStream();
                InputStream in = s.getInputStream();
                byte[] id = ("pgprobe_" + Integer.toHexString(rnd.nextInt())).getBytes(StandardCharsets.UTF_8);
                // MQTT 3.1.1 CONNECT: "MQTT", level 4, clean session, keepalive
                byte[] vh = {0, 4, 'M', 'Q', 'T', 'T', 4, 2, 0, (byte) KEEPALIVE_S};
                int rem = vh.length + 2 + id.length;
                o.write(0x10); o.write(rem); o.write(vh); o.write(id.length >> 8); o.write(id.length & 0xff); o.write(id); o.flush();
                if (in.read() != 0x20 || in.read() != 2) throw new Exception("bad reply");
                in.read(); if (in.read() != 0) throw new Exception("refused");
                synchronized (this) { up = true; upSince = System.currentTimeMillis(); connects++; }
                sink.log("connected");
                while (run) {
                    Thread.sleep(PING_MS);
                    long t0 = System.currentTimeMillis();
                    o.write(0xC0); o.write(0); o.flush();
                    if (in.read() != 0xD0 || in.read() != 0) throw new Exception("no ping reply");
                    synchronized (this) { pingsOk++; lastRtt = System.currentTimeMillis() - t0; }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                boolean wasUp;
                synchronized (this) { wasUp = up; if (up) pingsFailed++; else connectFails++; up = false; }
                if (wasUp) sink.log("connection lost (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            } finally {
                try { if (sock != null) sock.close(); } catch (Exception ignored) { }
            }
            try { Thread.sleep(RETRY_MS); } catch (InterruptedException e) { break; }
        }
    }
}
