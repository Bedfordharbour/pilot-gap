package au.bhe.pilotgap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.Location;
import android.telephony.CellSignalStrength;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Runs while a trip is on (started by Start on the page, stopped by End trip).
 * Same idea as DriveCheck's DriveService: a foreground service with a permanent notification,
 * so Android keeps the app alive. It feeds GPS fixes to the page and drives the page once a
 * second, so gaps, the relay, alarms and the log keep working behind other apps.
 */
public class TripService extends Service implements LocationListener {
    static volatile boolean running;
    static final String CH_TRACK = "tracking", CH_ALERT = "alerts";
    static final int ID_TRACK = 1, ID_ALERT = 2;

    PowerManager.WakeLock wakeLock;
    LocationManager lm;
    final Handler handler = new Handler(Looper.getMainLooper());
    long lastFixAt, lastGpsAt;
    int missedTicks;
    int gpsFixes, netFixes, ticks;
    ConnectivityManager.NetworkCallback netCb;
    GnssStatus.Callback gnssCb;
    volatile int satsUsed = -1, satsSeen = -1;   // from the GPS chip (accuracy test 5e, Ryan 19 Oct)

    /** Mobile signal: bars 0-4 and dBm where Android gives them. */
    String signalInfo() {
        try {
            if (Build.VERSION.SDK_INT < 28) return "n/a";
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            SignalStrength ss = tm == null ? null : tm.getSignalStrength();
            if (ss == null) return "unknown";
            String out = "bars " + ss.getLevel() + "/4";
            if (Build.VERSION.SDK_INT >= 29) {
                for (CellSignalStrength c : ss.getCellSignalStrengths()) {
                    int dbm = c.getDbm();
                    if (dbm != Integer.MAX_VALUE) { out += " " + dbm + " dBm (" + c.getClass().getSimpleName().replace("CellSignalStrength", "") + ")"; break; }
                }
            }
            return out;
        } catch (Exception e) { return "?"; }
    }
    static final long DIAG_MS = 60000;
    BroadcastReceiver saverRx;

    /** Battery saver's effect on location (Android: getLocationPowerSaveMode), in words. */
    String locSaver() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isPowerSaveMode()) return "off";
            if (Build.VERSION.SDK_INT < 28) return "on";
            switch (pm.getLocationPowerSaveMode()) {
                case 0: return "on, location not limited";
                case 1: return "on, GPS off when screen off";
                case 2: return "on, all location off when screen off";
                case 3: return "on, location for visible apps only";
                case 4: return "on, location slowed when screen off";
                default: return "on, mode " + pm.getLocationPowerSaveMode();
            }
        } catch (Exception e) { return "?"; }
    }
    void reportSaver() {
        String m = locSaver();
        boolean limits = m.startsWith("on,") && !m.contains("not limited");
        sendEvent(limits ? "locsaver_on: battery saver " + m : "locsaver_off");
        sendEvent("power: battery saver " + m);
    }

    /** Once a minute: what the phone is doing, written to the trip log (Ryan, 19 Oct: more diagnostics). */
    final Runnable diag = new Runnable() {
        @Override public void run() {
            if (!running) return;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            long now = System.currentTimeMillis();
            sendEvent("diag: last minute " + gpsFixes + " GPS fixes, " + netFixes + " network fixes, " + ticks + " ticks"
                + "; last GPS " + (lastGpsAt == 0 ? "never" : ((now - lastGpsAt) / 1000) + " s ago")
                + "; screen " + (pm.isInteractive() ? "on" : "off")
                + "; battery saver " + (pm.isPowerSaveMode() ? "ON" : "off")
                + "; battery exempt " + (pm.isIgnoringBatteryOptimizations(getPackageName()) ? "yes" : "NO")
                + (Build.VERSION.SDK_INT >= 23 ? "; doze " + (pm.isDeviceIdleMode() ? "ON" : "off") : "")
 + "; location saver " + locSaver()
                + "; app relay " + relaySummary()
                + "; satellites " + (satsUsed < 0 ? "?" : satsUsed + " used of " + satsSeen + " seen")
                + "; signal " + signalInfo()
                + "; net " + netInfo(TripService.this));
            gpsFixes = 0; netFixes = 0; ticks = 0;
            handler.postDelayed(this, DIAG_MS);
        }
    };

    static String relaySummary() { NativeRelay r = MainActivity.relay; return r == null ? "not used" : r.summary(); }

    static String netInfo(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return "none";
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null) return "unknown";
            String t = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "mobile" : "other";
            return t + (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? " internet ok" : " NO internet");
        } catch (Exception e) { return "?"; }
    }
    static final long NET_AFTER_MS = 10000;
    static final float NET_MAX_ACC_M = 100f;   // only use a network position this good (Wi-Fi based, typically); for Ryan to confirm   // use a mobile-network position only when GPS has been quiet this long

    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            MainActivity a = MainActivity.current.get();
            if (a == null) {   // screen gone: allow a few seconds for it to be rebuilt (page restart) before calling it closed
                if (++missedTicks >= 10) { appClosed(TripService.this); return; }
                handler.postDelayed(this, 1000); return;
            }
            missedTicks = 0; ticks++;
            a.tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        channels(this);
        Notification n = notification(this, CH_TRACK, "Pilot Gap is tracking",
            "Gaps and alarms keep working when you switch apps. Tap to open. Stop with End trip.", true);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID_TRACK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(ID_TRACK, n);
        } catch (RuntimeException e) {          // e.g. location permission missing
            sendEvent("servicefailed");
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pilotgap:trip");
        wakeLock.acquire(14 * 3600 * 1000L);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException e) {
            sendEvent("nogps");
        }
        // Network positions are no longer used (trial 19 Oct: ~10 km out while claiming 100 m).
        handler.postDelayed(tick, 1000);
        handler.postDelayed(diag, DIAG_MS);
        reportSaver();
        try {
            saverRx = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { reportSaver(); } };
            registerReceiver(saverRx, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        } catch (Exception ignored) { }

        try {
            gnssCb = new GnssStatus.Callback() {
                @Override public void onSatelliteStatusChanged(GnssStatus st) {
                    int used = 0, n = st.getSatelliteCount();
                    for (int i = 0; i < n; i++) if (st.usedInFix(i)) used++;
                    satsUsed = used; satsSeen = n;
                }
            };
            lm.registerGnssStatusCallback(gnssCb, handler);
        } catch (SecurityException | IllegalArgumentException ignored) { }
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { handler.post(() -> sendEvent("net: network available (" + netInfo(TripService.this) + ")")); }
                @Override public void onLost(Network n) { handler.post(() -> sendEvent("net: network lost")); }
            };
            cm.registerDefaultNetworkCallback(netCb);
        } catch (Exception ignored) { }
        sendEvent("service");
        return START_NOT_STICKY;
    }

    @Override public void onLocationChanged(Location l) {
        long now = System.currentTimeMillis();
        if (LocationManager.GPS_PROVIDER.equals(l.getProvider())) { lastGpsAt = now; gpsFixes++; }
        else return;   // only GPS positions are used (network positions are no longer requested, 1.0.5)
        if (!LocationManager.GPS_PROVIDER.equals(l.getProvider())) netFixes++;
        lastFixAt = now;
        MainActivity a = MainActivity.current.get();
        if (a != null) a.deliverFix(l);
    }
    // Implemented explicitly: on Android 10 and older these are not default methods.
    @Override public void onStatusChanged(String p, int s, Bundle b) { }
    @Override public void onProviderEnabled(String p) { if (LocationManager.GPS_PROVIDER.equals(p)) sendEvent("gpson"); }
    @Override public void onProviderDisabled(String p) { if (LocationManager.GPS_PROVIDER.equals(p)) sendEvent("gpsoff"); }

    /** The app was swiped away from the recent-apps list during a trip. */
    @Override public void onTaskRemoved(Intent root) {
        if (running) appClosed(this);
        super.onTaskRemoved(root);
    }

    void sendEvent(String what) {
        MainActivity a = MainActivity.current.get();
        if (a != null) a.event(what);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(diag);
        if (MainActivity.current.get() == null) MainActivity.closeRelayClearing();   // app closed: tell the others, close the relay (otherwise the page ends it)
        if (saverRx != null) try { unregisterReceiver(saverRx); } catch (Exception ignored) { }
        if (gnssCb != null && lm != null) try { lm.unregisterGnssStatusCallback(gnssCb); } catch (Exception ignored) { }
        if (netCb != null) try { ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCb); } catch (Exception ignored) { }
        if (lm != null) try { lm.removeUpdates(this); } catch (Exception ignored) { }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    /** The app screen was closed (swiped away or killed) during a trip: stop and say so loudly. */
    static void appClosed(Context c) {
        MainActivity.closeRelayClearing();
        c.stopService(new Intent(c, TripService.class));
        channels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        nm.notify(ID_ALERT, notification(c, CH_ALERT, "Pilot Gap stopped",
            "The app was closed during a trip, so other vehicles can't see you. Tap to open it and press Start.", false));
    }

    static void channels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_TRACK, "Trip running", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_ALERT, "Pilot Gap stopped", NotificationManager.IMPORTANCE_HIGH));
    }

    static Notification notification(Context c, String ch, String title, String text, boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(c, 0,
            new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, ch)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build();
    }
}
