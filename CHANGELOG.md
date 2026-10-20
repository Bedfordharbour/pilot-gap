# Changelog

The version shows at the bottom of the start screen and in Settings. Newest first.

## 1.6.0 — 20 Oct 2026 (page) and Android app 1.1.0

Ryan, 20 Oct 08:48: "4a fix. 4c go."
- **The app carries the relay connection (app 1.1.0).** Field test 20 Oct 08:26–08:36: a connection made by the app stayed up for 10 minutes behind other apps, while the page's own connection died about 100 s into every background spell. Now the page hands its messages (still scrambled by the page) to the app, which keeps one connection to the relay (secure WebSocket, IPv4 first), pings it every 15 s, and reconnects by itself (after 2, 5, 10, then every 15 s). Browser phones and older apps keep the page's own connection.
- **Start screen no longer wipes the stored log** (4c). A page reloaded by the browser and then updated saved an empty log over the stored one, and the next upload replaced the Drive file (Lawrie, 20 Oct: 05:49–07:44 lost from Drive).
- Found by an independent review and fixed before release:
  - a late "stop" from an old trip could kill the new trip's relay with no reconnect; each relay now has its own id, and the page restarts the app's relay if it is not running for 60 s
  - a rare crash in the once-a-minute diagnostics
  - the "clear my position" message on End trip is now sent before tracking stops
  - safer passing of text to the page
  - sockets closed on a failed connect
  - a refused subscription is now noticed
- Smoke test: 107 checks. App relay tested against a local relay (retained and live messages, long messages, reconnect after a relay restart). Not yet tested against the public relay on a phone.

## 1.5.9 — 20 Oct 2026 (page: app notice only) and Android app 1.0.7

- **App 1.0.7: relay test connection tries the relay's IPv4 address first.** On the road (20 Oct 07:48–08:00, app 1.0.6) every attempt went to the relay's IPv6 address and was refused ("ECONNREFUSED", port 8884), while the page connected normally. It now tries IPv4 addresses first, then IPv6, and logs which address worked. Tested against a local relay.
- Older apps are told about app 1.0.7.

## 1.5.8 — 20 Oct 2026 (page) and Android app 1.0.6

Ryan, 20 Oct 07:19: "3b. 3c new log file. A trip is reset every day."
- **New log file each day and when full.** The finished file is sent whole and kept in Drive; the trip carries on in a new file ("…-p2" if in the same minute). Before, the log was trimmed at 20,000 lines and each upload replaced the Drive copy, so the first hours of a long trip were lost (19 Oct, Truck 1). A trip started on a new day always starts a new file.
- **App 1.0.6: the relay test connection works.** 1.0.5's test never connected: it did not tell the relay server its name, and logged no reason. It now uses the same route as the page (secure WebSocket to the relay, port 8884), checks the server certificate, and logs why it failed. Tested against a local relay; not yet tested against the public relay.
- Older apps are told about app 1.0.6 (Download app update).
- Smoke test: 101 checks.

## 1.5.7 — 19 Oct 2026 (page only)

- **App update notice with tap to download** (Ryan, 19 Oct 22:16). The app shows the website page, so the page updates by itself but the app does not. Both phones ran app 1.0.3 all afternoon after 1.0.5 was out. Now, inside an older app, the start screen shows "New app version … Download app update". The link opens in the phone's browser, which downloads the file; then tap Install. The page also learns about a newer app from the website's update check.
- The version line shows the app version too, e.g. "1.5.7 · App 1.0.3".
- Smoke test: 100 checks.

## 1.5.6 — 19 Oct 2026 (page) and Android app 1.0.5

Ryan 4b and 4c (19 Oct 17:07 Eucla time), from the four logs of the Norseman–Caiguna run.
- **Gaps allow for the age of each position.** Both vehicles' last positions are moved forward along their heading by speed × age before the gap is measured. In the logs, positions 15–30 s old shown as live were out by about 300–365 m, and gave 5 false "too far" alarms. A vehicle under 2 m/s (7 km/h) is treated as stopped.
- **When contact is lost, the big number is the estimate,** marked "est.". The app's estimate was within about 20 m in the logs, while the raw distance to the last known spot was 300 m to several km out. After 2 minutes it shows "too long to estimate, last known …".
- **Network positions are never used** for gaps or alarms. In the logs they were about 10 km out (median 9.6 km) while claiming ±100 m, and set off false "too far" alarms on both phones. App 1.0.5 no longer asks for them; the page also ignores them from older apps.
- **Battery saver warning (app 1.0.5).** If battery saver is set to stop GPS when the screen is off, a banner tells the driver to turn off Power saving. The log records the mode at start, on every change and every minute.
- **Test build, relay probe (app 1.0.5).** The background service keeps its own small connection to the relay (Android's own code, not the page) and pings it every 20 s. The once-a-minute line shows whether it stayed up. If it survives in the background while the page's connection dies, the relay should move into the service; if it also dies, the phone is blocking background network.
- Relay retries in the background slowed to once a minute (593 failed attempts in one log); on return to the app it reconnects straight away.
- Every log line now shows the phone's time zone (one phone was on Perth time, one on Eucla time, 45 min apart).
- Smoke test: 97 checks.

## 1.5.5 — 19 Oct 2026 (page) and Android app 1.0.4

Extra logging for the accuracy tests (Ryan 7a, 19 Oct 08:53). Nothing on screen changes.
- **Track line every 2 s** (`trk`): this phone's position, GPS accuracy, speed, heading, fix age, GPS or network, and the gaps shown at that moment. After a trip, the true gap at any second can be worked out from two phones' logs and compared with what was shown.
- **30 s sample adds**, for each other vehicle, its GPS accuracy and its delay ("lag": arrival time minus send time; it includes any difference between the two phones' clocks). Also the relay round trip (phone → relay → phone, one clock): count, lowest, average and highest in ms.
- **App 1.0.4, once-a-minute line adds** satellites (used in the fix, of those seen) and mobile signal (bars 0–4 and dBm).
- Log size: kept on the phone raised to 20,000 lines (about 11 h). An 8 h trip log is about 1.5–2 MB (estimate), and the 10-minute upload sends the whole log, so mobile data use grows through the day.
- Smoke test: 93 checks.

## 1.5.4 — 19 Oct 2026 (page) and Android app 1.0.3

Ryan's decisions after the road test (19 Oct 06:00).
- **Voice once per event.** The screen shows alarms in red, so a gap going out of range is spoken once, with one double beep and vibration. It is spoken again only after the gap has been back in range for 10 s, or if it switches between too close and too far. "Lost contact" is spoken once for every dropout; the old once-a-minute limit could hide a second dropout.
- **Keep screen on works in the app (app 1.0.3).** The setting now uses the phone's own keep-screen-on, because the browser method does not work inside the app and the phone locked. The status row shows "Screen on (app)".
- **More diagnostics in the trip log:**
  - Every 30 s sample adds the relay state, seconds since a message was last received and sent, message counts, the phone's network as the browser sees it and, in the app, as Android sees it.
  - Leaving and coming back to the page logs the same.
  - Network lost, back or changed is logged.
  - App 1.0.3 adds a line every minute with GPS and network fix counts, the last GPS fix, screen on or off, battery saver, battery exemption, doze and network.
- Smoke test: 91 checks.

## 1.5.3 — 19 Oct 2026 (page only; app stays 1.0.2)

Fault fixes from the first four-phone road test (19 Oct 04:47–05:31: Truck 1 in the app, lead in Samsung Internet, rear on iPhone, Truck 2 in Chrome).
- **Dead relay connection noticed.** In the app's background the connection died at about 05:08 and nothing noticed until 05:23. Now, if nothing at all arrives for 45 s (not even this phone's own messages), the connection is replaced.
- **Positions carry the time they were measured.** An old GPS fix was being sent as new, giving false gaps (165–513 m when the real gap was about 540 m, and "Lead back at 594 m" from a 60 s old position).
- **Late messages show their true age.** A backlog delivered when a phone woke counted as live (rear pilot: "Rear gap too close, 27 m" when the gap was about 900 m). Age is now worked out from the sender's times, corrected by the smallest delay seen from that phone, so a wrong phone clock still works.
- **No "Lost contact" for a vehicle not seen live on this trip** (false warnings at every start from stored positions 1–9 min old).
- **A vehicle not heard from for 10 minutes is removed from the screen** (a rear gap of 16,104 m from a 25 min old position stayed on Truck 1).
- 10-minute uploads are now logged when they succeed.
- Smoke test: 88 checks.

## 1.5.2 — 19 Oct 2026 (page) and Android app 1.0.2

- **Trip restarts by itself in the app (Ryan, decision 3e-a, 19 Oct 04:23).** If the trip stopped without End trip less than 10 minutes ago (Android closed the page, the app was swiped away or killed), the next time the page loads it starts the same trip again with the same role and code, says "Pilot Gap restarted the trip", and logs it. End trip, Change role and Update now do not restart. Browser: no automatic restart.
- App 1.0.2: when Android closes the page's engine, background tracking keeps running and the page is rebuilt, instead of stopping with a notification. Twice within a minute: stops with the "Pilot Gap stopped" notification as before.
- Network-position limit stays at 100 m (Ryan, decision 3d, 19 Oct 04:23).
- Smoke test: 84 checks.

## 1.5.1 — 19 Oct 2026 (page) and Android app 1.0.1

Fault fixes from three review rounds and the first real-phone app log (Samsung, Truck 2, 18 Oct 22:29–22:46). No design changes.

- **Default trip code is now 234567** (Ryan, 19 Oct 03:11; was 123456). Phones still on 1.5.0 default to 123456, so update every phone.
- **App in the background:** the 10-second re-send of position and gap settings, the 10-minute log upload and the relay reconnect now run from the app's once-a-second tick. Before, they stopped until the driver came back to the app (seen in the log: no upload for 17 min, sent 3 s after returning).
- **Old relay messages ignored:** a stored position or gap setting more than 10 minutes old is from an earlier trip and is ignored (logged). Before, yesterday's Truck 1 settings and a lead pilot position 49 min old were used at the start of a trip, and a left-over Truck 2 could switch every phone to two trucks and stop the rear-gap alarm. A Truck 2 not heard from for 10 minutes no longer forces two-truck mode.
- **Change role and Update now clear this phone's position from the relay**, so no "ghost" vehicle is left behind. Update now also ends the trip properly (stops the app's tracking, sends the log).
- **Trip logs not lost:** a log still waiting to send is kept (up to 3) when a new log starts, and sent when signal returns. An upload asked for while another is in flight is sent again afterwards, so End trip always sends the last lines. Uploads give up after 30 s and try again later, instead of blocking all later uploads.
- A trip that ends without End trip (Change role, app closed, flat phone) keeps its log as waiting to send, so the last lines are not lost. The start screen retries waiting logs every 2 minutes.
- Relay: a connection with no activity for 30 s is replaced with a fresh one; late messages from an old connection are ignored; a quick End trip and Start again can't leave two connections fighting.
- Practice mode no longer leaves "PRACTICE" in the trip code after a reload.
- **App 1.0.1:** uses the phone's mobile-network position when GPS has had no fix for 10 s (GPS dropped for 1–2 min in the log), only if it is within 100 m, and no speed is worked out from it. The double alarm beep is timed by the app. Bold text setting no longer ends the trip. Shows "Pilot Gap stopped" when the app is swiped away, and restarts the page (with the warning) if Android closes the page's engine, where 1.0.0 crashed silently. Correction to 1.5.0: the warning cannot appear if Android kills the whole app. After opening with no signal, the app goes back to normal loading so uploads and update checks work. Changing font size, display size or language no longer ends the trip. If only approximate location is allowed, it says to turn on "Use precise location".
- Smoke test: 79 checks.

## 1.5.0 — 18 Oct 2026 (page) and Android app 1.0.0

- **Android app 1.0.0** (`app/PilotGap.apk`, source in `android/`). A thin shell around this same page, so screens and gap logic stay one set of code. A background service (like DriveCheck's `DriveService`) keeps GPS, the relay, alarms and the log running while the driver is in Maps or on a call, with a permanent "Pilot Gap is tracking" notification. Alarms are spoken by Android's own voice, with beep and vibration. Back keeps the trip running; End trip stops it. If Android or the driver closes the app mid-trip, a loud notification says "Pilot Gap stopped". Asks once to be left out of battery saving.
- Page: uses the app's GPS, voice, beep, vibration and log saving when running inside the app; shows "App" in the status row; logs "running in the Android app". Browser users unchanged.
- Page: Android browsers get a "Download the app" box on the start screen.
- Found in testing and fixed before release: inside the app, starting background tracking replaced the relay set-up instead of running alongside it, so the app would never have connected.
- Smoke test: 67 checks (in-app checks use a stand-in for the app).

## 1.4.2 — 18 Oct 2026

- The browser is told not to refill the gap boxes and tick boxes after a refresh (`autocomplete="off"`), so old values or a Practice mode tick can't come back on their own. Precaution after the 500–2000 m truck gap found on phone unw5l7 (cause not found; see DEVLOG).

## 1.4.1 — 18 Oct 2026

- Automatic log upload switched on: `UPLOAD_URL` set to Ryan's Google Apps Script web app (deployed 18 Oct 21:36). Logs land in the **Pilot Gap logs** folder in Ryan's Google Drive.

## 1.4.0 — 18 Oct 2026

- **Location agreement** before the first real trip on each phone: the driver types their name and taps I agree. Wording includes "I agree to share my location as part of my piloting / driver duties". Name, time and wording version go into every trip log. Asked again if the wording changes. Not needed for practice mode.
- **Positions in the trip log (2a):** every log line written during a trip ends with this phone's position (`@lat,lon`), including the 30-second samples.
- **Automatic log upload (1b):** at End trip and every 10 minutes while connected to the relay, the whole log is sent to a Google Apps Script in Ryan's Google Drive (`server/upload-log.gs`), which keeps one file per trip and replaces it with each newer copy. If it can't send, it tries again when signal returns or the page is next opened. Status shown in Settings and on the start screen. Practice trips are not sent. **Off until the script address is put into `UPLOAD_URL`.**
- Practice and real trips keep separate logs; a new log starts when the role or driver changes (found in testing: a real trip had continued a practice log).
- Smoke test: 57 checks.

## 1.3.0 — 18 Oct 2026

- **Update check (2a).** The page looks for a newer version on the website when it opens, at Start and every 10 minutes. If there is one, it shows "New version … Update now" on the start screen and as a banner during a trip. It never reloads by itself; updating mid-trip restarts the page and the driver taps Start again. Logged.
- Trip codes back to **4 or more** letters or numbers (Ryan declined the 6-character minimum for now; ask again once out of testing).
- Smoke test: 47 checks.

## 1.2.1 — 18 Oct 2026

- Fix: a phone whose clock is wrong no longer shows as "lost" on the others. Live messages are now timed by when they arrive (found in audit).
- Notes tidied (stale 1234 and v1 channel lines).
- Backups: each version is kept on its own branch, `release/v1.0.0`, `release/v1.1.0`, `release/v1.2.0`, `release/v1.2.1`.

## 1.2.0 — 18 Oct 2026

- **Trip log on each phone.** Records relay connects and drops, GPS errors and dropouts, each gap going in or out of range (with distance), lost and regained contact, alarms, mute, gap settings, the phone switching away (another app, call, screen locked), keep-awake, script errors, and a sample of all gaps every 30 s. Kept on the phone (up to 6,000 entries; a new trip within 6 h continues the same log).
- **Save trip log**: offered on the start screen after End trip, kept there after closing the page, and in Settings during a trip. Uses the phone's share screen (for example to Dropbox) where it can, otherwise downloads a .txt file.
- **Messages scrambled with the trip code** (AES-GCM, key from the code). The relay channel name is a hash of the code, so the code is never sent. Phones without the code see only scrambled text. Plain or wrongly scrambled messages are ignored and logged.
- Trip codes must be at least 6 letters or numbers. **Default code 123456** for testing where privacy does not matter; New code for real trips.
- **Not compatible with 1.0–1.1 phones**: they use the old channel and will not see 1.2 phones. Check the version at the bottom of the start screen on every phone.
- Smoke test extended to 41 checks.

## 1.1.0 — 18 Oct 2026

- Second truck: new role **Truck 2**. With two trucks there are three gaps: front (lead pilot to Truck 1), between trucks (Truck 1 to Truck 2), and rear (last truck to rear pilot).
- Separate range for each gap. Defaults: front 500–2000 m, between trucks 0–100 m, rear 100–1000 m.
- **Truck 1 sets the gaps and the number of trucks for the whole trip.** The other phones pick them up through the relay and show "Set by Truck 1 at hh:mm". Pilots can see the gaps but not change them.
- Each phone shows its own gaps large and the others small; speeds for all vehicles.
- Voice alerts now say "Front gap", "Truck gap" or "Rear gap".
- The role check also expects Truck 2 to be behind Truck 1.
- Smoke test extended to 29 checks, covering one-truck and two-truck layouts and settings passed from Truck 1.

## 1.0.0 — 18 Oct 2026

First version for the field trial on 19 Oct 2026. Built up through these commits:

| Commit | Change |
|---|---|
| `0ca8987` | Version number shown on the page; README, CHANGELOG, DEVLOG, TESTING and an automated smoke test added |
| `4720625` | Relay library stored in the repo instead of loaded from unpkg.com |
| `fe6b45f` | Fix: Settings panel was always visible, so the Settings button seemed to do nothing |
| `c329ae7` | Mute is now on/off and lasts 5 min (was 1 min); bigger Truck label with a truck symbol |
| `fc64688` | Screen setting split into Keep screen on (wake lock) and Force screen on (blank video) |
| `26d41ff` | Settings button scrolls the panel into view and changes to "Close settings" |
| `5b0f56d` | Keep screen on added as a setting, off by default, with a status light |
| `2bf6d57` | Practice mode: pretend vehicles at realistic speeds (was up to 152 km/h) |
| `06cf240` | Default trip code 1234; New code still makes a random one |
| `a6cf37f` | First page: roles, trip code, live gaps, alerts, last-seen and estimate, role check, practice mode |
