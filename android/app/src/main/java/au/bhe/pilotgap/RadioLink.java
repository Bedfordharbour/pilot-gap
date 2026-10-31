package au.bhe.pilotgap;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * App 1.3.0 (Ryan 1b, 31 Oct 2026): Bluetooth link to the Meshtastic radio unit in this vehicle.
 * Speaks the Meshtastic phone protocol (the same one the Meshtastic app uses): write ToRadio,
 * read FromRadio until empty, read again when FromNum notifies. Only what Pilot Gap needs is
 * decoded: this unit's own node number, positions and text messages heard from other units.
 * The unit decrypts the private channel itself; this code never sees the channel key.
 * Pairing: the unit asks for its fixed PIN the first time; Android shows its own PIN box.
 */
@SuppressLint("MissingPermission")   // callers check BLUETOOTH_CONNECT first (MainActivity.radioAllowed)
class RadioLink {
    static final UUID SVC  = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd");
    static final UUID TO   = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7");
    static final UUID FROM = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002");
    static final UUID NUM  = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453");
    static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    static final int[] RETRY_MS = {3000, 5000, 10000, 20000, 30000};

    interface Listener {
        void state(String state, String unit);   // connecting, pairing, connected, off, nobluetooth, nopermission
        void myNode(String node);
        void position(String node, String json);
        void text(String node, String json);
    }

    final Context ctx;
    final Listener out;
    final String address, unit;
    final HandlerThread thread = new HandlerThread("radio");
    final Handler h;
    final ArrayDeque<byte[]> writes = new ArrayDeque<>();
    volatile boolean want;
    BluetoothGatt gatt;
    BluetoothGattCharacteristic toChar, fromChar;
    boolean busy, readPending, ready;
    int retry;
    long myNum = -1;
    BroadcastReceiver bondRx;
    // counters for the diagnostics line
    volatile int rxPackets, rxPositions, rxTexts, txTexts, connects;

    RadioLink(Context c, String address, String unit, Listener out) {
        this.ctx = c.getApplicationContext(); this.address = address; this.unit = unit; this.out = out;
        thread.start(); h = new Handler(thread.getLooper());
    }

    void start() {
        want = true;
        bondRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d == null || !address.equalsIgnoreCase(d.getAddress())) return;
                int st = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                if (st == BluetoothDevice.BOND_BONDING) out.state("pairing", unit);
                if (st == BluetoothDevice.BOND_BONDED) h.post(() -> { close(); retry = 0; connect(); });   // start clean on the paired link
            }
        };
        ctx.registerReceiver(bondRx, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        h.post(this::connect);
    }

    void stop() {
        want = false;
        try { ctx.unregisterReceiver(bondRx); } catch (Exception ignored) { }
        h.post(() -> { close(); out.state("off", unit); thread.quitSafely(); });
    }

    /** Send a short text on the trip channel (the role announcement). False if not connected. */
    boolean sendText(String text) {
        if (!ready) return false;
        byte[] msg = MeshProto.toRadioText(text, new Random().nextInt() & 0x7fffffff);
        h.post(() -> { writes.add(msg); next(); });
        txTexts++;
        return true;
    }

    String summary() {
        return String.format(Locale.US, "radio %s: link %s, connects %d, in %d (pos %d, text %d), out %d",
            unit, ready ? "up" : "down", connects, rxPackets, rxPositions, rxTexts, txTexts);
    }

    // ---- connection (all on the radio thread) ----
    void connect() {
        if (!want) return;
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled()) { out.state("nobluetooth", unit); later(); return; }
        out.state("connecting", unit);
        try {
            BluetoothDevice dev = ad.getRemoteDevice(address);
            gatt = dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE);
        } catch (Exception e) { close(); later(); }
    }

    void later() {
        if (!want) return;
        int ms = RETRY_MS[Math.min(retry, RETRY_MS.length - 1)]; retry++;
        h.postDelayed(() -> { if (want && gatt == null) connect(); }, ms);
    }

    void close() {
        ready = false; busy = false; readPending = false; writes.clear(); toChar = null; fromChar = null;
        BluetoothGatt g = gatt; gatt = null;
        if (g != null) { try { g.disconnect(); } catch (Exception ignored) { } try { g.close(); } catch (Exception ignored) { } }
    }

    void lost(String why) {
        boolean was = ready;
        close();
        if (want) { out.state("connecting", unit); if (was) retry = 0; later(); }
    }

    /** One GATT operation at a time: pending writes first, then a FromRadio read. */
    void next() {
        BluetoothGatt g = gatt;
        if (busy || g == null || toChar == null) return;
        if (!writes.isEmpty()) {
            byte[] v = writes.poll();
            toChar.setValue(v);
            toChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            busy = g.writeCharacteristic(toChar);
            if (!busy) lost("write refused");
            return;
        }
        if (readPending && fromChar != null) {
            readPending = false;
            busy = g.readCharacteristic(fromChar);
            if (!busy) lost("read refused");
        }
    }

    static boolean authFail(int status) {
        return status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION || status == 15 /* insufficient encryption */ || status == 137;
    }

    final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int st) {
            h.post(() -> {
                if (g != gatt) { try { g.close(); } catch (Exception ignored) { } return; }
                if (st == BluetoothProfile.STATE_CONNECTED) { connects++; if (!g.requestMtu(512)) g.discoverServices(); }
                else if (st == BluetoothProfile.STATE_DISCONNECTED) lost("disconnected " + status);
            });
        }
        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) { h.post(() -> { if (g == gatt) g.discoverServices(); }); }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            h.post(() -> {
                if (g != gatt) return;
                BluetoothGattService s = g.getService(SVC);
                if (status != BluetoothGatt.GATT_SUCCESS || s == null) { lost("not a Meshtastic unit"); return; }
                toChar = s.getCharacteristic(TO); fromChar = s.getCharacteristic(FROM);
                BluetoothGattCharacteristic num = s.getCharacteristic(NUM);
                if (toChar == null || fromChar == null || num == null) { lost("Meshtastic service incomplete"); return; }
                g.setCharacteristicNotification(num, true);
                BluetoothGattDescriptor d = num.getDescriptor(CCCD);
                if (d == null) { startConfig(); return; }
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                busy = g.writeDescriptor(d);
                if (!busy) startConfig();
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            h.post(() -> {
                if (g != gatt) return;
                busy = false;
                if (authFail(status)) { out.state("pairing", unit); return; }   // Android asks for the PIN; bond receiver reconnects
                startConfig();
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            h.post(() -> {
                if (g != gatt) return;
                busy = false;
                if (authFail(status)) { out.state("pairing", unit); return; }
                readPending = true; next();
            });
        }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int status) { onRead(g, c.getValue(), status); }   // Android 12 and older
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int status) { onRead(g, value, status); }   // Android 13+
        void onRead(BluetoothGatt g, byte[] value, int status) {
            final byte[] v = value == null ? null : value.clone();
            h.post(() -> {
                if (g != gatt) return;
                busy = false;
                if (authFail(status)) { out.state("pairing", unit); return; }
                if (status != BluetoothGatt.GATT_SUCCESS) { lost("read failed " + status); return; }
                if (!ready) { ready = true; retry = 0; out.state("connected", unit); }
                if (v != null && v.length > 0) { rxPackets++; try { fromRadio(v); } catch (Exception ignored) { } readPending = true; }   // keep reading until empty
                next();
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) { onChanged(g, c); }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) { onChanged(g, c); }
        void onChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            h.post(() -> { if (g == gatt && NUM.equals(c.getUuid())) { readPending = true; next(); } });
        }
    };

    void startConfig() {
        writes.addFirst(MeshProto.toRadioWantConfig(new Random().nextInt() & 0x7fffffff));
        next();
    }

    void fromRadio(byte[] b) {
        MeshProto.Result r = MeshProto.fromRadio(b);
        if (r.myNode != null) { myNum = 0; out.myNode(r.myNode); }
        if (r.kind == MeshProto.POSITION) { rxPositions++; out.position(r.node, r.json); }
        else if (r.kind == MeshProto.TEXT) { rxTexts++; out.text(r.node, r.json); }
    }
}
