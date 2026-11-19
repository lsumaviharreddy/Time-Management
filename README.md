# TimeBlock

An Android app (Kotlin + Jetpack Compose) for arranging your day.

- **Plan**: daily timeline. Add tasks with a start time and duration, tap to edit, mark done. "Find next free slot" places a task in the first gap. Overlaps are flagged.
- **Reminder**: a notification 5 minutes before every task starts (change `LEAD_MIN` in `Reminders.kt`).
- **Focus**: Pomodoro timer (focus/break), optionally tied to a task. A notification fires when it ends, even if the app is closed.
- **Apps**: today's screen time for Instagram, YouTube, Kotatsu, Genshin Impact and Brawl Stars, with a 7-day chart and a daily timer for each. A timer gives a heads-up 5 min before it ends, then covers the app with a "Time's up" screen.
- **Google Calendar sync**: tap the sync icon on the Plan screen, allow calendar access and pick a calendar. Tasks become events there, and events you add or move in Google Calendar on your laptop show up in the app (with their 5-minute reminders). Recurring and all-day events are skipped.
- **Review**: weekly stats: tasks completed, focus time, planned vs focused chart.

## Install on your phone from GitHub

1. Push this folder to a GitHub repository (see below).
2. In the repo, go to **Releases** and download `TimeBlock.apk` on your phone.
3. Open the file. If Android asks, allow "Install unknown apps" for your browser. Play Protect may warn because the app isn't from the Play Store; choose "Install anyway".

GitHub builds the APK for you (`.github/workflows/build.yml`):
- Create a tag such as `v1.0` (Releases > Draft a new release > new tag `v1.0` > Publish, or `git tag v1.0 && git push origin v1.0`). The workflow builds and attaches `TimeBlock.apk`.
- Or open **Actions > Build APK > Run workflow** to build without tagging.
- Each new tag raises the version, and updates install over the old one because every build is signed with the same key (`app/release.keystore`). Keep the repo private if you don't want others to be able to sign apps with that key.

## Build yourself

1. Install Android Studio (Ladybug or newer) and open this folder.
2. Let Gradle sync, then press Run with your phone plugged in (USB debugging on) or an emulator.

Or from a terminal with the Android SDK installed:

    ./gradlew assembleDebug

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and open it to install.

## First run on the phone

- Allow notifications when asked.
- On Android 12+ tap "Allow exact alarms" in the banner so reminders arrive on the minute.
- Some phone makers (Xiaomi, Oppo, Vivo, Samsung) kill background apps: set battery use for TimeBlock to "Unrestricted".

## App screen time and timers (Apps tab)

Grant these once:
- **Usage access** (Settings > Special app access > Usage access > TimeBlock): needed to read screen time.
- **Display over other apps**: needed to show the block screen when a timer runs out.

If an app shows "Not installed", its package name differs on your phone. Edit `MonitoredApps` in `Usage.kt` and the `<queries>` list in `AndroidManifest.xml`.

## Google Calendar sync

- Create a calendar called **TimeBlock** in Google Calendar on your laptop (Other calendars > + > Create new calendar), then pick it in the app so your other events don't mix in.
- Add your Google account in phone Settings and keep Calendar sync on, or changes won't travel between phone and laptop.
- A finished task shows a check mark at the start of its event title. Add or remove the check mark in Google Calendar to mark a task done or not done.
- Turning sync off leaves the tasks in the app and the events in the calendar.
