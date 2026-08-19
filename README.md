# CallPulse

CallPulse is a lightweight Android call statistics app for daily, weekly, and monthly call tracking.

CallPulse 是一款轻量的 Android 通话统计应用，支持日、周、月汇总以及周/月按天明细。

## Features

- Daily, weekly, and monthly call summaries
- Daily breakdown for the current week and month
- Incoming and outgoing call counts
- Configurable duplicate suppression window
- Optional short-number filtering
- Daily call target with notification reminder
- Light and dark theme support
- English and Chinese UI with automatic system-language selection
- No background service and no data upload

## Compatibility

- Android 8.0 (API 26) and above
- Target SDK 34
- Uses the standard Android Call Log provider
- Build-verified and checked on Xiaomi dark mode; other vendor devices use standard Android APIs but require device-specific validation

The app requires `READ_CALL_LOG` to read call history. Android 13 and above may also require `POST_NOTIFICATIONS` for target reminders. Vendor permission managers may require these permissions to be enabled manually.

The default UI is Chinese. When the device's preferred language is English, Android automatically loads the English resources. Other languages fall back to Chinese until a dedicated translation is added.

## Build

Requirements:

- JDK 17+
- Android SDK API 34
- Gradle 8.4 or Android Studio Hedgehog+

Build a debug APK:

```bash
./tools/gradle-8.4/bin/gradle :app:assembleDebug
```

On Windows:

```powershell
.\tools\gradle-8.4\bin\gradle.bat :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The local `tools/` directory is optional and is intentionally excluded from version control. Use a locally installed JDK, Android SDK, and Gradle when those tools are not present.

## Version Management

Application versions are managed in the root `gradle.properties` file:

```properties
app.versionCode=2
app.versionName=1.1.0
```

Increment `app.versionCode` for every release and update `app.versionName` at the same time.

## Release Signing

Signing credentials are never stored in the repository. Provide these environment variables when building a release:

```text
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

The local keystore is ignored by Git and must be backed up securely outside the repository.

## Statistics Rules

- A week starts on Monday.
- A month starts on the first day of the month.
- Only incoming and outgoing calls are counted.
- When enabled, numbers with fewer than seven digits are excluded.
- Duplicate suppression uses the configured time window per call type and number.
- Weekly and monthly details include dates with zero calls.

## Project Layout

```text
app/src/main/java/com/example/callcounter/
├── MainActivity.kt
├── data/model/
│   ├── CallDayStats.kt
│   ├── CallLogItem.kt
│   ├── CallStats.kt
│   └── PeriodStats.kt
└── util/
    ├── CallLogHelper.kt
    ├── NotificationHelper.kt
    └── PrefsHelper.kt
```

## Privacy

CallPulse reads call history locally and does not upload call records or personal data.

## Roadmap

- Home screen widget
- CSV/Excel export
- Custom blacklist
- Automated unit tests for date boundaries and deduplication rules
