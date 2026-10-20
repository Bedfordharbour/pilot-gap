package au.bhe.pilotgap;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.webkit.GeolocationPermissions;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.Toast;

import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Pilot Gap Android app: a thin shell around the live Pilot Gap page, so screens and gap
 * logic stay one set of code with the browser version. What the app adds (see TripService):
 * GPS, the relay and alarms keep running while the driver is in another app or on a call.
 */
public class MainActivity extends Activity {
    static final String PAGE = "https://bedfordharbour.github.io/pilot-gap/";
    static final String HOST = "bedfordharbour.github.io";
    static volatile WeakReference<MainActivity> current = new WeakReference<>(null);
    static volatile NativeRelay relay;   // app 1.1.0: the relay connection lives here, not in the page
    static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    static int relaySeq;
    static void stopRelay() { NativeRelay r = relay; relay = null; if (r != null) new Thread(r::stop).start(); }
    /** Stop only the relay the page started (id), so a late stop from an old trip cannot kill the new one. */
    static synchronized void stopRelay(int id) { NativeRelay r = relay; if (r != null && r.id == id) stopRelay(); }

    /** Pass a relay event to the page (main thread). Payloads are base64 text, topics are [a-z0-9/-]. */
    static void toPage(String js) {
        main.post(() -> { MainActivity a = current.get(); if (a != null && a.web != null) a.web.evaluateJavascript(js, null); });
    }
    static String q(String v) { return org.json.JSONObject.quote(v == null ? "" : v); }   // a JSON string is valid JavaScript

    WebView web;
    TextToSpeech tts;
    volatile boolean ttsReady;
    ToneGenerator tone;
    boolean triedCache, cacheLoadStarted;
    static long lastRendererRestart;
    boolean restartingPage;   // the page engine died and the screen is being rebuilt; the trip carries on
    int speakId;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        current = new WeakReference<>(this);

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " PilotGapApp/" + appVersion());
        // Keep the page running at full priority when the app is behind Maps or a call.
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        web.addJavascriptInterface(new Bridge(), "PilotGapNative");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                if (v.getSettings().getCacheMode() == WebSettings.LOAD_CACHE_ONLY) cacheLoadStarted = true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                // After the saved copy has loaded, go back to normal loading so log upload and update check can use the network.
                // Only after the cache-only load itself has started, not when the failed first load finishes.
                if (cacheLoadStarted && v.getSettings().getCacheMode() == WebSettings.LOAD_CACHE_ONLY) {
                    cacheLoadStarted = false;
                    v.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                // Android closed the page's engine (usually to free memory). Without this the whole app crashes silently.
                // Ryan 3e-a (19 Oct): keep tracking running and rebuild the page; the page restarts the trip by itself.
                if (web == v) { setContentView(new View(MainActivity.this)); web = null; }
                try { v.destroy(); } catch (Exception ignored) { }
                long now = System.currentTimeMillis();
                if (now - lastRendererRestart > 60000) { lastRendererRestart = now; restartingPage = true; recreate(); }   // once a minute at most, so a page that keeps crashing can't loop
                else { if (TripService.running) TripService.appClosed(MainActivity.this); finish(); }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (!r.isForMainFrame()) return;
                if (!triedCache) {           // no signal: use the copy saved last time
                    triedCache = true;
                    v.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);
                    v.loadUrl(PAGE);
                } else {
                    v.loadDataWithBaseURL(null,
                        "<html><body style='font:18px sans-serif;padding:24px;background:#14181c;color:#f1efe8'>"
                        + "<h2>Pilot Gap</h2><p>No signal, and no saved copy of the page yet.</p>"
                        + "<p>Open the app again once the phone has signal.</p>"
                        + "<button style='font-size:20px;padding:12px 20px' onclick='PilotGapNative.reload()'>Try again</button>"
                        + "</body></html>", "text/html", "utf-8", null);
                }
            }
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("en", "AU"));
                ttsReady = true;
            }
        });
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); } catch (RuntimeException ignored) { }

        askPermissions();
        web.loadUrl(PAGE);
    }

    void askPermissions() {
        boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean note = Build.VERSION.SDK_INT < 33
            || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (loc && note) return;
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
    }

    boolean hasApproxOnly() {
        return !hasLocation() && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /** Ask once to be left out of battery saving, so Android does not stop the trip. */
    void askBatteryOnce() {
        SharedPreferences p = getSharedPreferences("pg", MODE_PRIVATE);
        if (p.getBoolean("askedBattery", false)) return;
        p.edit().putBoolean("askedBattery", true).apply();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
        }
    }

    // ---- called by TripService (main thread) ----
    void deliverFix(Location l) {
        String js = String.format(Locale.US,
            "window.__pgNativeFix&&window.__pgNativeFix(%.7f,%.7f,%.1f,%s,%s,%s)",
            l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : 999f,
            l.hasSpeed() ? String.format(Locale.US, "%.2f", l.getSpeed()) : "null",
            l.hasBearing() ? String.format(Locale.US, "%.1f", l.getBearing()) : "null",
            android.location.LocationManager.GPS_PROVIDER.equals(l.getProvider()) ? "false" : "true");   // true = network position: page works out no speed from it
        if (web != null) web.evaluateJavascript(js, null);
    }

    void tick() { if (web != null) web.evaluateJavascript("window.__pgNativeTick&&window.__pgNativeTick()", null); }

    void event(String what) {
        if (web != null) web.evaluateJavascript("window.__pgNativeEvent&&window.__pgNativeEvent('" + what.replace("'", "") + "')", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web == null) return;
        web.onResume();
        web.resumeTimers();
    }

    @Override
    protected void onPause() {
        // Deliberately not pausing the WebView: the page must keep running behind other apps.
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);   // Back keeps the trip running; End trip on the page stops it.
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) current = new WeakReference<>(null);
        if (TripService.running && !restartingPage) TripService.appClosed(this);
        if (tts != null) tts.shutdown();
        if (tone != null) { ToneGenerator t = tone; tone = null; t.release(); }
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** What the page can call. Names are part of the message contract with index.html. */
    class Bridge {
        @JavascriptInterface public String appVersion() { return MainActivity.this.appVersion(); }

        @JavascriptInterface public boolean hasLocation() { return MainActivity.this.hasLocation(); }

        @JavascriptInterface public void tripStart(String role) {
            runOnUiThread(() -> {
                if (!hasLocation()) { askPermissions(); event(hasApproxOnly() ? "approxonly" : "nolocation"); return; }
                Intent i = new Intent(MainActivity.this, TripService.class).putExtra("role", role);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                askBatteryOnce();
            });
        }

        @JavascriptInterface public void tripEnd() {
            runOnUiThread(() -> {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                stopService(new Intent(MainActivity.this, TripService.class));
            });
        }

        /** Keep screen on, from the page's setting (Ryan, 19 Oct: the phone locked in the app). */
        @JavascriptInterface public void keepScreenOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        /** Relay connection run by the app (1.1.0). The page scrambles and unscrambles; this only carries text. */
        @JavascriptInterface public int relayStart(String filter, String clientId) {
            synchronized (MainActivity.class) { stopRelay(); }
            final NativeRelay[] me = new NativeRelay[1];
            me[0] = new NativeRelay(new NativeRelay.Listener() {
                @Override public void onConnected() { if (relay == me[0]) toPage("window.__pgRelay&&window.__pgRelay('connect')"); }
                @Override public void onClosed(String why) { if (relay == me[0]) toPage("window.__pgRelay&&window.__pgRelay('close'," + q(why) + ")"); }
                @Override public void onMessage(String topic, String payload, boolean retained) {
                    if (relay == me[0]) toPage("window.__pgRelay&&window.__pgRelay('msg'," + q(topic) + "," + q(payload) + "," + retained + ")");
                }
            }, filter, clientId + "_" + Integer.toHexString(new java.util.Random().nextInt(0xffff)));
            me[0].id = ++relaySeq;
            relay = me[0];
            me[0].start();
            return me[0].id;
        }
        @JavascriptInterface public boolean relayPublish(String topic, String payload, boolean retain) {
            NativeRelay r = relay; return r != null && r.publish(topic, payload, retain);
        }
        @JavascriptInterface public void relayStop() { stopRelay(); }
        @JavascriptInterface public void relayStopId(int id) { stopRelay(id); }
        @JavascriptInterface public boolean relayUp() { NativeRelay r = relay; return r != null && r.up; }
        @JavascriptInterface public boolean relayRunning() { NativeRelay r = relay; return r != null && r.run; }

        /** Phone network as Android sees it, for the trip log. */
        @JavascriptInterface public String netInfo() { return TripService.netInfo(MainActivity.this); }

        @JavascriptInterface public void say(String text) {
            if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "pg" + (++speakId));
        }

        @JavascriptInterface public void stopSpeech() { if (ttsReady) tts.stop(); }

        @JavascriptInterface public void beep(int ms) {
            if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP2, Math.max(50, Math.min(ms, 1000)));
        }

        /** The alarm's double beep, timed here because page timers are slowed behind other apps. */
        @JavascriptInterface public void beepTwice(int ms) {
            if (tone == null) return;
            int d = Math.max(50, Math.min(ms, 1000));
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, d);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> { ToneGenerator t = tone; if (t != null) t.startTone(ToneGenerator.TONE_PROP_BEEP2, d); }, 280);
        }

        @JavascriptInterface public void vibrate() {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200}, -1));
        }

        /** Save the trip log to Downloads (Android 10+), or share it as text on older phones. */
        @JavascriptInterface public void saveLog(String name, String text) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                        cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                        Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        try (OutputStream o = getContentResolver().openOutputStream(u)) {
                            o.write(text.getBytes(StandardCharsets.UTF_8));
                        }
                        Toast.makeText(MainActivity.this, "Saved to Downloads: " + name, Toast.LENGTH_LONG).show();
                    } else {
                        Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, name)
                            .putExtra(Intent.EXTRA_TEXT, text.length() > 400000 ? text.substring(text.length() - 400000) : text);
                        startActivity(Intent.createChooser(s, "Save trip log"));
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not save the log: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void reload() {
            runOnUiThread(() -> { if (web == null) return; triedCache = false; web.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT); web.loadUrl(PAGE); });
        }
    }
}
