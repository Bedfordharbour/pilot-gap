package au.bhe.pilotgap;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The few Meshtastic protobuf messages Pilot Gap uses, hand-written so the app needs no
 * protobuf library. Field numbers from meshtastic/protobufs mesh.proto (checked against the
 * meshtastic 2.7.11 Python package on 31 Oct 2026). No Android classes: tested on a plain JVM.
 */
final class MeshProto {
    static final int NONE = 0, POSITION = 1, TEXT = 2;
    static final int PORT_TEXT = 1, PORT_POSITION = 3;

    static final class Result { int kind = NONE; String node, json, myNode; }

    static Result fromRadio(byte[] b) {
        Result res = new Result();
        PB r = new PB(b, 0, b.length);
        while (r.more()) {
            int tag = r.tag(), f = tag >>> 3, wt = tag & 7;
            if (f == 2 && wt == 2) packet(r.sub(), res);              // FromRadio.packet
            else if (f == 3 && wt == 2) {                             // FromRadio.my_info
                PB m = r.sub();
                while (m.more()) { int t = m.tag(); if ((t >>> 3) == 1 && (t & 7) == 0) res.myNode = node(m.varint()); else m.skip(t & 7); }
            } else r.skip(wt);
        }
        return res;
    }

    static String node(long n) { return String.format(Locale.US, "!%08x", n & 0xffffffffL); }

    static void packet(PB p, Result res) {
        long from = -1; long rxTime = 0; int hopStart = -1, hopLimit = -1; float snr = Float.NaN; long rssi = 0; boolean hasRssi = false;
        int port = -1; byte[] payload = null;
        while (p.more()) {
            int tag = p.tag(), f = tag >>> 3, wt = tag & 7;
            if (f == 1 && wt == 5) from = p.fixed32() & 0xffffffffL;
            else if (f == 4 && wt == 2) {                             // decoded Data
                PB d = p.sub();
                while (d.more()) {
                    int t = d.tag();
                    if ((t >>> 3) == 1 && (t & 7) == 0) port = (int) d.varint();
                    else if ((t >>> 3) == 2 && (t & 7) == 2) payload = d.bytes();
                    else d.skip(t & 7);
                }
            }
            else if (f == 7 && wt == 5) rxTime = p.fixed32() & 0xffffffffL;
            else if (f == 8 && wt == 5) snr = Float.intBitsToFloat(p.fixed32());
            else if (f == 9 && wt == 0) hopLimit = (int) p.varint();
            else if (f == 12 && wt == 0) { rssi = p.varint(); hasRssi = true; }
            else if (f == 15 && wt == 0) hopStart = (int) p.varint();
            else p.skip(wt);
        }
        if (from < 0 || payload == null) return;
        try {
            JSONObject j = new JSONObject();
            if (!Float.isNaN(snr)) j.put("snr", Math.round(snr * 100) / 100.0);
            if (hasRssi) j.put("rssi", (int) rssi);
            if (rxTime != 0) j.put("rxTime", rxTime);
            if (hopStart >= 0) j.put("hopStart", hopStart);
            if (hopLimit >= 0) j.put("hopLimit", hopLimit);
            if (port == PORT_POSITION) {
                if (!position(payload, j)) return;
                res.kind = POSITION;
            } else if (port == PORT_TEXT) {
                j.put("text", new String(payload, StandardCharsets.UTF_8));
                res.kind = TEXT;
            } else return;
            res.node = node(from); res.json = j.toString();
        } catch (Exception ignored) { }
    }

    static boolean position(byte[] b, JSONObject j) throws Exception {
        PB p = new PB(b, 0, b.length);
        Integer lat = null, lon = null;
        while (p.more()) {
            int tag = p.tag(), f = tag >>> 3, wt = tag & 7;
            if (f == 1 && wt == 5) lat = p.fixed32();                 // sfixed32 latitude_i
            else if (f == 2 && wt == 5) lon = p.fixed32();            // sfixed32 longitude_i
            else if (f == 3 && wt == 0) j.put("alt", (int) p.varint());
            else if (f == 4 && wt == 5) j.put("t", p.fixed32() & 0xffffffffL);
            else if (f == 7 && wt == 5 && !j.has("t")) j.put("t", p.fixed32() & 0xffffffffL);
            else if (f == 15 && wt == 0) j.put("spdRaw", p.varint());  // units not yet proven on these units: logged raw
            else if (f == 16 && wt == 0) j.put("trkRaw", p.varint());
            else if (f == 19 && wt == 0) j.put("sats", p.varint());
            else p.skip(wt);
        }
        if (lat == null || lon == null) return false;
        j.put("lat", lat / 1e7); j.put("lon", lon / 1e7);
        return true;
    }

    static byte[] toRadioWantConfig(int id) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        key(o, 3, 0); varint(o, id & 0xffffffffL);
        return o.toByteArray();
    }

    static byte[] toRadioText(String text, int id) {
        byte[] t = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        key(data, 1, 0); varint(data, PORT_TEXT);
        key(data, 2, 2); varint(data, t.length); data.write(t, 0, t.length);
        byte[] d = data.toByteArray();
        ByteArrayOutputStream pk = new ByteArrayOutputStream();
        key(pk, 2, 5); fixed32(pk, 0xffffffff);                       // to: everyone on the channel
        key(pk, 4, 2); varint(pk, d.length); pk.write(d, 0, d.length);
        key(pk, 6, 5); fixed32(pk, id);
        byte[] p = pk.toByteArray();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        key(o, 1, 2); varint(o, p.length); o.write(p, 0, p.length);
        return o.toByteArray();
    }

    static void key(ByteArrayOutputStream o, int field, int wt) { varint(o, ((long) field << 3) | wt); }
    static void varint(ByteArrayOutputStream o, long v) { while ((v & ~0x7fL) != 0) { o.write((int) ((v & 0x7f) | 0x80)); v >>>= 7; } o.write((int) v); }
    static void fixed32(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >>> 8); o.write(v >>> 16); o.write(v >>> 24); }

    /** Minimal protobuf reader. */
    static class PB {
        final byte[] b; int p; final int end;
        PB(byte[] b, int start, int end) { this.b = b; this.p = start; this.end = end; }
        boolean more() { return p < end; }
        int tag() { return (int) varint(); }
        long varint() {
            long r = 0; int s = 0;
            while (p < end) { int x = b[p++] & 0xff; r |= (long) (x & 0x7f) << s; if ((x & 0x80) == 0) return r; s += 7; if (s > 63) break; }
            throw new IllegalStateException("bad varint");
        }
        int fixed32() {
            if (p + 4 > end) throw new IllegalStateException("short");
            int v = (b[p] & 0xff) | (b[p + 1] & 0xff) << 8 | (b[p + 2] & 0xff) << 16 | (b[p + 3] & 0xff) << 24; p += 4; return v;
        }
        byte[] bytes() { int n = (int) varint(); if (n < 0 || p + n > end) throw new IllegalStateException("short"); byte[] r = new byte[n]; System.arraycopy(b, p, r, 0, n); p += n; return r; }
        PB sub() { int n = (int) varint(); if (n < 0 || p + n > end) throw new IllegalStateException("short"); PB r = new PB(b, p, p + n); p += n; return r; }
        void skip(int wt) {
            if (wt == 0) varint();
            else if (wt == 1) p += 8;
            else if (wt == 2) { int n = (int) varint(); p += n; }
            else if (wt == 5) p += 4;
            else throw new IllegalStateException("wire type " + wt);
            if (p > end) throw new IllegalStateException("short");
        }
    }
}
