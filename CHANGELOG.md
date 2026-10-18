# Changelog

The version shows at the bottom of the start screen and in Settings. Newest first.

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
