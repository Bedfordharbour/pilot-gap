# Developer log

Decisions, the reasons for them, and faults found. Newest first. Dates are Perth time.

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
- **4d (no positions in the log): Ryan asked why.** Open; see the reply of 20:37. Positions were left out by Claude for privacy (a location record of drivers); not Ryan's decision.
- **Where logs go:** Ryan asked whether logs can come straight to him or be kept somewhere other than the phone. Open.
- **3 (old unscrambled positions on the relay under code 1234):** not answered yet.

### Decisions (v1.4.0, 20:45)

- **1b, automatic:** logs go to Ryan's Google Drive without asking the driver each time, at End trip and **every 10 minutes while the phone is connected** ("must have good signal"; the relay connection is used as the signal test). Ryan: no per-upload permission needed.
- **2a: positions in the log**, on every line during a trip (own position only; every phone sends its own log).
- **Location agreement** (Ryan: "a simple I agree to share my location as part of my piloting / driver duties"). Claude's details, for Ryan to confirm: asked once per phone with the driver's name, again if the wording changes; recorded in every log; practice mode exempt. Not legal advice; Ryan may want it checked or added to employment paperwork.
- **3b:** old unscrambled test positions on the relay left as they are.

### Faults found

- **Settings button did nothing (fixed in `fe6b45f`).** Cause: `.panel{display:flex}` overrode the `hidden` attribute, so the panel was always shown at the bottom of the page and the button only toggled an attribute with no visible effect. Fix: `[hidden]{display:none!important}`. Lesson: test by looking at the screen at phone size, not only by checking element state in code. `tests/smoke.mjs` now checks visibility.
- **Freshness judged by the sender's clock (fixed in 1.2.1, found in audit, 18 Oct 20:30).** Since 1.0.0, a position's age was worked out from the sending phone's clock. A phone whose clock ran 15 s or more slow would always show as "lost" on the others. Not seen in use (phone clocks are normally network-set). Fix: live messages are timed by when they arrive; stored messages replayed on connect keep the sender's time so an old position is not shown as fresh. Smoke test checks both.
- **Real trip continued a practice log (found in testing, fixed before release of 1.4.0).** The log carried on across practice and real trips within 6 h, so the uploaded file was named after the practice role and "unknown" driver. Fix: a log continues only for the same kind of trip, role and driver.
- **Truck gap 500–2000 m on phone unw5l7 (open, 18 Oct 21:44).** The first uploaded log (driver Dave, Truck 1) shows "between trucks 500–2000 m, 2 trucks (set here)" at 21:09:15. Defaults are 0–100 m in every version (checked 1.1.0–1.4.1). Ryan did not set it. Possible causes: someone typed it after ticking "Second truck" (the row appears under Front and looks the same), or the browser refilled the boxes after a refresh (tested by refreshing 1.0 into 1.4.1 in a test browser: not reproduced). Precaution in 1.4.2: autocomplete="off" on all gap and tick boxes. The wrong value stays saved on that phone until Truck 1 corrects it on the start screen.
- **Practice speeds unrealistic (fixed in `2bf6d57`).** The pretend gap changed too fast, giving a lead pilot speed of 152 km/h. Slowed down.

### Not yet tested in the field

- Real GPS on a moving vehicle; three phones at 500–2000 m apart; behaviour in dead spots.
- Keep screen on with a real phone (the desktop test browser refused the wake lock); Force screen on worked in the test browser.
- Speech and beeps on iPhone and Android in a cab.
