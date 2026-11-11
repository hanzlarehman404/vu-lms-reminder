# 🎓 VU LMS Activity Reminder & Deadline Tracker

[![Android Build APK](https://github.com/hanzlarehman404/vu-lms-reminder/actions/workflows/build_apk.yaml/badge.svg)](https://github.com/hanzlarehman404/vu-lms-reminder/actions/workflows/build_apk.yaml)
[![Release](https://img.shields.io/badge/Release-v2.0.0-0D2F62.svg)](https://github.com/hanzlarehman404/vu-lms-reminder/releases)
[![Target Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-00A65A.svg)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An open-source, privacy-first Android client and academic deadline companion designed exclusively for students of the **Virtual University of Pakistan (VU)**.

VU LMS Reminder connects directly to `vulms.vu.edu.pk` without any middleman servers, synchronizing activities, calculating strict submission cutoffs, parsing enrolled courses, academic records, and fee challans, while scheduling local Android alarms for assignments, quizzes, Graded Discussion Boards (GDBs), and exams.

---

## 📌 Table of Contents
1. [Key Features](#-key-features)
2. [Smart Due Date Calculation](#-smart-due-date-calculation-exclusive-cutoffs)
3. [Notification & Routine Schema](#-three-tier-notification--alarm-schema)
4. [Continuous Full-Screen Alarm & Reliability](#-continuous-full-screen-alarm--battery-reliability)
5. [Fee Challans & Account Book](#-fee-challans--account-book)
6. [Student Portal & Digital E-Card](#-student-portal--digital-e-card)
7. [Developer Authenticity Verification](#-cryptographic-developer-verification)
8. [Architecture & Network Security](#-architecture--network-security)
9. [GitHub Actions Automated Build](#-github-actions-ci-build-workflow)
10. [Building from Source](#-building-from-source)
11. [Developer & Contributions](#-developer--contributions)

---

## 🌟 Key Features

- **Direct VU LMS Integration**: Authenticates directly with the official VU ASP.NET WebForms portal (`https://vulms.vu.edu.pk/Default.aspx`).
- **Atomic Fresh Sync (No Duplicates)**: Takes fresh data on every sync, atomically replacing older activities to eliminate duplicates and outdated states.
- **Automatic LMS Completion Verification**: Scrapes the student's live ToDo list to automatically detect submitted assignments and completed quizzes.
- **Activity Filter Badges**: Dashboard category tabs dynamically display live activity counts (e.g., `📋 All (6)`, `📝 Assignments (2)`, `⚡ Quizzes (3)`).
- **Interactive Activity Timeline**: Visual start-to-end timeline showing course codes, titles, and exact submission deadlines.
- **Fee Challans & Account Book**: Dedicated tab parsing all pending and paid semester fee challans directly from VU Account Book (`AccountBook.aspx`), keeping them separated from academic task deadlines.
- **Student Academic Profile & Enrolled Courses**: Live view of CGPA, Effective CGPA, Credits Earned, Current Semester, and enrolled subjects parsed from `ProgressTimeline.aspx`.
- **View & Download Official Student E-Card**: Download the student ID card PDF with native preview and Android share integration via secure `FileProvider`.
- **Direct VU Quick Links**: One-tap access to **VU Support System** (`https://www.vu.edu.pk/SupportSystem/`) and **VU Webmail** (`https://gmail.vu.edu.pk/`).
- **Continuous Looping Full-Screen Alarm**: Rings continuously until the student taps **"OK & Resync Now"**, which triggers a fresh sync and delivers a final confirmation notification.
- **Developer Security Verification**: Real-time cryptographic signing certificate verification before sign-in to protect students from tampered builds.
- **Zero Third-Party Data Collection**: Passwords and session cookies remain strictly on-device in Android private storage.
- **Customizable Themes**: Seamless system, VU Light, and VU Dark themes.

---

## ⏳ Smart Due Date Calculation (Exclusive Cutoffs)

In the raw VU LMS calendar feed (powered by FullCalendar JSON), activity end dates are specified **exclusively**:

```json
{
  "title": "CS301: Assignment# 1",
  "start": "2026,10,29",
  "end": "2026,11,06"
}
```

- In FullCalendar specification, `"end": "2026,11,06"` marks `2026-11-06 00:00:00` as the exclusive cutoff.
- The actual student deadline is **2026-11-05 at 23:59:59**. On November 6th, submission is closed.
- **How VU LMS Reminder Solves This**: The app parses end dates by subtracting 1 day (`cal.add(Calendar.DAY_OF_MONTH, -1)`), accurately displaying `2026-11-05` and scheduling alerts so students submit before the portal shuts down.
- **⚠️ Important Notice on VU LMS Submissions**: Due to a known quirk / minor bug in the VU LMS portal system, some course instructors or portal scripts occasionally accept submissions even after the posted due date has passed. **Always verify on the official VU LMS portal even after the due date**, as you might still have a window to submit your work!

---

## 🔔 Three-Tier Notification & Alarm Schema

To keep students on top of their coursework without battery drain, the app operates three configurable daily routines via `AlarmManager`:

| Routine | Default Time | Purpose | Action Buttons |
| :--- | :--- | :--- | :--- |
| **☀️ Morning Briefing** | `09:00 AM` | Auto-syncs LMS, verifies completed tasks, and delivers a summary of activities due today and tomorrow. | `Open LMS`, `Resync` |
| **🌤️ Mid-Day Activity Check** | `02:00 PM` | Asks: *"Have you done all of your available activities?"* Prompts students halfway through the day. | `Open App`, `Resync` |
| **🌙 Evening Last Reminder** | `09:00 PM` | Urgent check for any pending tasks due before midnight so no marks are lost. | `Open Activity`, `Resync`, `Snooze` |

> ⚠️ **Browser Session Notice**: Because VU LMS allows only one active web session per student, logging into the app during these sync times will invalidate active browser sessions. We recommend setting sync times when you are not actively taking timed tests in your browser.

---

## 🚨 Continuous Full-Screen Alarm & Battery Reliability

The app provides an intensive, non-ignorable alert system for high-stakes deadlines:

- **Continuous Looping Alarm**:
  Unlike simple short chime notifications, `UrgentAlarmActivity` utilizes Android's `MediaPlayer` on the `AudioAttributes.USAGE_ALARM` audio stream paired with a repeating hardware vibration waveform. It loops continuously until the student taps **"OK & Resync Now"**.
- **Post-Alarm Resync & Confirmation**:
  Tapping "OK & Resync Now" triggers a background synchronization with VU LMS. Once updated, the app issues a final confirmation notification confirming that your activities are up to date.
- **Full-Screen Alarm Setting**:
  Configurable under **"Full-Screen Alarm at Evening Last Reminder"** in Settings. If turned OFF, the app delivers a regular heads-up notification in the notification shade without hijacking the screen.
- **Background Reliability Helpers**:
  A dedicated settings card helps students whitelist the app from aggressive manufacturer battery optimizations (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and grant Exact Alarm scheduling permissions (`SCHEDULE_EXACT_ALARM`) on Android 12+.

---

## 💳 Fee Challans & Account Book

A dedicated **Fees** tab (`AccountBookScreen`) isolates financial records from academic milestones:

- Automatically fetches and parses all challans from `https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx`.
- Displays Challan ID, Description/Semester, Issue Date, Due Date, Total Payable Amount, and Paid Date.
- Filters between **All**, **Unpaid / Pending**, and **Paid** challans.
- Fee items in the raw calendar feed are automatically filtered out so your academic dashboard stays clutter-free.

---

## 🆔 Student Portal & Digital E-Card

A dedicated **Student** tab (`StudentPortalScreen`) displays a virtual student dashboard:

- **Student Academic Profile**: Displays Student ID, Name, Study Program, CGPA, Effective CGPA, Credits Earned, and Current Semester parsed from `CourseSelectionClosed.aspx`.
- **Current Enrolled Subjects**: Live list of active semester courses parsed from `ProgressTimeline.aspx`.
- **Official E-Card PDF**: Directly downloads the student's digital E-Card PDF from `PrintStudentECard.aspx` using session credentials. Offers native PDF viewing and sharing via `FileProvider`.
- **Direct VU Quick Links**: Direct links to **VU Support System** (`https://www.vu.edu.pk/SupportSystem/`) and **VU Webmail** (`https://gmail.vu.edu.pk/`).

---

## 🛡️ Cryptographic Developer Verification

To protect VU students against credential-stealing clones or modified APKs:

1. When clicking **"Verify & Sign In"**, the app queries `https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/main/update.json`.
2. It extracts the installed APK's cryptographic signing certificate using Android `PackageManager`.
3. It compares the SHA-256 fingerprint against the official developer key:
   ```
   27:AD:82:EF:6C:AA:E3:B7:0D:2C:A3:D9:F8:45:E2:9B:AA:04:C0:A4:5D:2A:EE:F7:AC:ED:64:24:6B:0F:99:74
   ```
4. **Verified**: Displays developer name (*Hanzla Rehman*), release version, and allows one-tap sign in.
5. **Tampered / Unmatched**: Warns the student immediately and directs them to the official GitHub repository to download a genuine APK.

---

## 🏗️ Architecture & Network Security

```
Android App (Jetpack Compose M3)
      │
      ├── ViewModel (StateFlow)
      │     └── Repository
      │           ├── Room SQLite DB (Atomic Refresh, Offline Cache)
      │           └── VuLmsNetworkService (OkHttp + Custom SSL)
      │                     │
      │                     ├── [TLS] Direct Connection ──► https://vulms.vu.edu.pk
      │                     │                                 ├── Calendar JSON
      │                     │                                 ├── AccountBook.aspx
      │                     │                                 ├── ProgressTimeline.aspx
      │                     │                                 ├── CourseSelectionClosed.aspx
      │                     │                                 └── PrintStudentECard.aspx
      │                     │
      │                     └── [TLS] GitHub Release API ──► update.json & APKs
      │
      └── Background Layer
            ├── AlarmManager (RTC_WAKEUP, exact alarm compat)
            ├── AlarmBroadcastReceiver
            └── NotificationHelper / UrgentAlarmActivity (Looping Audio)
```

### Android SSL Certificate Trust Anchor Resolution
The VU LMS server (`vulms.vu.edu.pk`) serves an intermediate/root certificate chain that Android's default trust store does not recognize. The app uses an SSL socket factory with permissive verification specifically scoped for `vulms.vu.edu.pk` to ensure smooth connection on all Android devices.

---

## 🚀 GitHub Actions CI Build Workflow

The repository includes an automated workflow at `.github/workflows/build_apk.yaml` that compiles, signs, and packages the production APK:

- **Trigger**: Pushes to `main`, tags (`v*`), or manual workflow dispatch.
- **Environment**: Ubuntu Latest with JDK 17 & Android SDK 36.
- **Signing**: Uses GitHub Secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
- **Artifact**: Uploads `vu-lms-reminder-v2.0.0.apk` ready for installation.

---

## 💻 Building from Source

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17
- Android SDK Platform 36

### Build Steps
```bash
# Clone the repository
git clone https://github.com/hanzlarehman404/vu-lms-reminder.git
cd vu-lms-reminder

# Build debug APK
gradle assembleDebug

# Run unit and Robolectric tests
gradle :app:testDebugUnitTest
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 👨‍💻 Developer & Contributions

- **Lead Developer**: [Hanzla Rehman](https://github.com/hanzlarehman404)
- **Repository**: [github.com/hanzlarehman404/vu-lms-reminder](https://github.com/hanzlarehman404/vu-lms-reminder)

Contributions, feature requests, and bug reports are warmly welcome! If you find this app helpful, please consider starring ⭐ the repository.

---
*Disclaimer: This is an independent, open-source student utility and is not officially affiliated with or endorsed by the Virtual University of Pakistan.*
