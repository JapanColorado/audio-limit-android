# Audio Limit

Tracks how long each app plays audio/media and locks apps once you hit a limit — in the spirit of LockMeOut.

## Features

- **Tracking**: per-app listening time, detected from media sessions (the same source as the media controls in the notification shade). Today view + 7-day history, with a usage day that resets at a configurable hour (default 4 am).
- **Rules**: daily limit for one app, a group of apps sharing a budget, or all audio (with exclusions); limits can apply only on chosen weekdays. Optional block windows (e.g. 11 pm–7 am) block regardless of usage.
- **Blocking**: blocked apps' playback is paused (and stopped if they ignore pause), and opening them shows a full-screen lock until the block lifts. Heads-up notification a configurable time before a limit (Settings → Warn before limit; default 5 min, or off).
- **Strictness**: a locked rule can only be tightened — no raising limits, removing apps/windows/days, or deleting — until it unlocks. The same freeze starts once a limit is within its warning time and holds until the daily reset, so you can't dodge the limit after being warned; the warning time itself can't be shortened then. While any lock is active you can't change the reset hour or turn off protections; the accessibility service bounces you out of system pages mentioning Audio Limit (App info, accessibility, notification access, device admin, uninstall dialog), and device admin blocks plain uninstall. Uninstall protection keeps you off the device admin pages by itself (even with settings protection off) for as long as any rule is locked or inside its warning time — the same span the in-app switch refuses — and a deactivation request that still gets through is covered by the lock screen.

## Building

Requires [pixi](https://pixi.sh) (it supplies JDK 21) and an Android SDK at `~/Android/Sdk` (exported as `ANDROID_HOME` on pixi activation). Never run `./gradlew` outside pixi.

```sh
pixi install          # one-time: fetches the JDK
pixi run build        # assembleRelease (signed if keystore.properties exists, else debug-signed)
pixi run install      # build + install the release APK on the connected phone
pixi run run          # install + launch
pixi run build-debug / install-debug / run-debug   # debug variant (dev.russell.audiolimit.debug)
pixi run test         # JVM unit tests
pixi run lint         # Android lint
pixi run logs         # filtered logcat
```

Release signing reads a gitignored `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).

## Setup on the phone

1. `pixi run install` (or `run-debug` for the debug build).
2. Open the app and follow the cards on the Today screen: notification access (required), accessibility service, device admin, notifications.
3. If a toggle is greyed out ("restricted setting"), go to App info → ⋮ → **Allow restricted settings**, then retry.

## Limitations

- Audio without a media session (some games, some web audio) isn't detected.
- "All audio" sums each app's time, so simultaneous playback counts twice.
- Rules that could never unlock are refused: block windows covering the whole week, windows with equal start and end, and limits under 5 minutes.
- Escape hatch if something goes wrong: reboot into safe mode (disables third-party accessibility services), then deactivate device admin and uninstall.

## Manual test checklist

1. Grant all access; play Spotify/YouTube → it appears on Today with the playing icon, time increases.
2. Create a 5 min app rule; at ~0–5 min left a warning notification appears; at the limit, playback pauses, a "Paused" notification appears.
3. Press play again → it's paused again within a second.
4. Open the app → lock screen with unlock time; "Go home" / back go to the launcher.
5. Edit the locked rule: raising the limit or deleting is refused; lowering it saves. The same holds from the moment the warning appears.
6. With a lock active, open Settings → Apps → Audio Limit → bounced to the lock screen. Settings → reset hour change refused.
7. Add a block window covering now on an unlimited rule → app is blocked immediately; ends at the window end.
8. With device admin on, "Protect settings while locked" off, and a rule inside its warning time: Settings → Device admin apps → Audio Limit → bounced to the lock screen ("Uninstall protection…").
