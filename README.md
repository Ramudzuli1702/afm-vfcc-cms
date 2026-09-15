# AFM VFCC Church Management System
### Desktop app (Java 22 + JavaFX + MySQL) with an Android companion app for attendance

---

## ✅ What's Included

| File count | Category |
|---|---|
| 24 Java controllers (+ 2 documentation content classes) | `src/main/java/com/afmvfcc/controllers/` |
| 10 Java models | `src/main/java/com/afmvfcc/models/` |
| 15 Java utilities | `src/main/java/com/afmvfcc/utils/` |
| 1 DB connection class | `src/main/java/com/afmvfcc/db/` |
| 1 embedded REST API | `src/main/java/com/afmvfcc/api/` — serves the Android app, see [Android App](#-android-app) |
| 1 Main entry point | `src/main/java/com/afmvfcc/` |
| 19 FXML layouts | `src/main/resources/com/afmvfcc/fxml/` |
| 1 CSS stylesheet | `src/main/resources/com/afmvfcc/css/` |
| 1 reference schema | `afm_vfcc_setup.sql` (kept for documentation — the app builds its own schema automatically, see below) |
| 1 Android companion app | `android-app/` — see [Android App](#-android-app) |

---

## 🚀 Try the Demo

Want to see it running before setting anything up yourself? Grab the latest
release: **[github.com/Ramudzuli1702/afm-vfcc-cms/releases](https://github.com/Ramudzuli1702/afm-vfcc-cms/releases)**

It includes:
- **`AFM_VFCC_CMS_Setup.exe`** — the full Windows installer (bundles MySQL
  Server and its own Java runtime, nothing else to install)
- **`app-debug.apk`** — the Android companion app (sideload it, or install
  via Android Studio from `android-app/`)
- **`demo_data.sql`** — ~40 fictional members, a handful of welfare cases,
  calendar events, and inventory items, so the system isn't empty on first
  look. **Entirely made-up data — no real church or member information.**
  After installing, run the first-time setup wizard, then in the CMS go to
  **Settings → Backup → Restore Backup…** and select this file.
  Demo logins: `demo_admin` / `Demo@1234` (desktop + app) and
  `demo_usher` / `Usher@1234` (mobile app only) — change or remove these
  before using the system for real.

---

## 🛠 Prerequisites

**For a client / production install:** none. Run `gradle createFullInstaller`
(see [Distribution](#-distribution--building-a-client-installer) below) to
produce `AFM_VFCC_CMS_Setup.exe`, which silently installs MySQL Server if the
client machine doesn't have it, and needs no separate Java install at all —
the app installer bundles its own Java runtime.

**For development**, install:

1. **Java 22 JDK** — https://adoptium.net
   Verify: `java -version` → should show `22`

2. **Gradle 8.7** — https://gradle.org/install
   Verify: `gradle -version`

3. **MySQL 8+** running locally
   Ensure it's running: `mysqladmin -u root -p status`

4. **VS Code extensions** (already noted):
   - Extension Pack for Java (Microsoft)
   - Gradle for Java (Microsoft)

---

## 🗄 Database Setup

Nothing to run manually — the app sets up its own database. The first time
it launches against a MySQL server with no `AFM_VFCC_CMS` database yet, it
shows a **Database Setup** wizard (`DatabaseConnection.java`) that:

1. Asks once for your MySQL **root** password (used only for this one-time
   setup — it is never stored).
2. Creates the `AFM_VFCC_CMS` database and a dedicated, least-privilege
   `afm_app` MySQL user with a password you choose there and then.
3. Creates every table.
4. Walks you through creating the first admin account.
5. Saves the connection details (host/user/password) to a local config file
   — see `DatabaseConnection.getConfigPath()` (`%APPDATA%\AFM_VFCC_CMS\db.properties`
   on Windows) — which is never committed to source control and is specific
   to that one machine.

`afm_vfcc_setup.sql` in the project root is kept only as a readable reference
of the schema; you don't need to run it.

---

## 🚀 How to Run

### Option A — VS Code
1. Open the `AFM_VFCC_CMS` folder in VS Code
2. Wait for Gradle to sync (bottom status bar)
3. Press `Ctrl+Shift+P` → `Gradle: Run a Gradle Task`
4. Select `run`
5. The login window will appear (or the Database Setup wizard, on first run)

### Option B — Terminal
```bash
cd AFM_VFCC_CMS
gradle run
```

### Option C — Build a JAR
```bash
gradle jar
java -jar build/libs/AFM_VFCC_CMS-1.0.0.jar
```

---

## 🔐 First Login

There is no fixed default account — the Database Setup wizard (above) makes
you create the first admin account, with a username and password of your
choosing, the first time the app runs against a fresh database.

---

## 📦 Distribution — building a client installer

To hand a client a single installer that also takes care of installing
MySQL Server if they don't already have it:

```bash
gradle createFullInstaller
```

This produces `build/dist/AFM_VFCC_CMS_Setup.exe`. See
[`installer/README.md`](installer/README.md) for how it works and what it
needs on the build machine (the WiX Toolset).

---

## 📁 Project Structure

```
AFM_VFCC_CMS/
├── build.gradle                    ← Dependencies & build config
├── settings.gradle
├── afm_vfcc_setup.sql               ← Reference schema (app builds this itself, see Database Setup)
├── installer/                       ← Bundled MySQL + WiX Burn setup .exe, see installer/README.md
├── android-app/                     ← Android companion app (Kotlin/Compose), see Android App below
└── src/main/
    ├── java/com/afmvfcc/
    │       ├── Main.java                       ← App entry point
    │       ├── db/
    │       │   └── DatabaseConnection.java     ← MySQL connection + first-run setup wizard
    │       ├── api/
    │       │   └── CmsApiServer.java           ← Embedded REST API (Javalin) for the Android app
    │       ├── models/
    │       │   ├── User.java
    │       │   ├── Member.java
    │       │   ├── Event.java
    │       │   ├── BoardMeeting.java
    │       │   ├── AttendanceSession.java
    │       │   ├── WelfareCase.java
    │       │   ├── WebsiteBlog.java
    │       │   ├── WebsiteEvent.java
    │       │   ├── InventoryItem.java          ← Instruments, office equipment, building materials
    │       │   └── AuditEntry.java
    │       ├── utils/
    │       │   ├── PasswordUtil.java           ← BCrypt hashing
    │       │   ├── SessionManager.java         ← Login session + 15min timeout
    │       │   ├── AuditLogger.java            ← Writes to audit_log table
    │       │   ├── AnnouncementsService.java   ← Posts announcements (WhatsApp/Facebook)
    │       │   ├── BroadcastTaskManager.java   ← Background state for Facebook→YouTube jobs
    │       │   ├── EmailService.java           ← SMTP sending
    │       │   ├── SmsService.java             ← BulkSMS sending
    │       │   ├── GitHubSync.java             ← Publishes website content via GitHub Contents API
    │       │   ├── WebsiteExporter.java        ← Generates data.js from the DB for the website
    │       │   ├── YouTubeUploader.java        ← YouTube Data API v3 upload (OAuth)
    │       │   ├── YtdlpUpdater.java           ← Auto-updates bundled yt-dlp every ~90 days
    │       │   ├── NetworkUtils.java           ← Finds the LAN/Mobile-Hotspot IP for the Android app
    │       │   ├── QrCodeGenerator.java        ← Renders a connect-QR (ZXing) for the Android app
    │       │   ├── ToastManager.java           ← App-wide Success/Failed toast notifications
    │       │   └── NavigationBus.java          ← Lets any page jump the sidebar (e.g. clickable Dashboard cards)
    │       └── controllers/
    │           ├── LoginController.java        ← Auth + lockout
    │           ├── MainLayoutController.java   ← Sidebar navigation + toast layer
    │           ├── DashboardController.java    ← Stats + reminders
    │           ├── MembersController.java      ← Full CRUD + search + filter + Guests/Families/Pending/Faithful Departed tabs
    │           ├── AddEditMemberController.java← Add/Edit member dialog
    │           ├── GuestsController.java       ← Guests tab (synced from the Android app)
    │           ├── DeceasedMembersController.java← "Faithful Departed" tab
    │           ├── MemberPdfExporter.java      ← iText7 PDF generation
    │           ├── DocumentExporter.java       ← Shared branded PDF export helper
    │           ├── AttendanceController.java   ← Sessions + marking + Excel export + mobile-connect QR
    │           ├── BoardController.java        ← Board members + meetings + minutes
    │           ├── CalendarController.java     ← Church events CRUD, past days greyed out and locked
    │           ├── WelfareController.java      ← Welfare cases + workers
    │           ├── WelfarePdfExporter.java     ← PDF export for welfare cases
    │           ├── InventoryController.java    ← Instruments/equipment/materials CRUD + Excel export
    │           ├── CommunicationsController.java← Compose/send Email, SMS & Announcements; Sent Log
    │           ├── WebsiteController.java      ← Website blogs + events
    │           ├── BroadcastController.java    ← Downloads a Facebook Live video, re-uploads to YouTube
    │           ├── CommemorationsController.java← Issues certificates (baptism, blessing, appreciation, death)
    │           ├── CertificateGenerator.java   ← PDF certificate rendering engine
    │           ├── SettingsController.java     ← Backup/restore, website/GitHub, SMTP/BulkSMS, mobile app connect QR, connected devices
    │           ├── AdminsController.java       ← Super admin: manage admins & ushers
    │           ├── AuditLogController.java     ← Read-only audit trail
    │           ├── DocumentationController.java← Exports the User Guide (PDF/Word) and System Usage Policy (PDF)
    │           ├── UserGuideContent.java       ← User Guide copy (rendered with the letterhead)
    │           ├── PolicyContent.java          ← Usage Policy copy (rendered plain, no letterhead)
    │           └── FinanceController.java      ← Placeholder (future)
    └── resources/com/afmvfcc/
        ├── css/
        │   └── styles.css                      ← Global dark gold theme
        └── fxml/
            ├── login.fxml
            ├── main_layout.fxml
            ├── dashboard.fxml
            ├── members.fxml
            ├── add_edit_member.fxml
            ├── attendance.fxml
            ├── board.fxml
            ├── calendar.fxml
            ├── welfare.fxml
            ├── inventory.fxml
            ├── communications.fxml
            ├── website.fxml
            ├── broadcast.fxml
            ├── commemorations.fxml
            ├── settings.fxml
            ├── admins.fxml
            ├── audit_log.fxml
            ├── documentation.fxml
            └── finance.fxml
```

---

## 🔧 Module Guide

### Notifications
Every add/edit/delete/send action across the system (members, events,
meetings, welfare cases, admins, announcements, inventory, etc.) shows a
"Success" or "Failed" toast in the bottom-right corner — no separate
confirmation dialogs to dismiss. Handled by `ToastManager`, attached once at
startup in `MainLayoutController`.

### Login
- BCrypt password verification
- Account lockout after 5 failed attempts (permanent until super admin unlocks)
- Enter key works on both fields
- Session timeout: 15 minutes of inactivity → auto-logout

### Dashboard
- Stat cards pulled live from the DB — click one to jump straight to the related module
- A ministry-involvement bar chart, a 6-week attendance line chart, and a Welfare Cases by
  Status pie chart (click it to jump to Welfare)
- A Recent Activity feed showing the latest actions across the system, pulled from the Audit Log
- Upcoming events in next 7 days, birthdays this week, pending welfare cases, and pending
  member approvals with a count badge

### Members
Seven tabs in one module: **All Members** (full CRUD with soft delete, search
by name/phone/email, filter by status/sub-branch/ministry, multi-ministry
assignment via checkboxes, PDF export via iText7), **Sub-Branches**,
**Ministries**, **Families** (family unit creation and linking), **Guests**
(synced from the Android app — promote to Pending Review or dismiss),
**Pending Review** (new member registrations awaiting approval, from the
Android app), and **Faithful Departed** (deceased members record-keeping).

### Attendance
- Create named sessions with date + ministry
- Tap a row to toggle Present/Absent
- Mark All Present / Clear All buttons
- Per-session Excel export via Apache POI
- Session history with delete
- Shows the server address the Android app should connect to (prefers the
  Windows Mobile Hotspot adapter when it's on) with a QR code — tap it to
  enlarge for scanning, see [Android App](#-android-app)
- **Sync with App** button refreshes the current session from the database,
  so attendance/guest records an usher just synced from their phone show up
  immediately without reselecting the session
- Sessions and attendance can also be taken from the Android app and synced
  back here

### Church Board
- Board member roles with start/end dates
- Meetings with typed agenda + minutes
- Meeting attendee count
- Searchable history

### Calendar
- Church events (separate from website events)
- Category filter: Service, Meeting, Outreach, Youth, Other
- Past days are greyed out and locked — no new events can be added on a date
  that has already passed
- Feeds the Dashboard 7-day reminder

### Welfare
- Cases linked to members + welfare workers
- Status: Pending → In Progress → Completed
- Report field updated by assigned worker
- Quick "Close" button on the table

### Inventory
- Tracks the church's musical instruments, office equipment, building
  materials, furniture, and electronics in one place
- Summary cards: total items, estimated total value, low-stock count, items
  needing repair
- Per item: category, quantity + unit, condition (New/Good/Fair/Needs
  Repair/Damaged), location, custodian, purchase date + value, an optional
  low-stock alert threshold, and free-text notes
- Search and filter by category/condition; soft-delete keeps history
- Excel export via Apache POI

### Communications
- **Compose Message**: recipient groups (All Active, Full Time, Part Time, by
  ministry, by sub-branch, Board), channel Email (SMTP) or SMS (BulkSMS SA)
- **Announcements**: posts to WhatsApp and/or Facebook Page (via
  `AnnouncementsService`), separate from member email/SMS
- **Sent Log**: history of everything sent
- SMTP/BulkSMS/WhatsApp/Facebook credentials live in the **Settings** module,
  not here (see below)

### Broadcast
- Downloads a Facebook Live video/URL (via bundled `yt-dlp.exe` + `ffmpeg.exe`)
- Re-uploads it to the church's YouTube channel (YouTube Data API v3, OAuth)
- Upload history with status and links

### Commemorations
- Issues printable certificates: Baptism, Blessing, Certificate of
  Appreciation, and Death Announcement
- Templates in `src/main/resources/com/afmvfcc/certificates/`, rendered by
  `CertificateGenerator`

### Settings
- **Backup**: export/restore the full database, backup history list
- **Website**: local website folder and/or GitHub repo publishing settings
  (see Website Integration below)
- **Email/SMS**: SMTP host/port/credentials, BulkSMS API key — stored in
  `system_settings`
- **Mobile App Connection**: shows the server address + a QR code for the
  Android app to connect to (auto-prefers the Mobile Hotspot adapter when
  it's on); ushers turn on Mobile Hotspot themselves in Windows Settings —
  the CMS only displays the connection info, it doesn't toggle it
- **Connected Devices**: lists every phone currently signed in to the
  Android app (from `app_tokens`), showing whether each is an Admin or
  Usher account, with a one-click **Revoke** to sign a device out
  immediately

### Website
- Blog posts (title, author, content) and website events (separate from the
  church calendar), managed here and written to MySQL
- Publishing to the actual public website is handled by `WebsiteExporter` +
  `GitHubSync` — see Website Integration below

### Admins (Super Admin Only)
- Full CRUD for admin accounts
- BCrypt password hashing on save
- Account unlock button for locked accounts
- Super admin cannot be deleted or demoted
- **Account Type**: Admin (full desktop + mobile app access) or Usher
  (mobile app only — attendance/guests — blocked from the desktop login
  with a clear message)

### Audit Log (Super Admin Only)
- Read-only table of all admin actions
- 500 most recent entries
- Searchable by admin name or action text

### Documentation
- **User Guide** and **System Usage Policy** are both readable directly in the app — two tabs,
  each showing the full text so nothing needs to be downloaded just to read it
- **User Guide**: a complete, step-by-step guide to every module in the system. The Download
  button exports it as PDF or Word with the same church letterhead used across every other export
- **System Usage Policy**: acceptable use, Admin/Usher access levels, password security, data
  privacy and confidentiality, backup and retention, and incident reporting. The Download button
  exports it as a plain, black-and-white PDF with no letterhead, ready to print and sign
- Content lives as plain data (`UserGuideContent.java` / `PolicyContent.java`), rendered once for
  the in-app tabs and again through `DocumentExporter`'s letterhead/plain-text renderers for the
  download — so the on-screen text and the exported document are always identical

---

## 📱 Android App

Implemented — see [`android-app/`](android-app/) (Kotlin, Jetpack Compose)
and its own [README](android-app/README.md) for the end-user side.

The desktop app embeds a REST API (`CmsApiServer.java`, Javalin, port 8080,
started from `Main.java`) that the Android app talks to over the local
church WiFi — no internet connection is used. It:
- Authenticates with the same username/password as the desktop login, and
  issues a random per-session token (stored in `app_tokens`, tied to the
  user, expires after 90 days, and is revoked immediately if that user is
  deactivated in Admins)
- Applies the same 5-failed-attempt lockout as the desktop login
- Works for both Admin and Usher accounts (Ushers are app-only — see Admins
  above)
- Lists open attendance sessions and lets the app create new ones
- Serves the ministry-filtered member list for marking attendance
- Accepts synced attendance records (rejected if the session has since been
  closed) and guest registrations, scoped to the session they were recorded
  in, which land in the **Guests** tab under Members for an admin to
  promote or dismiss
- The app can scan the QR code shown in the CMS's Settings/Attendance
  screens to connect instead of typing the IP address

---

## 🌐 Website Integration

The public website (`website/index.html`, reading `website/data.js`) is kept
in sync by two utilities, both optional and independently configurable in
**Settings → Website**:
- `WebsiteExporter` regenerates `data.js` from the `website_blogs` and
  `website_events` tables
- `GitHubSync` pushes that file (and poster images) straight to a GitHub
  repository over the GitHub Contents REST API — no `git` binary needed, so
  it works from inside the packaged `.exe` — using a Personal Access Token
  stored in `system_settings` (`github_token`, `github_owner`, `github_repo`,
  `github_branch`)

---

## 🔒 Security Summary

| Feature | Implementation |
|---|---|
| Password storage | BCrypt (12 rounds) |
| Login lockout | 5 failed attempts → permanent lock (desktop login and the Android API login both enforce this) |
| Session timeout | 15 minutes inactivity (desktop) |
| Android API tokens | Cryptographically random, stored per-user in `app_tokens`, expire after 90 days, revoked when the user is deactivated |
| Audit trail | All admin actions logged with timestamp |
| Super admin | Cannot be deleted; only one exists |
| DB user | `afm_app` (not root) — least privilege, and MySQL is bound to `127.0.0.1` only when installed via `gradle createFullInstaller` (see `installer/`) |

---

## ⚠️ Known TODOs Before Production

1. Configure SMTP settings in Settings
2. Configure BulkSMS API key in Settings
3. Test PDF export path on Windows (iText7 file chooser)

---

*Generated by AFM VFCC CMS Builder — March 2026*
