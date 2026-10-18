# Pilot Gap

Live distance between the lead pilot, the oversize vehicle (truck) and the rear pilot, shown on each driver's phone. Built for Bedford Harbour Engineering oversize moves.

Live page: https://bedfordharbour.github.io/pilot-gap/

## Using it

1. Open the live page on each phone.
2. Tap this phone's role: Lead, Truck or Rear.
3. Use the same trip code on all three phones (default `1234`; **New code** makes a random one).
4. Set the required gap (default 500–2000 m) and tap **Start**.
5. Mount the phone, plug it in and leave the page open.

Practice mode runs three pretend vehicles, with no GPS or other phones needed.

## How it works

- Each phone reads its own GPS (position, speed and direction) and sends it every 2 seconds to a public relay (HiveMQ's public MQTT relay, `broker.hivemq.com`), under `bhe-pilotgap/v1/<trip code>/<role>`.
- Each phone listens to the other two and works out the straight-line gaps: lead to truck, and truck to rear.
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

## Making a change

1. Edit `index.html` and bump `VERSION` near the top of the script.
2. Run `node tests/smoke.mjs`; it must print "All checks passed". Look at `tests/phone.png`.
3. Add a line to `CHANGELOG.md`, and to `DEVLOG.md` if a decision or fault is involved.
4. Commit and push to `main`. GitHub Pages updates the live page within a few minutes (decision 2026-10-18: push straight to live; see DEVLOG).
5. Open the live page with `?v=<anything>` on the end to skip the phone's saved copy, and check the version number at the bottom.
