# Testing

## Before every push (automated)

```
node tests/smoke.mjs
```

It must print "All checks passed". Then look at `tests/phone.png`. It checks: the relay library loads from the repo, the default trip code is 234567, the version shows, the screen options are off by default, practice mode shows a gap, Settings opens and closes, Mute turns on and off, there is no sideways scroll, scrambling, old stored relay messages are ignored, unsent logs are kept and sent later, the in-app checks (with a stand-in for the app, including the 10-minute upload from the app's tick), and there are no script errors.

## After every push (on a phone)

1. Open `https://bedfordharbour.github.io/pilot-gap/?v=<anything>` and check the version at the bottom matches `VERSION` in `index.html`.
2. Practice mode: both gaps show and change, Settings opens and closes, and Mute counts down.
3. Two phones, same trip code, different roles: each sees the other within about 5 seconds.

## Field test checklist (first trip)

- [ ] All three phones see each other at the start.
- [ ] Gaps look right against what the drivers can see or the odometers.
- [ ] Red alert, beep and voice when a gap leaves its range (front 500–2000 m, rear 100–1000 m by default).
- [ ] Gap goes grey with "last seen" in a dead spot, and comes back afterwards.
- [ ] Did any screen lock? Which phone, and which screen setting was on?
- [ ] Battery use over the trip, with and without Keep screen on.
- [ ] Were alerts too frequent or too quiet?

## Two-truck field check

- [ ] Truck 1 ticks "Second truck on this trip"; all four phones show three gaps.
- [ ] Truck 1 changes a gap in Settings; the pilots' phones show the new range within about 10 s.

## Agreement and log upload

- [ ] First real trip on a phone asks for the location agreement and a name; later trips don't.
- [ ] After End trip, the start screen says "Sent to the office at hh:mm" and the file appears in the Pilot Gap logs folder in Google Drive.
- [ ] Mid-trip, the file in Google Drive updates about every 10 minutes.
- [ ] Out of signal at End trip: "Waiting to send", and it sends when signal returns or the page is next opened.

## Android app (real phone)

- [ ] Download from the start screen box, install, open: the page shows **App** in the status row and version 1.5.1 or later, app 1.0.1.
- [ ] Start a real trip: "Pilot Gap is tracking" notification appears; allow battery "not optimised" when asked.
- [ ] Switch to Google Maps for 10 minutes: the other phones keep seeing this vehicle (no grey "last seen"), and alarms are still spoken.
- [ ] Take or make a phone call during a trip: same as above.
- [ ] Screen off (power button) for 10 minutes: same as above.
- [ ] Swipe the app away during a trip: "Pilot Gap stopped" notification appears.
- [ ] End trip: the tracking notification goes away; log arrives in Google Drive with "running in the Android app".
- [ ] Mid-trip with the app behind Maps for 10+ minutes: the file in Google Drive updates without opening the app.
- [ ] Indoors or under cover: the vehicle keeps showing on the other phones (network position) instead of going grey.
- [ ] Change font size in the phone's settings mid-trip: the trip carries on.

## Update check

- [ ] After a new version is pushed, a phone left open shows "New version … Update now" within about 10–20 minutes (website copy can lag up to 10 min).
- [ ] Update now restarts the page with the same trip code; tap Start again.

## Trip log check

- [ ] After End trip, the start screen shows "Trip ended. Save the trip log…"; Save trip log sends the file to Dropbox or Downloads.
- [ ] The file opens and shows start, relay, gap and sample lines.

## Known issues

- Android app: running the page behind other apps is unverified on a real phone until the checklist above is done. Some phone makers (for example Samsung, Xiaomi) close background apps harder; the battery "not optimised" setting is needed.

- Phones on 1.0 or 1.1 cannot see phones on 1.2 (different relay channel). Every phone must show version 1.2.0 or later.
- The default code 234567 is not private; anyone with the page and that code can see the trip.

- Distance is a straight line; on bends the road distance is longer.
- iPhones do not vibrate from a web page; they get beeps and voice only.
- Sound needs a tap first (the Start button does this). If a phone was locked and reopened, tap the screen once.
- The phone keeps a copy of the page for up to 10 minutes after a push; add `?v=<anything>` to the address to get the new one.
- If the screen locks or the browser goes to the background, that phone stops sending its position, and its relay connection drops until the driver comes back to the page (seen in the first uploaded log, 18 Oct).
- The public relay can take about 10 s to accept the first connection ("connack timeout" in the log).
