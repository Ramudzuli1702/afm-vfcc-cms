# AFM VFCC Church Management System
### Desktop Application — Java 22 + JavaFX + MySQL

---

## ✅ What's Included

| File count | Category |
|---|---|
| 16 Java controllers | `src/main/java/com/afmvfcc/controllers/` |
| 7 Java models | `src/main/java/com/afmvfcc/models/` |
| 3 Java utilities | `src/main/java/com/afmvfcc/utils/` |
| 1 DB connection class | `src/main/java/com/afmvfcc/db/` |
| 1 Main entry point | `src/main/java/com/afmvfcc/` |
| 14 FXML layouts | `src/main/resources/com/afmvfcc/fxml/` |
| 1 CSS stylesheet | `src/main/resources/com/afmvfcc/css/` |
| 1 reference schema | `afm_vfcc_setup.sql` (kept for documentation — the app builds its own schema automatically, see below) |

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
├── sql/
│   └── system_settings_migration.sql
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── com/afmvfcc/
    │       ├── Main.java                       ← App entry point
    │       ├── db/
    │       │   └── DatabaseConnection.java     ← MySQL singleton
    │       ├── models/
    │       │   ├── User.java
    │       │   ├── Member.java
    │       │   ├── Event.java
    │       │   ├── BoardMeeting.java
    │       │   ├── AttendanceSession.java
    │       │   ├── WelfareCase.java
    │       │   ├── WebsiteBlog.java
    │       │   ├── WebsiteEvent.java
    │       │   └── AuditEntry.java
    │       ├── utils/
    │       │   ├── PasswordUtil.java           ← BCrypt hashing
    │       │   ├── SessionManager.java         ← Login session + 15min timeout
    │       │   └── AuditLogger.java            ← Writes to audit_log table
    │       └── controllers/
    │           ├── LoginController.java        ← Auth + lockout
    │           ├── MainLayoutController.java   ← Sidebar navigation
    │           ├── DashboardController.java    ← Stats + reminders
    │           ├── MembersController.java      ← Full CRUD + search + filter
    │           ├── AddEditMemberController.java← Add/Edit member dialog
    │           ├── MemberPdfExporter.java      ← iText7 PDF generation
    │           ├── AttendanceController.java   ← Sessions + marking + Excel export
    │           ├── BoardController.java        ← Board members + meetings + minutes
    │           ├── CalendarController.java     ← Church events CRUD
    │           ├── WelfareController.java      ← Welfare cases + workers
    │           ├── CommunicationsController.java← Email + SMS (BulkSMS)
    │           ├── WebsiteController.java      ← Website blogs + events
    │           ├── AdminsController.java       ← Super admin: manage admins
    │           ├── AuditLogController.java     ← Read-only audit trail
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
            ├── communications.fxml
            ├── website.fxml
            ├── admins.fxml
            ├── audit_log.fxml
            └── finance.fxml
```

---

## 🔧 Module Guide

### Login
- BCrypt password verification
- Account lockout after 5 failed attempts (permanent until super admin unlocks)
- Enter key works on both fields
- Session timeout: 15 minutes of inactivity → auto-logout

### Dashboard
- 8 stat cards pulled live from DB
- Upcoming events in next 7 days
- Pending welfare cases
- Pending member approvals with count badge

### Members
- Full CRUD with soft delete
- Search by name, phone, email
- Filter by status, sub-branch, ministry
- Multi-ministry assignment via checkboxes
- Family unit creation and linking
- Pending review queue (from Android app)
- PDF export via iText7

### Attendance
- Create named sessions with date + ministry
- Tap a row to toggle Present/Absent
- Mark All Present / Clear All buttons
- Per-session Excel export via Apache POI
- Session history with delete
- Android merge placeholder (WiFi API — future phase)

### Church Board
- Board member roles with start/end dates
- Meetings with typed agenda + minutes
- Meeting attendee count
- Searchable history

### Calendar
- Church events (separate from website events)
- Category filter: Service, Meeting, Outreach, Youth, Other
- Feeds the Dashboard 7-day reminder

### Welfare
- Cases linked to members + welfare workers
- Status: Pending → In Progress → Completed
- Report field updated by assigned worker
- Quick "Close" button on the table

### Communications
- Recipient groups: All Active, Full Time, Part Time, by ministry, by sub-branch, Board
- Channel: Email (SMTP) or SMS (BulkSMS SA)
- Sent log stored in DB
- Settings tab: SMTP + BulkSMS API key stored in `system_settings`

### Website
- Blog posts: title, author, content
- Website events: separate from church calendar
- Both write to MySQL — ready for REST API phase

### Admins (Super Admin Only)
- Full CRUD for admin accounts
- BCrypt password hashing on save
- Account unlock button for locked accounts
- Super admin cannot be deleted or demoted

### Audit Log (Super Admin Only)
- Read-only table of all admin actions
- 500 most recent entries
- Searchable by admin name or action text

---

## 📱 Android App (Next Phase)

The Android app will:
- Connect over local WiFi (token entered once in settings)
- Take attendance → sync to desktop
- Register new members → pending review queue
- Read-only member list (synced from desktop)

The `app_tokens` table in the DB is already prepared for this.

---

## 🌐 Website Integration (Next Phase)

The website currently reads from `localStorage`.  
Migration plan:
1. Deploy a lightweight REST API (Spring Boot or Node.js) alongside the website
2. The API reads from `website_blogs` and `website_events` tables
3. The desktop app writes to those tables — website updates automatically

---

## 🔒 Security Summary

| Feature | Implementation |
|---|---|
| Password storage | BCrypt (12 rounds) |
| Login lockout | 5 failed attempts → permanent lock |
| Session timeout | 15 minutes inactivity |
| Audit trail | All admin actions logged with timestamp |
| Super admin | Cannot be deleted; only one exists |
| DB user | `afm_app` (not root) — least privilege |

---

## ⚠️ Known TODOs Before Production

1. Configure SMTP settings in Communications → Settings
2. Configure BulkSMS API key in Communications → Settings
3. Test PDF export path on Windows (iText7 file chooser)

---

*Generated by AFM VFCC CMS Builder — March 2026*
