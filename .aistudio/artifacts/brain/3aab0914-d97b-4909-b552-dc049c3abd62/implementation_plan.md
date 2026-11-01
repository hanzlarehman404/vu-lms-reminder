# VU LMS Activity Reminder App - Implementation Plan (Revised)

The **VU LMS Activity Reminder App** is a specialized Android application built to help Virtual University of Pakistan students never miss assignments, quizzes, GDBs, practicals, or fee challans. It authenticates directly with `https://vulms.vu.edu.pk` via ASP.NET WebForms session negotiation, extracts calendar activities, stores them offline in a local Room database, and triggers customizable alerts and urgent full-screen alarms.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following enhancements and requirements have been incorporated directly from user feedback:
> 1. **No Notifications for Past Deadlines**: Strict check to ensure alerts are never scheduled or fired for past or already-expired activities.
> 2. **Activity Completion & Auto-Suppression**: Unique hashing per task (`courseCode_title_endDate`) in the local Room database. Marking any task as "Completed" immediately cancels all pending alarms for it.
> 3. **Urgent Same-Day Full-Screen Alarm**: When an activity is due today and entering its final critical window (e.g., < 6 hours or 2 hours remaining), the app triggers an assertive full-screen alert dialog with audible sound, snooze, and direct LMS submission button.
> 4. **First-Launch Privacy & Transparency Disclosure**: A dedicated disclaimer dialog on initial setup emphasizing:
>    - Independent 3rd-party student tool (not an official VU app).
>    - **Zero-Data-Leak Guarantee**: Credentials and LMS data remain strictly on the user's device and never touch any external server.
> 5. **Founder Credit & Community Block**: Beautiful founder profile in About & Settings highlighting **Hanzla Rehman**, GitHub (`github.com/hanzlarehman404`), and avatar profile image (`https://avatars.githubusercontent.com/u/115634418?v=4`).
> 6. **Customizable Notification Preferences**: Settings for alert sound, vibration, advance intervals (7d, 3d, 1d, 12h, 2h, 1h), and urgent same-day alarm triggers.

---

### 1. Overview & Core Concept

- **What It Does**: Automates the multi-step ASP.NET WebForms handshake (`LMS_LP.aspx` GET hidden fields -> POST credentials -> cookie persistence -> `ActivityCalendar.aspx` GET -> JavaScript `JsonData` extraction -> Room database sync). Provides an interactive dashboard, calendar views, course progress, countdown timers, and urgent alarm reminders.
- **Target Audience**: VU students enrolled across CS, Management, English, and other faculties managing multiple simultaneous deadlines.
- **Key Value**: Guarantees deadlines are never missed, provides instant offline visibility into all course deadlines, and gives students peace of mind through locally scheduled alarms.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **First-Launch Onboarding & Privacy Disclaimer**:
   - Clean, professional welcome card outlining that this is an independent companion app for VU students.
   - Prominent security assurance: *"Your credentials and data never leave your phone. Direct connection to https://vulms.vu.edu.pk only."*
   - Acceptance gate before proceeding to login.
2. **Authentication Flow**:
   - Secure login screen requesting Student ID (e.g., `bc250417449`) and Password.
   - Real-time connection feedback during the 3-step ASP.NET handshake:
     - Step 1: Connecting to VU portal & retrieving security tokens (`__VIEWSTATE`).
     - Step 2: Authenticating student credentials.
     - Step 3: Fetching Activity Calendar and initializing sync.
3. **Dashboard Hub**:
   - **Hero Urgent Countdown Card**: Displays the nearest upcoming deadline with a live ticking countdown timer (days, hours, minutes, seconds) and color-coded urgency badge.
   - **Quick Stat Summary**: Overdue, Due Today, Due Tomorrow, Due This Week, Total Pending, and Completed.
   - **Category Filters**: All, Assignments, Quizzes, GDBs, Practicals, Challans.
   - **Activity Cards**: Course code chip (`CS301`), title, start/end badges, remaining time progress bar, one-tap mark-complete checkbox, and "Open in VU LMS" action.
4. **Interactive Calendar View**:
   - Monthly and Weekly interactive calendar grids.
   - Date cells with color-coded dot indicators matching activity categories.
   - Tapping any date filters the agenda list below for that specific day.
5. **Urgent Deadline Alarm & Notification Experience**:
   - **Advance Reminders**: High-priority heads-up notifications (e.g., 3 days, 1 day, 12 hours before).
   - **Urgent Day-Of Alarm**: When a deadline is within the critical hours window (e.g., 2–6 hours remaining on due date), triggers an assertive full-screen alarm screen with ringing audio, snooze (15m, 30m, 1h), and direct "Open LMS" button.
   - **Automatic Suppression**: Completed tasks and past dates are completely excluded from notifications.
6. **Founder Credit & Settings Screen**:
   - **Founder Card**: Features **Hanzla Rehman**, GitHub profile button (`github.com/hanzlarehman404`), founder photo, and greeting note.
   - **Sync Settings**: Auto-sync frequency (15m, 1h, 3h, 6h, 12h, 24h), manual "Sync Now" button, and last sync timestamp.
   - **Alarm & Notification Controls**: Enable/disable sound, vibration, select reminder intervals, and toggle urgent same-day alarms.
   - **Account Management**: Logged-in Student ID display and secure Logout (clears all cookies and stored credentials).

#### Visual Identity & Theme
- **Primary Color**: Deep VU Navy Blue (`#0D2F62`)
- **Secondary / Accent**: Emerald Green (`#00A65A`)
- **Category Indicators**:
  - *Assignment*: Flame Coral (`#F56954`)
  - *Quiz*: Forest Emerald (`#00A65A`)
  - *GDB*: Golden Amber (`#F39C12`)
  - *Practical*: Cyan Blue (`#00C0EF`)
  - *Challan*: Royal Purple (`#B833FF`)
  - *Other*: Slate Blue (`#605CA8`)
- **Typography & Components**: Modern Material 3 cards, rounded action pills, course code badges, and smooth animated transitions.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: OkHttp Client with CookieJar for ASP.NET WebForms**:
  - *Chosen Approach*: OkHttp `CookieJar` combined with dynamic regex extractors for `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, and `__EVENTVALIDATION`.
  - *Why*: Direct compliance with ASP.NET WebForms protocol without needing heavy WebView overhead.
- **Decision 2: Room Database with Composite Hash IDs & Completion State**:
  - *Chosen Approach*: Generate a unique MD5 hash from `courseCode_title_endDate` as primary key. When syncing new activities from LMS, existing completion statuses (`isCompleted`) and user custom reminders are preserved rather than overwritten.
- **Decision 3: Android AlarmManager & Notification Pipeline**:
  - *Chosen Approach*: Android `AlarmManager.setExactAndAllowWhileIdle` paired with a dedicated `AlarmBroadcastReceiver` and `UrgentAlarmActivity`. Guarantees on-time alerts even in Android Doze mode.
- **Decision 4: Coil for Remote Images & Fallbacks**:
  - *Chosen Approach*: Coil image loader for the founder GitHub avatar and LMS badges with graceful offline fallback icons.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             Jetpack Compose UI                             │
├─────────────────┬───────────────────┬───────────────────┬──────────────────┤
│   LoginScreen   │  DashboardScreen  │   CalendarScreen  │  SettingsScreen  │
│(Privacy Consent)│(Urgent Countdown) │(Month/Week Grid)  │ (Founder Card)   │
└────────┬────────┴─────────┬─────────┴─────────┬─────────┴────────┬─────────┘
         │                  │                   │                  │
         ▼                  ▼                   ▼                  ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    MainViewModel & ActivityViewModel                       │
│    - Observes Room DB StateFlows (Activities, Filters, Completed)          │
│    - Ticking countdown timer for nearest deadline                          │
│    - Coordinates Background Sync & Alarm Rescheduling                     │
└───────────────────────────────────┬────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                           ActivityRepository                               │
│  - Merges remote LMS JSON with local Room DB                               │
│  - Preserves isCompleted flags across sync updates                         │
│  - Schedules / Cancels Android AlarmManager events                         │
└───────────────┬────────────────────────────────────────────┬───────────────┘
                │                                            │
                ▼                                            ▼
┌────────────────────────────────┐           ┌───────────────────────────────┐
│         Room Database          │           │      VuLmsNetworkClient       │
│  - ActivityEntity              │           │  - OkHttpClient + CookieJar   │
│  - UserSession & EncryptedPrefs│           │  - AspNetWebFormsAuth         │
│  - AlarmScheduleEntity         │           │  - CalendarScraper & Parser   │
└────────────────────────────────┘           └───────────────────────────────┘
                │
                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                       System Alarm & Notification Layer                    │
│  - AlarmBroadcastReceiver: Dispatches heads-up notifications               │
│  - UrgentAlarmActivity: Full-screen alarm with sound, snooze, dismiss      │
│  - DailyBriefingReceiver: Morning summary of pending deadlines             │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Data Entities

1. **ActivityEntity**:
   - `id`: String (MD5 hash of `"${courseCode}_${title}_${endDate}"`)
   - `title`: String (e.g. `CS301: Assignment# 1`)
   - `courseCode`: String (e.g. `CS301`)
   - `semester`: String (e.g. `Fall 2026`)
   - `activityType`: String (`Assignment`, `Quiz`, `GDB`, `Practical`, `Challan`, `Other`)
   - `startDate`: Long (Epoch millis parsed from `YYYY,MM,DD` or `YYYY-MM-DD`)
   - `endDate`: Long (Epoch millis parsed from `YYYY,MM,DD` or `YYYY-MM-DD`)
   - `isExpired`: Boolean
   - `isCompleted`: Boolean (User toggled; cancels alarms when true)
   - `url`: String (Full URL to open activity directly in browser)
   - `colorHex`: String
   - `lastSyncedAt`: Long

2. **UserSession & AppPreferences**:
   - `studentId`: String
   - `encryptedPassword`: String
   - `isLoggedIn`: Boolean
   - `privacyPolicyAccepted`: Boolean
   - `syncIntervalMinutes`: Int (default: 60)
   - `advanceReminderDays`: Set (7d, 3d, 1d, 12h, 2h, 1h)
   - `urgentAlarmHoursBefore`: Int (default: 3 hours)
   - `urgentAlarmEnabled`: Boolean (default: true)
   - `alarmSoundEnabled`: Boolean (default: true)
   - `vibrationEnabled`: Boolean (default: true)
