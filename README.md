# TimeBlock

An Android app (Kotlin + Jetpack Compose) for arranging your day.

- **Plan**: daily timeline. Add tasks with a start time and duration, tap to edit, mark done. "Find next free slot" places a task in the first gap. Overlaps are flagged.
- **Reminder**: a notification 5 minutes before every task starts (change `LEAD_MIN` in `Reminders.kt`).
- **Study**: a stopwatch you start when you sit down to study. While it runs, TimeBlock's own reminders are paused and, if you allow it, Do Not Disturb is turned on so other apps' notifications are silenced too (alarms still ring). It switches back to how it was when you stop. Android asks for Do Not Disturb access once; without it only TimeBlock's reminders are paused.
- **Due**: submission dates. Save a deadline with a date, optional time and a note; the list shows days left and highlights overdue ones. You get a reminder the day before (9 AM) and on the day. With calendar sync on they become all-day "Due: ..." events, so the web version shows them too.
- **Apps**: today's screen time for Instagram, YouTube, Kotatsu, Genshin Impact and Brawl Stars, with a 7-day chart and a daily timer for each. A timer gives a heads-up 5 min before it ends, then covers the app with a "Time's up" screen.
- **Repeating tasks**: choose Daily, Weekly (pick days, or Weekdays), or Monthly when adding a task, with an optional end date. Manage them from the menu (three dots) > Repeating tasks. Open one to change it for every day, skip a single day, or delete the series. Each occurrence has its own done tick and its own 5-minute reminder. With calendar sync on, they become repeating events in Google Calendar and back.
- **Day, Week and Month views**: switch with the chips under the date. Tap an empty spot in the day or week grid to add a task at that time, or use Add task under the month grid. Tap a day in the week header or the month grid to open it.
- **Background colour**: menu (three dots) > Background colour: sky blue, orange, red, gray, black or white.
- **Google Calendar sync**: open the three-dot menu on the Plan screen > Google Calendar sync, allow calendar access and pick a calendar. Tasks become events there, and events you add or move in Google Calendar on your laptop show up in the app (with their 5-minute reminders). Repeating events with other patterns (every 2 weeks, yearly, "first Monday", a set number of repeats) and all-day events are skipped.
- **Review**: weekly stats: tasks completed, study time, planned vs studied chart.

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
- Repeating tasks sync as repeating events. Skipping a day in the app excludes that date in the calendar, and deleting or changing a single day in Google Calendar hides it in the app. Done ticks on single days of a repeating task stay in the app.
- A finished task shows a check mark at the start of its event title. Add or remove the check mark in Google Calendar to mark a task done or not done.
- Turning sync off leaves the tasks in the app and the events in the calendar.

## Use it on your laptop (web version)

The `docs/` folder is a web app with the same day, week and month views, repeating tasks and background colours. It reads and writes your Google Calendar, so the phone app and the laptop always show the same tasks. Screen time and app timers stay phone-only.

### 1. Turn on GitHub Pages (once)

1. Push this repo to GitHub.
2. Repo **Settings → Pages**. Under *Build and deployment*, set **Source: Deploy from a branch**, **Branch: main**, **Folder: /docs**, then Save.
3. After a minute the site is live at `https://<your-username>.github.io/<repo-name>/` (for example `https://lsumaviharreddy.github.io/Time-Management/`).

### 2. Create a Google sign-in key (once, free)

1. Go to <https://console.cloud.google.com/> and create a project (any name, for example *TimeBlock*).
2. **APIs & Services → Library**, search for **Google Calendar API**, click **Enable**.
3. **APIs & Services → OAuth consent screen** (may be called *Google Auth Platform*): choose **External**, fill in the app name (TimeBlock) and your email, and save. Under **Audience / Test users**, add your own Google address. Leave the app in *Testing* mode; that is fine for personal use.
4. **Credentials → Create credentials → OAuth client ID**:
   - Application type: **Web application**
   - **Authorized JavaScript origins**: add `https://lsumaviharreddy.github.io` (just the domain, no path and no trailing slash). Add `http://localhost:8000` too if you want to test locally.
   - Create, then copy the **Client ID** (it ends in `.apps.googleusercontent.com`).
5. Open your site, paste the Client ID on the first screen (or put it in `docs/config.js`), and press **Sign in with Google**. Google will warn that the app is unverified; choose **Continue**. That is expected for your own test app.

In *Testing* mode Google signs you out about every 7 days; just press Sign in again.

### 3. Use the same calendar on both devices

Pick the calendar named **TimeBlock** (create it in Google Calendar first, as for the phone app). The web app selects it automatically if it exists; change it under **⋮ → Google Calendar**. In the Android app choose the same calendar in the sync dialog.

### Notes

- Repeating tasks are written as normal repeating Google Calendar events. Skipping one day deletes just that occurrence. Custom patterns made in Google Calendar (every 2 weeks, "first Monday") show up but their pattern can only be changed in Google Calendar.
- "Completed" adds a ✓ to the title, the same as the Android app.
- Tap or click an empty spot on the timeline to add a task at that time.
- ⋮ → Reminders in this tab gives a notification 5 minutes before a task while the page is open. For reminders when the page is closed, rely on the phone app or Google Calendar's own notifications.
- Install as an app: in Chrome or Edge, use the install icon in the address bar.
- To try it locally: `cd docs && python -m http.server 8000`, then open <http://localhost:8000>. **Demo mode** works without any Google setup.

## Take-with-me lists, double-tap and the week view

- **Take with me**: when you add or edit a task, fill in "Take with me" (for example `charger, ID card`). The list is shown in the 5-minute reminder, on the task and in the month list. It syncs through the event description as a `Take with me: ...` line.
- **Double-tap a task** (double-click on the laptop) to mark it done, or not done. A single tap opens it.
- **Week view** merges tasks that have the same title on the same day into one block, for example `Study ×3`. Tap it to see and edit each one, or double-tap to mark them all done.
