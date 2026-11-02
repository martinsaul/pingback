# Building PingBack

## Requirements

- JDK 17 or newer (CI uses 21)
- Android SDK with platform 36 and build-tools installed
- No other setup: the Gradle wrapper downloads the right Gradle version

Tell Gradle where your SDK is, either with the `ANDROID_HOME` environment variable or with a
`local.properties` file in the project root:

```
sdk.dir=/path/to/android-sdk
```

## Common tasks

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint
./gradlew assembleDebug       # debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # release APK (unsigned) -> app/build/outputs/apk/release/
```

## Screenshots

The images in `docs/screenshots` are rendered on the JVM with
[Roborazzi](https://github.com/takahirom/roborazzi) and Robolectric, from fake monitor states in
`app/src/test/java/io/saul/pingback/ui/ScreenshotTest.kt`. You don't need a device or emulator.
After a UI change, re-render them with:

```bash
./gradlew recordScreenshots
```

A normal `testDebugUnitTest` run skips these tests.

Install a debug build on a connected device:

```bash
./gradlew installDebug
```

## Signing a release

The release build is minified and **unsigned**, so you sign it with your own key:

```bash
# One-time: create a key
keytool -genkeypair -v -keystore my-release.jks -alias pingback \
  -keyalg RSA -keysize 4096 -validity 10000

# Sign and verify
$ANDROID_HOME/build-tools/<version>/zipalign -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk pingback-aligned.apk
$ANDROID_HOME/build-tools/<version>/apksigner sign --ks my-release.jks \
  --out pingback.apk pingback-aligned.apk
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs pingback.apk
```

Android only updates an installed app in place if the new APK is signed with the same key, so
keep your keystore safe.
