# Testing

## Before every push (automated)

```
node tests/smoke.mjs
```

It must print "All checks passed". Then look at `tests/phone.png`. It checks: the relay library loads from the repo, the default trip code is 1234, the version shows, the screen options are off by default, practice mode shows a gap, Settings opens and closes, Mute turns on and off, there is no sideways scroll, and there are no script errors.

## After every push (on a phone)

1. Open `https://bedfordharbour.github.io/pilot-gap/?v=<anything>` and check the version at the bottom matches `VERSION` in `index.html`.
2. Practice mode: both gaps show and change, Settings opens and closes, and Mute counts down.
3. Two phones, same trip code, different roles: each sees the other within about 5 seconds.

## Field test checklist (first trip)

- [ ] All three phones see each other at the start.
- [ ] Gaps look right against what the drivers can see or the odometers.
- [ ] Red alert, beep and voice when a gap leaves 500–2000 m.
- [ ] Gap goes grey with "last seen" in a dead spot, and comes back afterwards.
- [ ] Did any screen lock? Which phone, and which screen setting was on?
- [ ] Battery use over the trip, with and without Keep screen on.
- [ ] Were alerts too frequent or too quiet?

## Two-truck field check

- [ ] Truck 1 ticks "Second truck on this trip"; all four phones show three gaps.
- [ ] Truck 1 changes a gap in Settings; the pilots' phones show the new range within about 10 s.

## Known issues

- Distance is a straight line; on bends the road distance is longer.
- Public relay: anyone using the same trip code sees the positions. Use New code for sensitive jobs.
- iPhones do not vibrate from a web page; they get beeps and voice only.
- Sound needs a tap first (the Start button does this). If a phone was locked and reopened, tap the screen once.
- The phone keeps a copy of the page for up to 10 minutes after a push; add `?v=<anything>` to the address to get the new one.
- If the screen locks or the browser goes to the background, that phone stops sending its position.
