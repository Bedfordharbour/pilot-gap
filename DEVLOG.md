# Developer log

Decisions, the reasons for them, and faults found. Newest first. Dates are Perth time.

## 2026-10-19

### Instruction (03:11)

- Ryan: "Once you have finished the app run a full audit. and loop three times. fix any fault that doesn't need me. Create a new version and change the standard code to 234567 for both the app and the browser." Worked unattended. Each round was a separate reviewer that had not seen the fixes being made. Faults were fixed without asking. Anything that changes design, wording or the message format was left for Ryan (DriveCheck rule D-056).
- **Default trip code 234567** (was 123456). It is set in the page, so the app picks it up too.
- Work in progress was kept on the branch `wip/v1.5.1` until release, so the live page stayed on 1.5.0 during the audit.

### First real-phone app log (Samsung SM-S918B, Android 16, app 1.0.0, Truck 2, 18 Oct 22:29–22:46)

Google Drive file `pilotgap-...-truck2-...` (id 1X8Fo8OhnMo1vn6XyH6RHzUQh2nRHDXrW).

- **Good:** the app tick kept gaps, alarms and the 30 s samples running behind other apps for 17 min.
- **Fault:** the page's own timers stopped in the background. As a result, there was no 10-min upload (the log was sent 3 s after returning to the app), no 10 s re-send, and no relay reconnect. Fixed by moving them into `periodic()`, which runs from `render()` and is driven by the app tick.
- **Fault:** GPS dropped for 1–2 min indoors or when stopped. Fixed in app 1.0.1 with a mobile-network position, used only when GPS has been quiet for 10 s and the network position is within 100 m.
- **Fault:** stored relay messages from old trips were used as current. There was a lead pilot position 49 min old ("Lost contact with lead pilot" at start), and Truck 1 settings from the previous evening (2 trucks, between trucks 500–2000 m). Fixed: stored messages over 10 min old are ignored and logged. *This may also explain the open 500–2000 m truck-gap entry on phone unw5l7 (18 Oct): unverified.*

### Audit round 1 (13 faults)

Fixed:
- leftover ("ghost") vehicles: Change role and Update now clear this phone's position, and an old Truck 2 no longer forces two-truck mode
- a log waiting to send was overwritten by the next trip (now queued, up to 3)
- an End-trip upload was dropped while another upload was in flight, and an upload had no timeout
- the app crashed silently when Android killed the page engine
- after opening with no signal, the app stayed cache-only
- Update now left the app's tracking running with no trip
- the default-code change was not released as a new version
- PRACTICE was left in the trip code after a reload
- font size or language changes ended the trip
- approximate-only location gave a misleading message

Left for Ryan:
- the trip code can be guessed offline from the channel name (fixing it changes the message format)
- anyone with the public key can add or overwrite logs in the Drive folder
- the agreement wording ("close the page" is not true for the app)

### Audit round 2 (9 faults)

Fixed:
- the manual relay `reconnect()` from round 1 could leave two connections fighting over one id; now the old client is ended and a new one made, and late events from a replaced client are ignored
- the upload timeout used a page timer, which freezes in the background; it is now also checked from `periodic()`
- a successful mid-trip upload cleared "waiting to send", so a trip ended by role change, crash or a flat phone lost its last lines; a running real trip now always counts as waiting
- a refused older log blocked the queue
- failed final uploads were only retried on the "online" event; the start screen now retries every 2 min
- the second alarm beep was late in the background (app 1.0.1 times it)
- the bold-text setting ended the trip
- the cache-mode reset could run too early (needs a check on a phone in airplane mode)

Round 1's network-position fix could feed in a position kilometres out, with a false speed and false alarms. Limited to positions within 100 m, and no speed worked out from a position worse than 100 m. **The 100 m figure is for Ryan to confirm.**

Left for Ryan: when Android kills the page engine mid-trip, the trip stops with only a notification. Restarting the trip automatically, or speaking a warning, is a design choice.

### Audit round 3 (no serious faults; 3 minor fixed)

- The older-log upload had no abort from the app tick: both uploads are now checked.
- A 60 m network position after a 5 m GPS fix could still give a false speed. The app now marks network positions, and the page works out no speed or heading from them.
- A quick End trip and Start again could leave the first start's relay connection running. Now only the latest start connects, and a late key for an old trip code is dropped.
- **Field check, not a confirmed fault:** the relay library uses page timers inside. With a phone in the background for 5+ min in a two-phone test, check in the log that the other phones' "ages" stay under 15 s and "starting a new connection" lines do not repeat.

### Released 1.5.1 / app 1.0.1 (19 Oct, early morning)

79 smoke checks pass. App signed with the Pilot Gap key (certificate `5b2fdb16…74e8`), lint clean. **Not yet tested on a real phone.**

## 2026-10-18

### Decisions

- **Purpose.** Show the gap between the lead pilot, the truck and the rear pilot during oversize moves. Ryan stated the required gap as 500–2000 m. Accuracy needed: to the metre. At these distances, phone GPS (a few metres of error) is enough, so no extra receiver is needed.
- **Reference rules.** Main Roads WA, *Class 1 RAV Oversize Period Permit Operating Conditions* (Nov 2022 copy): cl. 9.4.4 sets no fixed pilot distance ("far enough ahead to give adequate warning"); cl. 7.3 requires a 200 m minimum following distance between heavy vehicles; cl. 9.11.10 sets 100 m maximum between convoy RAVs. The 500–2000 m range is BHE company practice, not a regulation, and changes from job to job as needed (Ryan, 18 Oct). That is why the range is set on each phone at Start and can be changed under Settings during a trip.
- **Web page, not a Claude artifact.** Claude artifacts block phone location, so the page is hosted on GitHub Pages instead.
- **Relay: public HiveMQ broker over secure WebSocket.** Free, with no server to run. Risk accepted at first: anyone using the same trip code sees the positions; default code 1234 at Ryan's request (replaced in 1.2.0 by scrambled messages and default 123456, see below). A private relay on Ryan's own server is the upgrade path.
- **Mobile network only for now.** Coverage is patchy on routes. When contact is lost, the page shows the last known gap in grey plus a speed-based estimate. A LoRa radio link (Meshtastic) is the longer-term option and needs hardware.
- **Estimate stops after 2 minutes.** A pilot may pull over while out of coverage, which makes a speed-based estimate wrong; 2 minutes limits the damage.
- **Roles are picked by the driver (option 1b), with an automatic check.** Fully automatic ordering fails when stopped and on bends. The check asks "Are you still the …?" after the order looks wrong for 60 s.
- **Straight-line distance.** On bends, the road distance is longer than the figure shown. This is acceptable for the trial; it is not suitable as compliance evidence without more work.
- **Screen-on settings are off by default** (Ryan: "if it's not required why use it"). Keep screen on uses the Wake Lock API. Force screen on loops a 2-second blank video, which costs more battery. Each is a separate setting.
- **No in-app touch lock** (Ryan declined, 18 Oct).
- **Push straight to the live page (option 2b).** Ryan's call: the app is small, so the risk is low. Revisit if it grows or if a push ever breaks a live trip.
- **Relay library stored in the repo.** Removes the dependency on unpkg.com being up.

### Decisions (v1.1.0, evening)

- **Second truck is a separate vehicle** driving behind the first, not attached to the same load (Ryan). So the gap between trucks is measured and alarmed like the others.
- **Front and rear have different ranges** because the pilots work differently (Ryan). Defaults set by Ryan: front 500–2000 m, rear 100–1000 m. Between trucks defaults to 0–100 m, from the Main Roads convoy rule (cl. 9.11.10: no more than 100 m between convoy RAVs). Ryan gave no figure; Truck 1 can change it per job.
- **Truck 1 sets the gaps for the trip** so all phones alarm on the same figures. They are sent as a retained relay message on `.../cfg` (v1: `bhe-pilotgap/v1/<trip>/cfg`; from 1.2.0 scrambled under a hashed channel), re-sent every 10 s and on every change. Other phones use the defaults until it arrives and say so on screen. Bad values (min not below max) are ignored.
- **Role id `truck` kept for Truck 1** so a phone still on v1.0.0 still matches during the changeover.
- **Truck 2 is included automatically** if Truck 1 ticks "Second truck on this trip", if a Truck 2 phone appears, or if this phone is Truck 2.

### Audit and decisions (v1.2.0, evening)

Ryan asked for an audit of the app recommendation against earlier builds (DriveCheck). Corrections to what Claude had said:
- Proposed Capacitor; wrong fit. DriveCheck already runs GPS in the background on Android (`DriveService`, watchdog, "stopped tracking" alarm, fixes CR-006 and CR-015).
- Said GitHub would build the app; DriveCheck is built and signed in Claude's workspace using the Google build sites Ryan allowed.
- "A day or two" for the app was optimistic given DriveCheck 1.5.0–1.5.7 and four regressions on 18 Oct.
- Said only someone guessing the trip code could see positions. Wrong: anyone listening to every channel on the public relay could see every trip, including speeds (unverified, but how public relays normally work). Same concern as DriveCheck D-011.
- Pilot Gap kept no record on the phone; DriveCheck D-051 added a developer log because faults could not be explained without one.
- Claude made some design calls without explicit approval (single screen-on switch, 0–100 m truck gap, "Front gap" wording). Going forward, DriveCheck's approval rule (D-056) applies to the Pilot Gap app.

Ryan's decisions (20:18):
- **8a: trip log on each phone**, with Save log at End trip. Built in 1.2.0.
- **8b: scramble messages with the trip code.** Built in 1.2.0. Claude proposed a random default code; **Ryan overruled: default stays a fixed 123456** for testing where privacy does not matter (20:21). Use New code for real trips.
- **9a: after the trial, a native Android app on DriveCheck's base** (background service, watchdog, build and signing method) plus the relay; the web page stays for browser users. Same message format, held together by a written message spec and a shared test. DriveCheck's change process applies.
- **Some drivers use iPhones** (Ryan). iPhone users stay on the web page for now: no vibration, and no background tracking. An iPhone app needs an Apple developer account and Apple's review; not decided.

### Decisions (v1.3.0, 20:37)

- **2a: update check**, built. Never reloads by itself, so a driver is not dropped mid-trip; the banner says to update when stopped.
- **4a declined:** no 6-character minimum for trip codes during testing; back to 4. **To do: ask again once the design is out of testing.**
- **4b approved:** a new trip within 6 h continues the same log. **4c approved:** Save trip log on the start screen after End trip and in Settings.
- **4d (no positions in the log): Ryan asked why.** Positions were left out by Claude for privacy (a location record of drivers); not Ryan's decision. *Resolved 20:45: 2a, positions in (see v1.4.0).*
- **Where logs go:** Ryan asked whether logs can come straight to him or be kept somewhere other than the phone. *Resolved 20:45: 1b, automatic upload to Google Drive (see v1.4.0).*
- **3 (old unscrambled positions on the relay under code 1234):** *Resolved 20:45: 3b, leave them.*

### Decisions (v1.4.0, 20:45)

- **1b, automatic:** logs go to Ryan's Google Drive without asking the driver each time, at End trip and **every 10 minutes while the phone is connected** ("must have good signal"; the relay connection is used as the signal test). Ryan: no per-upload permission needed.
- **2a: positions in the log**, on every line during a trip (own position only; every phone sends its own log).
- **Location agreement** (Ryan: "a simple I agree to share my location as part of my piloting / driver duties"). Claude's details, for Ryan to confirm: asked once per phone with the driver's name, again if the wording changes; recorded in every log; practice mode exempt. Not legal advice; Ryan may want it checked or added to employment paperwork.
- **3b:** old unscrambled test positions on the relay left as they are.

### v1.4.1–1.4.2 and first real upload (21:36–21:48)

- Ryan deployed `server/upload-log.gs` as a Google Apps Script web app (21:36); `UPLOAD_URL` set in 1.4.1. The workspace cannot reach script.google.com (Google redirects to a blocked host), so checking is done through Ryan's Google Drive connection instead.
- **Upload confirmed working (21:41).** File `pilotgap-20261018-2109-truck-Dave-unw5l7.txt` in Google Drive folder **Pilot Gap logs** (Drive folder id `1dx-TD0nYeElQhzOfo-xgLd9wQ1tx_hAx`). First copy 21:39 (the 21:14 trip that had been waiting from before the upload was switched on — retry path works), second at End trip 21:40.
- **Two phones confirmed through the scrambled relay** (same log): front gap 14 m with phones together (21:13, "too close" spoken), 5,681 m with the lead pilot elsewhere (21:40, "too far" spoken).
- Seen in the log: the relay drops whenever the phone leaves the page and reconnects within seconds of returning; first connection twice took ~10 s ("connack timeout", public relay slow to answer). Watch on the trial.
- Wording "waiting for Truck 1" shown on Truck 1's own phone before its GPS fix; change to "waiting for GPS on this phone" proposed, **Ryan: not now**.

### Android app 1.0.0 (21:57–22:15)

- **Ryan: "Finish the project. Everything is working ok atm we can roll it back if it doesn't work"** (21:57) — the app is built now instead of after the trial (overrides the "after the trial" part of 9a). Rollback: uninstall the app and use the browser; the page works the same either way.
- **Design (Claude's, for Ryan to confirm):** thin Android shell (WebView of the live page) instead of a separate native app. Keeps one set of screens and gap logic, so app and browser can't drift apart (the main risk raised in 9a). The native part copies DriveCheck's approach: foreground location service, wake lock, permanent notification, "stopped" alert if the app is closed mid-trip. The service feeds GPS into the page and drives the page once a second, because Android slows a hidden page's own timers.
- **Unverified until tested on a real phone:** that a WebView keeps its relay connection and keeps running the page behind other apps for a whole trip. If it doesn't, the fallback is moving the relay and gap check into the service (more code, two copies of the logic).
- Signing key: new key for Pilot Gap (not DriveCheck's), certificate SHA-256 starting `5b2fdb16`. Kept outside the source; backed up to Dropbox `Claude/Projects/PilotGap/signing/`. Every later version must use the same key or it won't install over the old one.
- Distribution: APK published with the page at `app/PilotGap.apk` (latest) and `app/PilotGap-<version>.apk`. No Play Store. Android browsers see a download box.
- Not on iPhone (no change: iPhone users stay on the web page).

### Faults found

- **Settings button did nothing (fixed in `fe6b45f`).** Cause: `.panel{display:flex}` overrode the `hidden` attribute, so the panel was always shown at the bottom of the page and the button only toggled an attribute with no visible effect. Fix: `[hidden]{display:none!important}`. Lesson: test by looking at the screen at phone size, not only by checking element state in code. `tests/smoke.mjs` now checks visibility.
- **Freshness judged by the sender's clock (fixed in 1.2.1, found in audit, 18 Oct 20:30).** Since 1.0.0, a position's age was worked out from the sending phone's clock. A phone whose clock ran 15 s or more slow would always show as "lost" on the others. Not seen in use (phone clocks are normally network-set). Fix: live messages are timed by when they arrive; stored messages replayed on connect keep the sender's time so an old position is not shown as fresh. Smoke test checks both.
- **Real trip continued a practice log (found in testing, fixed before release of 1.4.0).** The log carried on across practice and real trips within 6 h, so the uploaded file was named after the practice role and "unknown" driver. Fix: a log continues only for the same kind of trip, role and driver.
- **App never connected to the relay (found in testing before release of 1.5.0).** In the page, starting the app's background tracking was written as an alternative to the relay set-up instead of alongside it. The in-app test caught it (no scramble key, no connection). Fixed by starting them separately.
- **Truck gap 500–2000 m on phone unw5l7 (open, 18 Oct 21:44).** The first uploaded log (driver Dave, Truck 1) shows "between trucks 500–2000 m, 2 trucks (set here)" at 21:09:15. Defaults are 0–100 m in every version (checked 1.1.0–1.4.1). Ryan did not set it. Possible causes: someone typed it after ticking "Second truck" (the row appears under Front and looks the same), or the browser refilled the boxes after a refresh (tested by refreshing 1.0 into 1.4.1 in a test browser: not reproduced). Precaution in 1.4.2: autocomplete="off" on all gap and tick boxes. The wrong value stays saved on that phone until Truck 1 corrects it on the start screen.
- **Practice speeds unrealistic (fixed in `2bf6d57`).** The pretend gap changed too fast, giving a lead pilot speed of 152 km/h. Slowed down.

### Not yet tested in the field

- Real GPS on a moving vehicle; three or four phones at working distances; behaviour in dead spots.
- The 10-minute upload during a trip (both test trips were under 10 minutes).
- Done on 18 Oct: two phones through the scrambled relay, automatic upload at End trip and on page open.
- Keep screen on with a real phone (the desktop test browser refused the wake lock); Force screen on worked in the test browser.
- Speech and beeps on iPhone and Android in a cab.
