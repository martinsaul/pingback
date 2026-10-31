# PingBack

**Know the moment your internet is back.** Press play, put your phone down, and PingBack
alerts you when your connection is working again.

PingBack pings several well-known internet providers, your router (gateway), and the DNS
servers your phone is using. It doesn't call your connection "back" because of one lucky reply.
It waits until enough providers answer consistently, and you choose how strict that is.

## Features

- **One button.** Press play to start and stop to end. There is nothing to set up.
- **Checks providers, not just Wi-Fi.** It pings Cloudflare (`1.1.1.1`), Google (`8.8.8.8`),
  Quad9 (`9.9.9.9`), and OpenDNS (`208.67.222.222`) by IP address, so the check doesn't depend on
  DNS working.
- **Shows where the problem is.** It also pings your gateway and the DNS servers your network
  hands out:
  - If the gateway replies but the providers don't, the problem is past your router (your ISP
    or modem).
  - If the gateway doesn't reply either, the problem is your Wi-Fi or router.
- **Configurable "back" threshold.** By default the internet is "back" when at least **2
  providers** have each answered **5 pings in a row**. You can change both numbers and the ping
  interval in settings.
- **Works in the background.** A foreground service keeps checking with the screen off, and
  you get a notification when the connection comes back.
- **Follows network changes.** If your phone moves from Wi-Fi to mobile data, PingBack picks up
  the new gateway and DNS servers on the next round.
- **Private.** PingBack has no accounts, no analytics, no ads, and no tracking. The only traffic
  it sends is the pings.

## Install

Build it yourself (see [docs/BUILDING.md](docs/BUILDING.md)), or download the debug APK from the
artifacts of the latest successful [CI run](../../actions). It needs Android 8.0 (API 26) or
newer.

## Using it

1. Open PingBack and press the green **play** button.
2. On Android 13 and newer, allow notifications when asked so PingBack can alert you. If you say
   no, it still works, but you only see the result on screen.
3. Leave it running. The status at the top shows one of these:

   | Status | Meaning |
   |---|---|
   | **Checking...** | PingBack is building up reply streaks and hasn't seen an outage. |
   | **Offline** | Providers stopped replying and the threshold isn't met. |
   | **Online** | The threshold is met. |
   | **Stopped** | Monitoring is off. |

4. When the connection goes from **Offline** to **Online**, you get an **"Internet is back"**
   notification.
5. Press **stop** in the app, or **Stop** on the notification, to end monitoring.

Each row shows the latest round-trip time, the current reply streak against your threshold (for
example `streak 3/5`), and the overall reply rate since you pressed play.

### When does it alert?

PingBack alerts when the threshold becomes met **after it has seen an outage**. An outage is a
round in which the threshold wasn't met and at least one provider failed to reply.

- If you press play while you're **offline**, you get an alert as soon as the connection meets the
  threshold.
- If you press play while you're already **online**, you get no alert. The status just moves from
  Checking... to Online. If the connection then drops and comes back, you get an alert.
- If one provider has a hiccup while the others still meet the threshold, that doesn't count as an
  outage.
- PingBack keeps monitoring after an alert, so it alerts again after each later outage.

Only the internet providers count toward the threshold. Your gateway and DNS servers are shown to
help you diagnose problems, because a router can answer while the internet behind it is down.

## Settings

Tap the gear icon at the top right.

| Setting | Default | Range | What it does |
|---|---|---|---|
| Pings in a row | 5 | 1 to 30 | How many replies in a row a provider needs before it counts. One missed reply resets its streak to zero. |
| Providers that must agree | 2 | 1 to 4 | How many providers must reach that streak at the same time. |
| Seconds between pings | 2 | 1 to 30 | How often a new round of pings starts. |

With the defaults, the fastest an alert can arrive after the connection returns is about
10 seconds (5 rounds, 2 seconds apart).

## How it works

Each round, PingBack:

1. Reads the active network's default gateway and DNS servers from Android's
   `ConnectivityManager`.
2. Pings every target in parallel. It runs the system `ping` binary (ICMP echo, one packet,
   2-second timeout). On devices where that binary is missing or blocked, it falls back to a TCP
   connection on port 53. A refused connection still counts as a reply, because the host had to be
   reachable to refuse it. Rows checked this way are labelled `(TCP)`.
3. Updates each target's reply streak and checks the threshold.

**Threading:** all networking and process work runs on background threads (Kotlin coroutines on
`Dispatchers.IO`), never on the main thread. Debug builds turn on `StrictMode` so that any network
call on the main thread crashes the app straight away.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the code layout.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | The TCP fallback probe. |
| `ACCESS_NETWORK_STATE` | Reading the gateway, DNS servers, and network type. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeping the check running in the background after you press play. |
| `POST_NOTIFICATIONS` | The "Internet is back" alert (Android 13+). |

PingBack doesn't start on its own. It runs only after you press play, and it stops when you press
stop.

## Limitations

- Some networks block ICMP. Corporate Wi-Fi, some mobile carriers, and some VPNs do this, so
  providers may show "no reply" even though browsing works. If that happens, lower **Providers that
  must agree**, or check whether rows switch to `(TCP)`.
- On mobile data there's usually no gateway to show. That's expected.
- Battery savers on some phones may pause background services. If monitoring stops when the screen
  is off, exclude PingBack from battery optimisation in your phone's settings.

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Copyright (C) 2026 Martin Saul

PingBack is free software: you can redistribute it and/or modify it under the terms of the
[GNU Affero General Public License v3.0](LICENSE) as published by the Free Software Foundation,
either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the license
for details.
