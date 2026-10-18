# Changelog

The version shows at the bottom of the start screen and in Settings. Newest first.

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
