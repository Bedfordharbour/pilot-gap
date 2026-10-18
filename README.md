# Pilot Gap

Live distance between the lead pilot, the truck or trucks, and the rear pilot, shown on each driver's phone. Built for Bedford Harbour Engineering oversize moves.

Live page: https://bedfordharbour.github.io/pilot-gap/

## Using it

1. Open the live page on each phone.
2. Tap this phone's role: Lead, Truck 1, Truck 2 (only when there is a second truck) or Rear.
3. First real trip on a phone: the driver types their name and agrees to share their location (asked once per phone).
4. Use the same trip code on every phone (default `234567` for testing; **New code** makes a random one for real trips). At least 4 letters or numbers.
5. On Truck 1 only: set the gaps (defaults front 500–2000 m, between trucks 0–100 m, rear 100–1000 m) and tick **Second truck on this trip** if there is one. The other phones get these from Truck 1. Tap **Start**.
6. Mount the phone, plug it in and leave the page open. Press **End trip** at the end; the log is sent to the office by itself.

Practice mode runs three pretend vehicles, with no GPS or other phones needed.

## How it works

- Each phone reads its own GPS (position, speed and direction) and sends it every 2 seconds to a public relay (HiveMQ's public MQTT relay, `broker.hivemq.com`), under `bhe-pilotgap/v2/<hash of trip code>/<role>`.
- Every message is scrambled with a key made from the trip code (AES-GCM, key by PBKDF2-SHA-256, 150,000 rounds). The code itself is never sent.
- Each phone listens to the others and works out the straight-line gaps: front (lead to Truck 1), between trucks (Truck 1 to Truck 2) and rear (last truck to rear pilot).
- Truck 1 sends the gap ranges and number of trucks on `.../cfg`; the other phones use them.
- Each phone keeps a trip log, with its own position on every line. It is sent automatically to the **Pilot Gap logs** folder in Ryan's Google Drive at End trip and every 10 minutes while connected, and retried when signal returns. **Save trip log** (after End trip, or in Settings) also shares or downloads it as a .txt file.
- Green means in range. Red means outside the range: it beeps, speaks, and vibrates on Android.
- No update for 15 seconds means grey: the last known gap, "last seen", and an estimate from speed for up to 2 minutes.
- If the vehicle order looks wrong for over a minute, it asks the driver to confirm their role.

## Files

| File | What it is |
|---|---|
| `index.html` | The whole app: page, styles and script |
| `mqtt.min.js` | Relay library, MQTT.js 5.10.1, stored here so the page does not depend on a third-party CDN |
| `mqtt-LICENSE.md` | Licence for MQTT.js |
| `CHANGELOG.md` | What changed in each version |
| `DEVLOG.md` | Design decisions, reasons, faults found and how they were fixed |
| `TESTING.md` | Test checklist and known issues |
| `tests/smoke.mjs` | Automated phone-size test, run before every push |
| `android/` | Android app source (shell + background service) |
| `app/PilotGap.apk` | Latest Android app; older versions as `app/PilotGap-<version>.apk` |
| `server/upload-log.gs` | Google Apps Script that receives trip logs into Ryan's Google Drive |

## Android app

`app/PilotGap.apk` (served at https://bedfordharbour.github.io/pilot-gap/app/PilotGap.apk). Android browsers see a "Download the app" box on the start screen.

- **What it adds:** keeps tracking, the relay, alarms and the trip log going when the driver switches to Maps or takes a call. A "Pilot Gap is tracking" notification shows while a trip is on. Back keeps the trip running; **End trip** stops it. If the trip stops any other way (app closed or killed), opening the app within 10 minutes restarts it by itself.
- **How it works:** a thin shell (`android/`) that shows this same page, plus a background service that feeds GPS into the page and keeps it running. Page changes reach the app without a new app version.
- **Install:** open the link on the phone, allow "install unknown apps" for the browser, install, open, allow location and notifications, and allow "not optimised" for battery when asked.
- **Build** (needs JDK 17+, Android SDK platform 34, Gradle 8.14): put `pilotgap-release.jks` and `signing.properties` (storePassword, keyAlias, keyPassword) in `~/.pilotgap/` or point `PILOTGAP_SIGNING` at their folder, then in `android/` run `gradle :app:assembleRelease`. Bump `versionCode` and `versionName` in `android/app/build.gradle` every release. The key is backed up in Ryan's Dropbox, `Claude/Projects/PilotGap/signing/`; never put it in this public repository. Certificate SHA-256 starts `5b2fdb16`.
- **Publish:** copy `android/app/build/outputs/apk/release/app-release.apk` to `app/PilotGap-<version>.apk` and `app/PilotGap.apk`, add a CHANGELOG line, push.

## Automatic log upload (set-up, once)

Logs go to a folder called **Pilot Gap logs** in Ryan's Google Drive, through a small Google Apps Script.

1. Open https://script.google.com signed in as the Google account that should hold the logs, and choose **New project**.
2. Delete what is in `Code.gs` and paste in the whole of `server/upload-log.gs` from this repository. Name the project "Pilot Gap log receiver" and save.
3. **Deploy → New deployment →** type **Web app**. Execute as: **Me**. Who has access: **Anyone**. Deploy, and allow the permissions Google asks for (it needs Drive to save the files).
4. Copy the **Web app URL** (it ends in `/exec`) and give it to Claude, who puts it into `UPLOAD_URL` in `index.html` and releases a new version.
5. Check: opening the URL in a browser shows `{"ok":true,"service":"Pilot Gap log receiver",...}`.

If the script is ever redeployed as a *new* deployment, the URL changes and `UPLOAD_URL` must be updated. Use **Manage deployments → Edit → New version** to keep the same URL.

## Versions and backups

The live page is always the newest version on `main`. Every released version is also kept unchanged on its own branch, `release/vX.Y.Z` (for example `release/v1.2.0`), so any old version can be looked at or put back.

## Making a change

1. Edit `index.html` and bump `VERSION` near the top of the script.
2. Run `node tests/smoke.mjs`; it must print "All checks passed". Look at `tests/phone.png`.
3. Add a line to `CHANGELOG.md`, and to `DEVLOG.md` if a decision or fault is involved.
4. Commit and push to `main`, then push a `release/vX.Y.Z` branch for the new version. GitHub Pages updates the live page within a few minutes (decision 2026-10-18: push straight to live; see DEVLOG).
5. Open the live page with `?v=<anything>` on the end to skip the phone's saved copy, and check the version number at the bottom.
