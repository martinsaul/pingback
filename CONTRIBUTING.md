# Contributing to PingBack

Thanks for helping out.

## Reporting a bug

Open an issue with:

- your phone model and Android version
- your connection type (Wi-Fi, mobile data, VPN)
- what you expected and what happened
- whether the rows showed `(TCP)` (this means the ICMP `ping` binary wasn't usable)

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep the change focused. Small pull requests get reviewed faster.
3. Before you open the pull request, run:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

4. Add or update unit tests for any logic change, especially in `monitor/`.
5. Don't do network or other blocking I/O on the main thread. Debug builds crash if you do.

## Design principles

- **One button.** Anything new must not get in the way of "open, press play, wait".
- **No tracking.** No analytics, crash reporters, ads, or third-party SDKs that phone home.
- **Few dependencies.** AndroidX and Kotlin coroutines only, unless there's a strong reason.

## License

By contributing, you agree that your contributions are licensed under the
[GNU AGPL v3.0 or later](LICENSE), the same license as the project.
