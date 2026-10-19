# CLAUDE.md

Audio Limit (`dev.russell.audiolimit`): an Android app (Kotlin, Jetpack Compose / Material 3, single `app` module, minSdk 31) that tracks media playback per app and enforces LockMeOut-style limits — per-app, group, and total daily budgets plus time-of-day block windows.

## Commands

pixi tasks wrap `./gradlew` (the wrapper pins Gradle 8.11.1; pixi supplies JDK 21). Never run `./gradlew` outside pixi.

```sh
pixi run test          # JVM unit tests (domain logic)
pixi run lint          # ./gradlew lintDebug
pixi run build-debug   # assembleDebug; `build` = assembleRelease (minified, debug-signed)
pixi run run-debug     # install debug (appId dev.russell.audiolimit.debug) and launch
pixi run logs          # logcat tag AudioLimit
pixi run sessions      # dumpsys media_session: which apps have sessions and their states
```

## Architecture

- **Manual DI**: `AppGraph` (built in `AudioLimitApp`) owns every long-lived object; reach it via `context.appGraph`.
- **Detection** (`tracking/`): `MediaListenerService` is a NotificationListenerService only for the grant that unlocks `MediaSessionManager.getActiveSessions()`. It registers a callback on every session and reports the set of packages in `STATE_PLAYING` to `PlaybackTracker`, which writes `usage_segment` rows (insert on start, extend `endMillis` every 10 s and on stop).
- **Evaluation** (`domain/`, pure + unit-tested): `RuleEvaluator` turns rules + today's usage into an `Evaluation` (which rules are blocking, until when). `Enforcer` keeps `evaluation: StateFlow` fresh on rule/settings/usage changes and a 15 s ticker.
- **Enforcement**: `MediaListenerService.enforce()` pauses (then stops, if ignored) any playing controller that `blockFor(pkg)` says is blocked. `BlockAccessibilityService` covers locked apps with `BlockActivity`, and while any lock is active bounces the user out of Settings/package-installer pages whose text mentions the app name. `AdminReceiver` is a policy-less device admin for uninstall friction.
- **Usage days** start at `AppSettings.resetHour` (default 4 am). Always use `UsageDay` for day boundaries. Limit weekdays are judged by the day the usage day started; block windows by wall clock (overnight windows belong to their start day).
- **Locks**: every UI edit to rules/settings goes through `LockGuard`. While a rule is blocking, `Loosening.reason()` must return null for the edit; while any lock is active, the reset hour and protections can't be relaxed. Don't add UI paths that write `RuleRepository`/`SettingsRepository` directly.
- TOTAL rules use `packages` as an *exclusion* list and only screen-block apps that have ever played media (`mediaPackages`), so they never lock the dialer etc.

## Database

Room, `AppDatabase.VERSION` = 1, schemas exported to `app/schemas/`. There is no destructive fallback: a schema change needs a real `Migration`.
