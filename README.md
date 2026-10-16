# Trace

**Every rupee leaves a trace.** A light, quick expense tracker for Android.

Website: https://nraj07054.github.io/trace/

## What it does

- **Lock-screen add:** one small notification on your lock screen. Tap it, type the amount, pick a category, done. The phone stays locked.
- **A chart you can tap:** your week as stacked, springy bars. Switch between week, month, 6 months and year, or filter by category.
- **Your own categories:** Food, Travel, Shopping, Bills and Fun built in; add your own with a name, colour and icon.
- **Insights:** plain-language highlights, what changed vs last month, and monthly budgets per category.
- **Private by design:** no account, no bank login. The app has no internet permission at all; everything is stored on your phone.

## Download

Get the APK from the [latest release](https://github.com/nraj07054/trace-app/releases/latest). Android 8.0 or newer.

### Verify the download

Each release lists the APK's SHA-256. Check it before installing:

```bash
# macOS
shasum -a 256 Trace.apk
# Linux
sha256sum Trace.apk
# Windows (PowerShell)
Get-FileHash Trace.apk -Algorithm SHA256
```

v1.0.0: `1b9ee665180037012b3cc15ce82750bd974e855b8379903d4423ba74d5d9cded`

## Build it yourself

Requirements: Android SDK (platform 36) and JDK 17–21.

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # minified build
```

Tech: Kotlin, Jetpack Compose (Material 3), Room. No third-party analytics or ads in the app.

## License

Trace is free software, licensed under the [GNU General Public License v3.0](LICENSE).
You can use, study, change and share it; if you distribute a modified version, it must stay open source under the same licence.

Copyright © 2026 Nikhil Raj
