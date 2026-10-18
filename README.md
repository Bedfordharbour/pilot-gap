# Pilot Gap

Live distance between the lead pilot, the truck or trucks, and the rear pilot, shown on each driver's phone. Built for Bedford Harbour Engineering oversize moves.

Live page: https://bedfordharbour.github.io/pilot-gap/

## Using it

1. Open the live page on each phone.
2. Tap this phone's role: Lead, Truck 1, Truck 2 (only when there is a second truck) or Rear.
3. Use the same trip code on every phone (default `123456` for testing; **New code** makes a random one for real trips). At least 4 letters or numbers.
4. On Truck 1 only: set the gaps (defaults front 500–2000 m, between trucks 0–100 m, rear 100–1000 m) and tick **Second truck on this trip** if there is one. The other phones get these from Truck 1. Tap **Start**.
5. Mount the phone, plug it in and leave the page open.

Practice mode runs three pretend vehicles, with no GPS or other phones needed.

## How it works

- Each phone reads its own GPS (position, speed and direction) and sends it every 2 seconds to a public relay (HiveMQ's public MQTT relay, `broker.hivemq.com`), under `bhe-pilotgap/v2/<hash of trip code>/<role>`.
- Every message is scrambled with a key made from the trip code (AES-GCM, key by PBKDF2-SHA-256, 150,000 rounds). The code itself is never sent.
- Each phone listens to the others and works out the straight-line gaps: front (lead to Truck 1), between trucks (Truck 1 to Truck 2) and rear (last truck to rear pilot).
- Truck 1 sends the gap ranges and number of trucks on `.../cfg`; the other phones use them.
- Each phone keeps a trip log; **Save trip log** after End trip (or in Settings) shares or downloads it as a .txt file.
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
| `server/upload-log.gs` | Google Apps Script that receives trip logs into Ryan's Google Drive |

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
