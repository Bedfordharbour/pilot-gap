package au.bhe.pilotgap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
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
    long lastFixAt;

    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            MainActivity a = MainActivity.current.get();
            if (a == null) { appClosed(TripService.this); return; }
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
        handler.postDelayed(tick, 1000);
        sendEvent("service");
        return START_NOT_STICKY;
    }

    @Override public void onLocationChanged(Location l) {
        lastFixAt = System.currentTimeMillis();
        MainActivity a = MainActivity.current.get();
        if (a != null) a.deliverFix(l);
    }
    // Implemented explicitly: on Android 10 and older these are not default methods.
    @Override public void onStatusChanged(String p, int s, Bundle b) { }
    @Override public void onProviderEnabled(String p) { sendEvent("gpson"); }
    @Override public void onProviderDisabled(String p) { sendEvent("gpsoff"); }

    void sendEvent(String what) {
        MainActivity a = MainActivity.current.get();
        if (a != null) a.event(what);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        if (lm != null) try { lm.removeUpdates(this); } catch (Exception ignored) { }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    /** The app screen was closed (swiped away or killed) during a trip: stop and say so loudly. */
    static void appClosed(Context c) {
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
