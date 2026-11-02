# Architecture

PingBack is a single-module Android app written in Kotlin with Jetpack Compose. It has no
third-party dependencies beyond AndroidX and Kotlin coroutines.

```
app/src/main/java/io/saul/pingback/
  PingBackApp.kt            Application: loads settings, creates notification channels,
                            enables StrictMode (network on main thread = crash) in debug
  monitor/
    Target.kt               Target + TargetKind; the fixed list of public providers
    Pinger.kt               One blocking probe: system ping (ICMP), TCP:53 fallback;
                            PingOutput parses "time=12.3 ms"
    NetworkInfo.kt          Reads the active network's gateway, DNS servers, transport
    Threshold.kt            Threshold ("N in a row from M providers") + RestoreDetector
                            (decides when to alert)
    MonitorState.kt         Phase, TargetStatus, MonitorState, Settings
    MonitorEngine.kt        The loop: read network -> ping all in parallel -> update
                            streaks -> evaluate threshold -> publish state / alert
    MonitorRepository.kt    Process-wide StateFlows for state and settings;
                            SettingsStore (SharedPreferences)
    MonitorService.kt       Foreground service that owns the engine's coroutine
    Notifications.kt        Ongoing "monitoring" notification + "Internet is back" alert
  ui/
    MainActivity.kt         Play/stop, notification permission request
    MainScreen.kt           Status header, provider rows, gateway/DNS rows
    SettingsDialog.kt       Threshold and interval sliders
    Theme.kt                Material 3 theme (dynamic colour on Android 12+)
```

## Data flow

```
 MainActivity --start/stop--> MonitorService
                                  |
                                  | launches on Dispatchers.IO
                                  v
                             MonitorEngine --ping()--> Pinger (ping binary / TCP)
                                  |        --snapshot()--> NetworkInfoReader
                                  |
                       onState    |    onInternetBack
                 +----------------+----------------+
                 v                                 v
     MonitorRepository.state              Notifications.showInternetBack
                 |
                 | collectAsStateWithLifecycle
                 v
            MainScreen (UI)
```

## Threading rules

- `MonitorService` owns a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. The engine runs
  inside it.
- `MonitorEngine` runs `readNetwork()` with `withContext(io)` and each ping with `async(io)`, so
  even if someone launched it from the main thread, the blocking work still moves off it.
- The UI only reads `StateFlow`s. It never calls the pinger.
- Debug builds set a `StrictMode` thread policy with `detectNetwork().penaltyDeath()`.
- A small lock in `MonitorService` keeps a round that finishes after the user pressed stop from
  publishing state or re-posting the ongoing notification.

## The threshold and the alert

- A target's **streak** is the number of replies in a row. Any missed reply resets it to 0.
- `Threshold.isMet` is true when at least `minProviders` providers have
  `streak >= consecutivePings`.
- `RestoreDetector` records an **outage** when a round ends with the threshold not met and at
  least one provider failing. The next round in which the threshold is met raises the alert and
  clears the outage.
  - This is why pressing play on a healthy connection doesn't alert: in the first rounds every
    provider replies, so nothing fails and no outage is recorded.
- Gateway and DNS targets never count toward the threshold.

## Tests

Plain JVM unit tests in `app/src/test`:

- `ThresholdTest` and `RestoreDetectorTest` cover the threshold maths and when alerts fire.
- `PingOutputTest` covers parsing real `ping` output.
- `MonitorEngineTest` drives the engine with a fake pinger and fake network to cover a full outage
  and recovery, streak resets, and switching networks.

Run them with `./gradlew testDebugUnitTest`.

`ui/ScreenshotTest` renders the README screenshots with Roborazzi on Robolectric. It runs only
through `./gradlew recordScreenshots` (see [BUILDING.md](BUILDING.md#screenshots)).
