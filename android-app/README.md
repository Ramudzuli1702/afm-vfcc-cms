# AFM VFCC Attendance App

**Android companion app for the AFM VFCC Church Management System**
Developed by Ralidzhivha Ramudzuli Ricardo

---

## What This App Does

The AFM VFCC Attendance App is used by ushers and assigned church workers to take attendance during services and ministry meetings. It connects to the AFM VFCC Church Management System (CMS) running on the church computer over the local WiFi network.

**Key features:**
- Sign in with your CMS administrator credentials
- View and select active attendance sessions created in the CMS
- Create a new session directly from the phone
- Mark members present or absent from a full scrollable list
- Search for any member by name
- Record guest visitors with full details
- Sync everything to the CMS with a single tap

---

## Requirements

| Item | Requirement |
|------|-------------|
| Phone OS | Android 8.0 (Oreo) or later |
| CMS computer OS | Windows 10 or 11 |
| Network | Both phone and CMS computer must be on the **same WiFi network** |
| CMS version | Any current AFM VFCC CMS — the API server is built in, nothing to enable |

---

## Installation

### On the CMS Computer (do this first)

Nothing extra to set up — `CmsApiServer` is started automatically by
`Main.java` every time the CMS launches, and it creates its own database
tables (`app_tokens`, `guests`) on first start if they don't already exist.
Just run the CMS as normal (`gradle run`, or the installed app). You'll see
this in the console:
```
[CMS API] Server started on port 8080
```

### On the Android Phone

1. Open **Android Studio** and open the `android-app` folder (inside the
   `afm-vfcc-cms` repo, alongside the CMS).
2. Connect your Android phone via USB with USB debugging enabled, or use an emulator.
3. Click **Run** (the green play button) to build and install the app.
4. The app icon will appear as **AFM VFCC** on the phone.

> **To install without Android Studio:** Ask the developer to generate a signed APK file and transfer it to the phone.

---

## First-Time Setup

Before using the app for the first time, you need to tell it the IP address of the CMS computer.

### Step 1 — Find the CMS computer's IP address

1. On the CMS computer, press `Windows + R`, type `cmd` and press Enter.
2. In the black window, type `ipconfig` and press Enter.
3. Look for **"IPv4 Address"** under your WiFi adapter. It will look something like `192.168.1.5`.
4. Write this number down.

> The IP address may change if the router restarts. If the app stops connecting, repeat this step.

### Step 2 — Enter the server address in the app

1. Open the **AFM VFCC** app on the phone.
2. On the login screen, tap **Server Settings** at the bottom.
3. Enter the IP address you found (e.g. `192.168.1.5`).
4. Leave the port as `8080` (default).
5. Tap **Test Connection** — you should see "Connected successfully".
6. Tap **Save Settings**.

### Step 3 — Log in

Use the same username and password you use to log into the CMS desktop application.

---

## Using the App

### Taking Attendance

1. **Log in** with your CMS credentials.
2. You will see a list of **Sessions**. Tap the session for today's service.
   - If no session exists yet, tap the **+ New Session** button (bottom right) to create one.
3. The **Attendance screen** opens with a list of all active members.
   - Scroll through the list and **tap a member's row** (or tap the checkbox) to mark them present. The row turns green.
   - Tap again to unmark them.
   - Use the **search bar** at the top to find someone quickly by name.
   - Tap the **⋮ menu** (top right) to **Mark All Present** or **Clear All**.
4. The progress bar at the top shows how many members have been marked.

### Adding a Guest

1. On the attendance screen, tap **Add Guest** (bottom right).
2. Fill in the guest's details:
   - **Full Name** (required)
   - **Phone Number**
   - **Gender**
   - **Sub-Branch / Area** they are from
   - **Invited By** — which member brought them
   - **Interested in Full Membership** — toggle this on if they want to join
   - **Prayer Request** — anything they would like the Bishop to know
3. Tap **Save Guest**.
4. You can add another guest immediately or go back to attendance.
5. The guest count is shown as a chip at the bottom of the attendance screen.

> Guests are saved on the phone and will be sent to the CMS when you sync.

### Syncing to the CMS

When you are ready to send the attendance and guest records to the main system:

1. Tap the **Sync to CMS** button at the bottom of the attendance screen.
2. A confirmation dialog will show you exactly what will be sent.
3. Tap **Sync Now**.
4. A message will confirm how many records were synced.

> **Important:** The phone must be connected to the church WiFi when you sync. If you took attendance offline, you can sync later as long as you do not close the app session.

---

## On the CMS Desktop — After Syncing

### Viewing Attendance

- Open the **Attendance** module in the CMS.
- Go to **Session History** and find the session.
- Click **View** to see the full register as submitted from the phone.

### Viewing Guests

- Open the **Members** module.
- Click the **Guests** tab.
- Every guest synced from the app appears here with all their details.
- Click **View** on any guest to see their full record including their prayer request (shown in a gold highlighted box for the Bishop).
- Click **Promote** to move a guest to **Pending Review** — from there the admin can approve them as a full member.
- Click **Dismiss** if the guest does not need further follow-up.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Cannot reach the CMS" | Make sure both devices are on the same WiFi. Check the IP address in Settings. Make sure the CMS desktop app is open. |
| "Invalid username or password" | Use the same credentials as the CMS desktop login. |
| "Account is locked" | Log into the CMS desktop app and unlock the account from Admin Management. |
| App shows no sessions | Make sure the CMS is running. Tap the refresh button on the Sessions screen. |
| Sync fails | Check WiFi connection. Try again. If it still fails, do not close the app — the data is still saved on the phone. |
| IP address changed | On the CMS computer run `ipconfig` again to get the new IP. Update it in the app's Server Settings. |

---

## Project Structure

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/afmvfcc/app/
│   │       ├── AfmApp.kt                  Application class
│   │       ├── MainActivity.kt            Entry point + navigation
│   │       ├── ui/
│   │       │   ├── AppViewModel.kt        All business logic and state
│   │       │   ├── Navigation.kt          Screen route definitions
│   │       │   ├── theme/
│   │       │   │   └── Theme.kt           AFM VFCC colours and theme
│   │       │   └── screens/
│   │       │       ├── LoginScreen.kt     Sign-in screen
│   │       │       ├── SessionsScreen.kt  Session list + new session
│   │       │       ├── AttendanceScreen.kt Member list + marking
│   │       │       ├── AddGuestScreen.kt  Guest recording form
│   │       │       └── SettingsScreen.kt  Server IP configuration
│   │       └── data/
│   │           ├── api/
│   │           │   └── ApiClient.kt       HTTP client (Ktor)
│   │           ├── local/
│   │           │   └── Prefs.kt           DataStore preferences
│   │           └── model/
│   │               └── Models.kt          Data classes
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

---

## Technical Details

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI framework | Jetpack Compose (Material 3) |
| Navigation | Navigation Compose |
| Networking | Ktor Client (Android engine) |
| Serialization | kotlinx.serialization |
| Local storage | DataStore Preferences |
| State management | ViewModel + StateFlow |
| Min Android SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

The app communicates with the CMS via a lightweight REST API (Javalin) embedded in the CMS desktop application. No external server or internet connection is required — everything runs on the local church WiFi network.

---

## Security Notes

- Login tokens are stored in the app's private DataStore (sandboxed to this
  app on the device, though not encrypted at rest) and sent with every API
  request as a `Bearer` token.
- The API server checks every request's token against the database — it must
  be active, unexpired, and belong to a user who is still active in the CMS.
- Tokens expire automatically after 90 days, and are invalidated immediately
  if the user's account is deactivated in the CMS's Admins page.
- Repeated failed logins lock the account after 5 attempts, same as the
  desktop CMS login.
- All communication happens on the local network only, over plain HTTP (no
  TLS) — this is an accepted trade-off for a church-WiFi-only tool with no
  internet exposure; no data is ever sent to the internet.

---

## Developer

**Ralidzhivha Ramudzuli Ricardo**
AFM VFCC Church Management System
Victory Fellowship Christian Centre, Ha-Kutama Tshikwarani, Limpopo

*"Where you find people, you find AFM" — John 3:16*
