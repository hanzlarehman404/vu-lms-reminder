# 🎓 VU LMS Activity Reminder & Deadline Tracker

[![Android Build APK](https://github.com/hanzlarehman404/vu-lms-reminder/actions/workflows/build_apk.yaml/badge.svg)](https://github.com/hanzlarehman404/vu-lms-reminder/actions/workflows/build_apk.yaml)
[![Release](https://img.shields.io/badge/Release-v2.0.0-0D2F62.svg)](https://github.com/hanzlarehman404/vu-lms-reminder/releases)
[![Target Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-00A65A.svg)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An open-source, privacy-first Android client and academic deadline companion built exclusively for students of the **Virtual University of Pakistan (VU)** (currently **v2.0.0**).

VU LMS Reminder connects directly to `vulms.vu.edu.pk` without any middleman servers—synchronizing assignments, quizzes, GDBs, and fee challans, calculating true submission cutoffs, caching your official Student E-Card PDF offline, and delivering reliable local alarms alongside live GitHub announcement notices.

---

## 📌 Table of Contents
1. [Key Features](#-key-features)
2. [Smart Due Date Calculation & Pending Count](#-smart-due-date-calculation--pending-count)
3. [Daily Routine Alarms & Notification Ringtones](#-daily-routine-alarms--notification-ringtones)
4. [Live Announcements & Upcoming Notices](#-live-announcements--upcoming-notices)
5. [Continuous Full-Screen Alarm & System Permissions](#-continuous-full-screen-alarm--system-permissions)
6. [Fee Challans & Account Book](#-fee-challans--account-book)
7. [Student Portal & Offline E-Card](#-student-portal--offline-e-card)
8. [Developer Authenticity Verification](#-cryptographic-developer-verification)
9. [Architecture & Network Security](#-architecture--network-security)
10. [GitHub Actions CI Build Workflow](#-github-actions-ci-build-workflow)
11. [Building from Source](#-building-from-source)
12. [Developer, Issues & Contributions](#-developer-issues--contributions)

---

## 🌟 Key Features

- **Direct VU LMS Integration**: Authenticates directly with the official VU ASP.NET WebForms portal (`https://vulms.vu.edu.pk/LMS_LP.aspx`) via a privacy-first login experience (**Encrypted**, **Auto Sync**, **Privacy First**) with **Auto-Sync on App Launch** and manual dashboard refresh.
- **Personalized Dashboard & Live Countdown**: Greets the student by name (`Hi <Student Name>,`) directly above the **Nearest Deadline** countdown card.
- **Atomic Fresh Sync & Pending Badge**: Every sync cleanly updates local Room storage with no duplicate records; the **Available Now** tab badge displays only currently pending tasks.
- **Automatic LMS Completion Verification**: Scrapes your live ToDo list and paid challans so completed assignments, quizzes, and fees are marked automatically.
- **Activity Category Filters & Timeline**: Filter tasks by type (`All`, `Assignment`, `Quiz`, `GDB`, `Exam`) with live counts and direct **Open on LMS** launch links.
- **Custom VU + Bell Notification Icon & Audio Picker**: Displays a crisp monochrome **VU + Bell** status-bar icon across all notifications—tinted **App Info Cyan (`#36A3F7`)** for routine alerts and **VU Danger Red (`#F4516C`)** whenever deadlines are due today—alongside a system **Notification Ringtone** picker with inline **Preview / Stop**, **Reset to Default**, and silent vibration mode.
- **Live Announcements & Moving Dashboard Banner**: Fetches updates from the dedicated `notifications` branch (`notifications.json`) for a persistent moving Dashboard banner, configurable daily popups, **What's Upcoming & Announcements**, and status-bar tray notifications via a 5-minute background `WorkManager` refresh.
- **Live In-App GitHub Documentation**: Tapping **View Documentation & Details** in Settings fetches and renders the latest `README.md` straight from GitHub (with offline guide fallback).
- **Fee Challans & IPG Card Payment Support**: Dedicated **Fees** tab tracking Paid and Unpaid challans, printable vouchers, 1Bill instructions, and an in-app **VU Card Payment (IPG)** checkout dialog with **Open in Browser** handoff.
- **Student Profile & Offline Roll-Number E-Card**: Displays CGPA, Credits Earned, Enrolled Courses, and downloads a validated `%PDF-` E-Card saved permanently offline by roll number (`<ROLL_NO>.pdf`).
- **Developer Security & Network Hardening**: Verifies APK signing certificate SHA-256 against `update.json` using dynamic app version metadata, enforces HTTPS-only TLS verification (`usesCleartextTraffic="false"`), and excludes stored credentials from Android cloud backup and device transfer.
- **Customizable Themes**: Seamless **System**, **VU Light**, and **VU Dark** Material 3 themes.

---

## ⏳ Smart Due Date Calculation & Pending Count

In the raw VU LMS calendar feed (FullCalendar JSON), activity end dates use **exclusive** boundaries:

```json
{
  "title": "CS301: Assignment# 1",
  "start": "2026,10,29",
  "end": "2026,11,06"
}
```

- `"end": "2026,11,06"` means submission closes at `2026-11-06 00:00:00`—the actual student deadline is **2026-11-05 at 23:59:59**.
- VU LMS Reminder shifts exclusive cutoffs back by 1 day so deadlines and evening alarms fire accurately before the portal closes.
- The **Available Now** segmented pill badge counts only active **pending** items so completed history never inflates your remaining workload.

> ⚠️ **Browser Session Notice**: VU LMS permits only one active login session per student at a time. Whenever the app runs an automatic or manual LMS sync (including opening the app with **Auto-Sync on App Launch** enabled), your active VU LMS web browser session will be logged out.

---

## 🔔 Daily Routine Alarms & Notification Ringtones

The app coordinates three configurable daily routines via `AlarmManager`:

| Routine | Default Time | Purpose | Action Buttons |
| :--- | :--- | :--- | :--- |
| **☀️ Morning Briefing** | `09:00 AM` | Auto-syncs LMS, verifies completed tasks, and summarizes activities due today and tomorrow. | `Open LMS`, `Resync` |
| **🌤️ Mid-Day Activity Check** | `02:00 PM` | Reminds halfway through the day to finish available tasks. | `Open App`, `Resync` |
| **🌙 Evening Last Reminder** | `09:00 PM` | Resyncs LMS and triggers urgent alerts for pending tasks due before midnight. | `Open Activity`, `Resync`, `Snooze` |

### Custom VU + Bell Icon & Visual Tinting
- **Monochrome Status Bar Icon**: All notifications (deadlines, urgent alarms, morning briefings, mid-day checks, sync confirmations, and remote announcements) use a dedicated `24dp` monochrome `#FFFFFF` **VU + Bell** vector icon (`ic_notification_vu_bell`).
- **Dynamic Shade Tinting**:
  - **App Info Cyan (`#36A3F7`)**: Default accent for upcoming multi-day reminders, routine briefings with nothing due today, sync confirmations, and announcements.
  - **VU Danger Red (`#F4516C`)**: Automatically highlights any notification involving deadlines due today (`todayDueCount > 0`, `< 24h` remaining, due-today calendar date, or urgent alarms).

### Notification Audio & Ringtone Control
- **Enable Notifications**: Master switch for all scheduled briefings, reminders, and urgent alerts.
- **Full-Screen Evening Alarm**: Toggle between the waking full-screen alarm overlay and a regular heads-up shade notification.
- **Mute Notifications & Alarms**: Instantly silences notification channels and alarm audio while keeping vibration available.
- **Notification Ringtone**: Tap directly to choose any Android sound via the system ringtone picker (`RingtoneManager`), with inline **Preview / Stop** and **Reset to Default** actions. Notification channels rotate cleanly on Android 8.0+ so sound changes take effect immediately.

---

## 📣 Live Announcements & Upcoming Notices

Announcements, upcoming feature previews, and urgent notices are driven remotely from [`notifications.json`](https://github.com/hanzlarehman404/vu-lms-reminder/blob/notifications/notifications.json) on the `notifications` branch (documented in `NOTIFICATIONS_GUIDE.md`):

- **Foreground & 5-Minute Sync**: Refreshes whenever the app returns to the foreground, every 5 minutes while open, and via a 5-minute background `WorkManager` job when notification-bar alerts are configured.
- **Moving Dashboard Banner**: Displays active persistent notices across the top of the Home screen; tapping opens full announcement details.
- **Controlled Popups & Status Bar Alerts**: Supports `once_per_day`, `once_per_id`, `interval` (with configurable cooldown minutes), and `always` rules plus start/expiry dates and app version targeting.
- **What's Upcoming & Announcements**: Available in Settings right above **Check for Updates** to inspect notices manually anytime.

---

## 🚨 Continuous Full-Screen Alarm & System Permissions

- **Looping Alarm Ringtone**: `UrgentAlarmActivity` plays the device's default **Alarm** ringtone (`AudioAttributes.USAGE_ALARM`) with repeating vibration until dismissed, snoozed, or resolved via **OK & Resync Now**.
- **System Permissions & Reliability**: Settings provides scannable status rows (`Allowed` / `Allow`) with direct Android system shortcuts for:
  - **Battery Optimization** (Unrestricted background run)
  - **Auto-Run / Background Launch** (OEM startup managers)
  - **Exact Alarm Scheduling** (`SCHEDULE_EXACT_ALARM` on Android 12+)
  - **Full-Screen Lock-Screen Alarm** (`USE_FULL_SCREEN_INTENT` on Android 14+)

---

## 💳 Fee Challans & Account Book

A dedicated **Fees** tab (`AccountBookScreen`) keeps financial items separate from academic coursework:

- Parses all challans from `https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx`.
- Shows Challan ID, Challan Type, Payable/Paid Amount, Due Date, and Paid Date with **All / Unpaid / Paid** filters.
- Supports Challan Print Preview, **1Bill** voucher checkout, and an in-app **VU Card Payment (IPG)** WebView dialog featuring:
  - Compact two-row header displaying the **Challan #** and **PKR** amount cleanly without layout wrapping.
  - Direct **Open in Browser** action to continue bank checkout in the device browser.
  - Strict WebView SSL certificate validation (`handler.cancel()` on TLS errors) and graceful Chromium renderer crash/close lifecycle handling (`onRenderProcessGone` recovery & safe WebView disposal).

---

## 🆔 Student Portal & Offline E-Card

The **Student** tab (`StudentPortalScreen`) displays:

- **Student Academic Profile**: Student ID, Name, Study Program, CGPA, Effective CGPA, Credits Earned, and Current Semester (`CourseSelectionClosed.aspx`).
- **Current Enrolled Subjects**: Active semester course cards (`ProgressTimeline.aspx`).
- **Permanent Offline Student E-Card PDF**: Fetches from `PrintStudentECard.aspx`, verifies the `%PDF-` header to reject corrupted HTML login pages, automatically purges invalid caches, and saves permanently on-device by roll number (`<ROLL_NO>.pdf`) for instant offline viewing and sharing via `FileProvider`.
- **Direct VU Quick Links**: One-tap links to **VU Support System** and **VU Webmail**.

---

## 🛡️ Cryptographic Developer Verification

To guard against tampered third-party builds:

1. Tapping **"Verify & Sign In"** (or **Check for Updates**) fetches `https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/default/update.json`.
2. Compares the installed APK's signing certificate SHA-256 fingerprint via `PackageManager` against the official key:
   ```
   27:AD:82:EF:6C:AA:E3:B7:0D:2C:A3:D9:F8:45:E2:9B:AA:04:C0:A4:5D:2A:EE:F7:AC:ED:64:24:6B:0F:99:74
   ```
3. Displays live verification status (`BuildConfig.VERSION_NAME`), changelog notes, and release metadata before allowing login or directing users to official GitHub releases if mismatched.

---

## 🏗️ Architecture & Network Security

```
Android App (Jetpack Compose Material 3)
      │
      ├── ViewModel (StateFlow / Lifecycle-aware)
      │     └── VuLmsRepository
      │           ├── Room SQLite DB + PreferencesManager (Backup-Excluded Credentials)
      │           └── VuLmsNetworkService (OkHttp + Standard System TLS Verification)
      │                     ├── [HTTPS] https://vulms.vu.edu.pk
      │                     │     ├── ActivityCalendar / ToDo JSON
      │                     │     ├── AccountBook.aspx & IPG Payment WebView (Strict SSL)
      │                     │     ├── ProgressTimeline.aspx
      │                     │     ├── CourseSelectionClosed.aspx
      │                     │     └── PrintStudentECard.aspx (Validated PDF)
      │                     └── [HTTPS] GitHub Raw Feeds
      │                           ├── default/update.json & default/README.md
      │                           └── notifications/notifications.json
      │
      └── Background & Alert Layer
            ├── AlarmManager (Morning, Mid-Day, Evening exact alarms)
            ├── WorkManager (5-minute RemoteNotificationSyncWorker)
            └── NotificationHelper (VU + Bell Icon, Dynamic Channels) + UrgentAlarmActivity
```

### Transport & Local Credential Hardening
- **HTTPS-Only Network Policy**: `android:usesCleartextTraffic="false"` and `network_security_config.xml` (`cleartextTrafficPermitted="false"`) enforce HTTPS globally.
- **Standard TLS & Hostname Verification**: `VuLmsNetworkService` relies exclusively on Android/OkHttp default certificate chain and hostname verification (no trust-all bypasses or leaf certificate pinning), and the in-app Card Payment WebView cancels invalid TLS handshakes via `handler.cancel()`.
- **Scoped Android 12 Root Compatibility**: `network_security_config.xml` keeps Android's `<certificates src="system" />` store enabled across all domains (GitHub, payment gateways, and external services) while adding the SHA-256-verified **Sectigo Public Server Authentication Root R46** CA strictly as an additional trust anchor for `vu.edu.pk` so Android 12 devices validate VU LMS's certificate chain cleanly.
- **Backup & Transfer Exclusion**: Credential-containing preferences (`vu_lms_app_prefs.xml`) are excluded from Android cloud backups (`backup_rules.xml`) and device-to-device transfers (`data_extraction_rules.xml`).

---

## 🚀 GitHub Actions CI Build Workflow

Automated builds are defined in `.github/workflows/build_apk.yaml`:

- **Trigger**: Pushes and pull requests to `dev`, or manual `workflow_dispatch`.
- **Environment**: Ubuntu Latest with **JDK 21 (Temurin)** & **Android SDK 36**.
- **Output**: Builds, renames, uploads `vu-lms-reminder-app-v2.0.0.apk`, and updates the `v2.0.0` GitHub Release asset.
- **Version Releases**: See [`update.md`](update.md) for the step-by-step release checklist across `app/build.gradle.kts` (`versionCode` / `versionName`), `.github/workflows/build_apk.yaml`, and `update.json`.

---

## 💻 Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- **JDK 21**
- Android SDK Platform **36** (minSdk **24**)

### Build & Test Commands
```bash
# Clone the repository
git clone https://github.com/hanzlarehman404/vu-lms-reminder.git
cd vu-lms-reminder

# Build debug APK
gradle assembleDebug

# Run local unit & Robolectric Compose UI tests
gradle :app:testDebugUnitTest
```

The debug APK outputs to `app/build/outputs/apk/debug/app-debug.apk`.

---

## 👨‍💻 Developer, Issues & Contributions

- **Lead Developer**: [Hanzla Rehman](https://github.com/hanzlarehman404)
- **Repository**: [github.com/hanzlarehman404/vu-lms-reminder](https://github.com/hanzlarehman404/vu-lms-reminder)
- **Report a Bug or Issue**: [GitHub Issues](https://github.com/hanzlarehman404/vu-lms-reminder/issues)

Contributions, bug reports, and feature suggestions are warmly welcome! You can also report bugs directly from **Settings → Developer & Support → Report an Issue**.

---
*Disclaimer: This is an independent, open-source student utility and is not officially affiliated with or endorsed by the Virtual University of Pakistan.*
