# Changelog

The version shows at the bottom of the start screen and in Settings. Newest first.

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
| (this) | Version number shown on the page; README, CHANGELOG, DEVLOG, TESTING and an automated smoke test added |
| `4720625` | Relay library stored in the repo instead of loaded from unpkg.com |
| `fe6b45f` | Fix: Settings panel was always visible, so the Settings button seemed to do nothing |
| `c329ae7` | Mute is now on/off and lasts 5 min (was 1 min); bigger Truck label with a truck symbol |
| `fc64688` | Screen setting split into Keep screen on (wake lock) and Force screen on (blank video) |
| `26d41ff` | Settings button scrolls the panel into view and changes to "Close settings" |
| `5b0f56d` | Keep screen on added as a setting, off by default, with a status light |
| `2bf6d57` | Practice mode: pretend vehicles at realistic speeds (was up to 152 km/h) |
| `06cf240` | Default trip code 1234; New code still makes a random one |
| `a6cf37f` | First page: roles, trip code, live gaps, alerts, last-seen and estimate, role check, practice mode |
