# Trace

Every rupee leaves a trace. A small, private Android expense tracker built with Kotlin, Jetpack Compose and Room.

## Features
- **Home:** weekly, monthly, 6-month and yearly stacked-bar chart with a spring animation, category filters and recent expenses. Tap any expense to edit or delete it.
- **Add expense:** keypad entry, category, note and date.
- **Lock-screen quick-add:** a small notification and a Quick Settings tile open the add box over the lock screen, with no unlock needed. A launcher shortcut does the same from the home screen.
- **Voice add:** tap the mic and say "50 for vada pav". Trace fills in the amount, the note and the category. Speech is recognised on the phone (Android 12+ with an offline language installed); nothing is sent anywhere.
- **Insights:** highlights, what changed from last month, and monthly budgets per category.
- **Custom categories**, currency choice (₹ $ € £), and a friendly first-run onboarding and permissions screen.

**Private by design:** no account, no bank login. The app has no internet permission at all; everything is stored on your phone.

## Build
Requires JDK 21 (Android Studio's bundled JBR works).
```
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease       # app/build/outputs/bundle/release/app-release.aab (Google Play)
./gradlew testDebugUnitTest   # unit tests
```
Release builds are signed with the upload key in `keystore.properties` when it exists, and with the debug key otherwise.

## Project layout
```
app/src/main/java/com/homeboundlabs/trace/
  data/         Room database, repository, preferences
  quickadd/     lock-screen notification, tile, quick-add screen, voice input
  ui/           Compose screens: home, add, insights, onboarding, permissions, settings
app/schemas/    Room schema history (used for migrations)
```

## Support

Trace is free, with no ads and no sign-up. If it helps you, a coffee keeps it going ☕

<a href="https://ko-fi.com/hey_nikhilraj"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support Trace on Ko-fi" height="36"></a>

## License

Trace is free software, licensed under the [GNU General Public License v3.0](LICENSE).
You can use, study, change and share it; if you distribute a modified version, it must stay open source under the same licence.

Copyright © 2026 Nikhil Raj
