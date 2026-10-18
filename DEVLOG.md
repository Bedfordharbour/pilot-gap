# Developer log

Decisions, the reasons for them, and faults found. Newest first. Dates are Perth time.

## 2026-10-18

### Decisions

- **Purpose.** Show the gap between the lead pilot, the truck and the rear pilot during oversize moves. Ryan stated the required gap as 500–2000 m. Accuracy needed: to the metre. At these distances, phone GPS (a few metres of error) is enough, so no extra receiver is needed.
- **Reference rules.** Main Roads WA, *Class 1 RAV Oversize Period Permit Operating Conditions* (Nov 2022 copy): cl. 9.4.4 sets no fixed pilot distance ("far enough ahead to give adequate warning"); cl. 7.3 requires a 200 m minimum following distance between heavy vehicles; cl. 9.11.10 sets 100 m maximum between convoy RAVs. The 500–2000 m range is BHE company practice, not a regulation, and changes from job to job as needed (Ryan, 18 Oct). That is why the range is set on each phone at Start and can be changed under Settings during a trip.
- **Web page, not a Claude artifact.** Claude artifacts block phone location, so the page is hosted on GitHub Pages instead.
- **Relay: public HiveMQ broker over secure WebSocket.** Free, with no server to run. Risk accepted: anyone using the same trip code sees the positions. Default code 1234 at Ryan's request; New code makes a random 6-character code. A private relay on Ryan's own server is the upgrade path.
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
- **Truck 1 sets the gaps for the trip** so all phones alarm on the same figures. They are sent as a retained relay message on `bhe-pilotgap/v1/<trip>/cfg`, re-sent every 10 s and on every change. Other phones use the defaults until it arrives and say so on screen. Bad values (min not below max) are ignored.
- **Role id `truck` kept for Truck 1** so a phone still on v1.0.0 still matches during the changeover.
- **Truck 2 is included automatically** if Truck 1 ticks "Second truck on this trip", if a Truck 2 phone appears, or if this phone is Truck 2.

### Faults found

- **Settings button did nothing (fixed in `fe6b45f`).** Cause: `.panel{display:flex}` overrode the `hidden` attribute, so the panel was always shown at the bottom of the page and the button only toggled an attribute with no visible effect. Fix: `[hidden]{display:none!important}`. Lesson: test by looking at the screen at phone size, not only by checking element state in code. `tests/smoke.mjs` now checks visibility.
- **Practice speeds unrealistic (fixed in `2bf6d57`).** The pretend gap changed too fast, giving a lead pilot speed of 152 km/h. Slowed down.

### Not yet tested in the field

- Real GPS on a moving vehicle; three phones at 500–2000 m apart; behaviour in dead spots.
- Keep screen on with a real phone (the desktop test browser refused the wake lock); Force screen on worked in the test browser.
- Speech and beeps on iPhone and Android in a cab.
