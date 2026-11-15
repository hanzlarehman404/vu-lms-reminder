# `notifications.json` Complete Reference & Safe-Editing Guide

The app fetches this file from your **`notifications`** branch:
- **GitHub Edit URL:** `https://github.com/hanzlarehman404/vu-lms-reminder/blob/notifications/notifications.json`
- **Raw Feed URL Used by App:** `https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/notifications/notifications.json`

> **Important JSON Rule:** Standard `.json` files **cannot** contain `//` comments or trailing commas `,`. Always use this guide as your reference and keep `notifications.json` strictly valid JSON so parsing never breaks.

---

## 1. Top-Level Structure

Every `notifications.json` file must be a JSON object with a `"notifications"` array:

```json
{
  "notifications": [
    {
      "id": "unique-id-here",
      "active": true
    }
  ]
}
```

To temporarily turn off all notices without breaking the app, set `"active": false` on items or use an empty array:

```json
{
  "notifications": []
}
```

---

## 2. All Allowed Fields & Inputs

| Field | Type | Default | Allowed Values / Rules |
| --- | --- | --- | --- |
| `"id"` | `String` | `""` *(Required)* | Unique ID per announcement (e.g., `"2026-11-16-v21"`). Tracks popup cooldowns (`once_per_day` / `once_per_id`). Changing `id` treats it as a brand-new notice. **If blank, the item is ignored.** |
| `"active"` | `Boolean` | `true` | `true` to enable, `false` to hide immediately from banner, popup, and Settings list. |
| `"showBanner"` | `Boolean` | `true` | `true` shows the item in the persistent moving Home screen marquee banner. `false` hides it from the moving banner. |
| `"showPopup"` | `Boolean` | `true` | `true` triggers the automatic popup dialog on app open. `false` prevents the automatic popup (item still shows in banner / Settings if active). |
| `"popupFrequency"` | `String` | `"once_per_day"` | `"once_per_day"` *(shows once per local calendar day)*, `"once_per_id"` *(shows only once ever for that `id`)*, or `"always"` *(shows every app launch)*. |
| `"priority"` | `Integer` | `0` | Higher numbers appear first (`100` > `50` > `0`). Highest priority controls the main banner color/badge and wins the popup slot. |
| `"severity"` | `String` | `"info"` | Controls theme color, icon, and default badge: `"info"`, `"major_update"`, `"warning"`, or `"critical"`. |
| `"badge"` | `String` | `""` | Custom pill label (automatically uppercased, e.g., `"COMING SOON"`, `"EXAM ALERT"`). Leave `""` to use the severity default. |
| `"title"` | `String` | `""` | Bold headline in the popup dialog and Settings list. *(At least one of `title`, `message`, or `bannerText` must be non-empty.)* |
| `"bannerText"` | `String` | `""` | Optional single-line ticker text specifically for the moving Home banner. If blank `""`, the app uses `"<title> — <message>"`. |
| `"message"` | `String` | `""` | Main body text displayed inside the popup dialog and **What's Upcoming** sheet. |
| `"highlights"` | `Array<String>` | `[]` | Optional bullet-point strings displayed with checkmarks inside a highlighted card. Use `[]` if not needed. |
| `"actionLabel"` | `String` | `""` | Button text when `"actionUrl"` is provided (defaults to `"Learn More"` / `"Open Link"` if blank). |
| `"actionUrl"` | `String` | `""` | Full `"https://..."` link opened in the user's browser when tapped. Leave `""` to omit the CTA button. |
| `"startsAt"` | `String` | `""` | Optional UTC start date/time. Item stays hidden until this time. Leave `""` to start immediately. |
| `"expiresAt"` | `String` | `""` | Optional UTC end date/time. Item stops showing automatically after this time. Leave `""` for no expiration. |
| `"minVersionCode"` | `Integer` or `null` | `null` | Optional minimum app `versionCode` (e.g., `2`). Hidden on older versions. Use `null` for all versions. |
| `"maxVersionCode"` | `Integer` or `null` | `null` | Optional maximum app `versionCode`. Hidden once a student updates above this `versionCode`. Use `null` for all versions. |

---

## 3. Severity Styles & Default Badges

| `"severity"` Value | Color Accent | Icon | Default `"badge"` (when `""`) | Best Used For |
| --- | --- | --- | --- | --- |
| `"major_update"` | Emerald Green | Rocket | `UPCOMING` | Upcoming major app releases, feature previews |
| `"info"` | Electric Cyan | Sparkle | `NOTICE` | General tips, routine reminders, community links |
| `"warning"` | Warm Amber | Warning | `ADVISORY` | LMS maintenance windows, exam/date-sheet warnings |
| `"critical"` | Coral Red | Megaphone | `URGENT` | Critical VU LMS outages, urgent bug fixes |

---

## 4. Accepted Date & Time Formats (`"startsAt"` & `"expiresAt"`)

All dates are evaluated safely in **UTC** (compatible with Android 7.0+):

| Format | Example | Behavior |
| --- | --- | --- |
| `YYYY-MM-DD` *(Easiest)* | `"2026-11-25"` | For `startsAt`: begins `00:00:00Z`. For `expiresAt`: stays active all day until `23:59:59Z`. |
| `YYYY-MM-DDTHH:MM:SSZ` | `"2026-11-25T18:30:00Z"` | Exact UTC ISO-8601 timestamp. |
| `YYYY-MM-DDTHH:MM:SS.SSSZ` | `"2026-11-25T18:30:00.000Z"` | ISO-8601 with milliseconds. |
| `YYYY-MM-DDTHH:MMZ` | `"2026-11-25T18:30Z"` | ISO-8601 without seconds. |
| `YYYY-MM-DD HH:MM:SS` | `"2026-11-25 18:30:00"` | Space-separated UTC datetime. |
| `""` *(Empty String)* | `""` | No start or end limit. |

---

## 5. Copy-Paste Recipes

### A. Moving Banner Only (Persistent Ticker, No Popup)
```json
{
  "notifications": [
    {
      "id": "2026-11-16-lms-maintenance",
      "active": true,
      "showBanner": true,
      "showPopup": false,
      "priority": 90,
      "severity": "warning",
      "badge": "LMS NOTICE",
      "title": "VU LMS Scheduled Maintenance",
      "bannerText": "VU LMS may be slow tonight 11:00 PM – 1:00 AM PKT due to server maintenance.",
      "message": "If syncing fails during this maintenance window, wait until 1:00 AM PKT and tap Sync Now.",
      "highlights": [],
      "actionLabel": "",
      "actionUrl": "",
      "startsAt": "2026-11-16",
      "expiresAt": "2026-11-17"
    }
  ]
}
```

### B. Once-Per-Day Popup Only (No Home Banner)
```json
{
  "notifications": [
    {
      "id": "2026-11-16-datesheet-reminder",
      "active": true,
      "showBanner": false,
      "showPopup": true,
      "popupFrequency": "once_per_day",
      "priority": 80,
      "severity": "info",
      "badge": "REMINDER",
      "title": "Make Your Date Sheet Now!",
      "message": "VU Fall date sheet portal is open. Reserve preferred exam slots early before centers fill up.",
      "highlights": [
        "Verify your pending fee challans first",
        "Check Student tab for your E-Card after booking"
      ],
      "actionLabel": "Open VU Date Sheet",
      "actionUrl": "https://datesheet.vu.edu.pk",
      "expiresAt": "2026-11-22"
    }
  ]
}
```

### C. Different Items for Banner & Popup at the Same Time
```json
{
  "notifications": [
    {
      "id": "banner-major-v21",
      "active": true,
      "showBanner": true,
      "showPopup": false,
      "priority": 100,
      "severity": "major_update",
      "badge": "COMING SOON",
      "title": "Version 2.1 Preview",
      "bannerText": "Major Update v2.1 coming soon with Date Sheet Reminders & faster sync!",
      "message": "Stay tuned in Settings -> What's Upcoming for release details.",
      "expiresAt": "2026-11-30",
      "maxVersionCode": 2
    },
    {
      "id": "popup-midterm-alert",
      "active": true,
      "showBanner": false,
      "showPopup": true,
      "popupFrequency": "once_per_day",
      "priority": 90,
      "severity": "critical",
      "badge": "URGENT",
      "title": "Midterm Quiz Deadlines Tonight",
      "message": "Double-check your Pending filter before 11:59 PM so no quiz is missed.",
      "expiresAt": "2026-11-18"
    }
  ]
}
```

---

## 6. Quick Checklist Before Committing to GitHub

1. Keep the outer `{ "notifications": [ ... ] }` wrapper intact.
2. Give every new announcement a fresh `"id"` if you want `once_per_id` popups to trigger again or want clean daily tracking.
3. No comments (`//` or `/* */`) and no trailing commas after the last property or array item.
4. Set `"active": false` instead of deleting an item if you want to keep it ready for reuse later.
5. Check your changes inside the app immediately using **Settings → What's Upcoming & Announcements**.
