# AFM VFCC CMS — Prerequisite Installer

This folder builds a single `AFM_VFCC_CMS_Setup.exe` that a client can hand to
a technician (or double-click themselves) to get MySQL Server *and* the CMS
app installed, with no separate downloads required.

## Why only MySQL is bundled here (not Java)

The CMS installer produced by `gradle createInstaller` already ships its own
Java runtime — `build.gradle`'s `createRuntime` task uses `jlink` to build a
trimmed JRE containing exactly the modules the app needs, and `createInstaller`
embeds it directly into the app installer via `jpackage --runtime-image`.
A client machine never needs Java installed at all for the packaged installer;
that's only required for `gradle run` during development.

MySQL Server has no equivalent "embed it in the JVM" option — it's a separate
service — so it's the one real prerequisite that needs bundling and silent
installation.

## What's in this folder

| File | Purpose |
|---|---|
| `redist/mysql-9.7.0-winx64.msi` | Standalone MySQL Server product MSI (not the MySQL Installer GUI bootstrapper — this one installs silently with a plain `msiexec /quiet`). |
| `install.ps1` | Checks for an existing MySQL install, and silently installs + hardens MySQL if missing. Run as one step of the bundle below — not meant to be double-clicked on its own. |
| `bundle.wxs` | A WiX "Burn" bootstrapper definition that chains `install.ps1` and the built app installer into one setup `.exe`. |
| `build-installer.ps1` | Compiles `bundle.wxs` (via the WiX Toolset's `candle`/`light`) into `build/dist/AFM_VFCC_CMS_Setup.exe`. |

We build this with WiX (not a simpler tool like `iexpress`) because `iexpress`'s
`/Q` silent-build switch turned out to be unreliable on current Windows builds
— it popped up its interactive wizard instead of building headlessly. WiX's
`candle`/`light` are plain command-line compilers with no such issue, and
they're already installed on this machine (WiX Toolset v3.14) since `jpackage`
itself uses them to build the plain app installer.

## Building the combined installer

```bash
gradle createFullInstaller
```

This runs `createInstaller` first (if needed), then `build-installer.ps1`,
producing `build/dist/AFM_VFCC_CMS_Setup.exe` — the file to hand to a client.
It requires the WiX Toolset v3 (`candle.exe` / `light.exe` / `WixBalExtension.dll`)
to be installed on the build machine — get it from https://wixtoolset.org if
`gradle createFullInstaller` reports it can't find WiX.

## What happens when a client runs `AFM_VFCC_CMS_Setup.exe`

1. The Burn bootstrapper shows a standard setup wizard (license page, etc.)
   and, once confirmed, runs its chain of two steps.
2. **Step 1 — MySQL.** If MySQL is already installed on the machine (any
   `MySQL*` Windows service), `install.ps1` leaves it completely alone and
   exits immediately. Otherwise it installs MySQL Server silently, registers
   it as a Windows service named `MySQL_AFMVFCC`, bound to `127.0.0.1` only
   (never exposed to the network), and hardens it the same way
   `mysql_secure_installation` does (anonymous users and the `test` database
   removed). A random root password is generated for this step, shown once in
   an on-screen dialog and copied to the clipboard — it is **never written to
   disk**.
3. **Step 2 — the app.** The CMS application installer runs (the normal
   jpackage/WiX installer with the directory chooser, shortcuts, etc.).
4. On first launch, the CMS's own "Database Setup" wizard
   (`DatabaseConnection.java`) asks for that MySQL root password once, then
   creates the `AFM_VFCC_CMS` database, the least-privilege `afm_app` user,
   all tables, and the first admin account itself. The root password is not
   needed again after that.

## Updating the MySQL version

If you bundle a different MySQL Server MSI, update the filename in
`install.ps1` (the `$msiCandidates` list), the copy in `redist/`, and
`build-installer.ps1`'s `$mysqlMsi` path. Also adjust `$mysqlInstallDir` /
`$mysqlDataDir` in `install.ps1` to match the new version's default paths.

## Testing

`build-installer.ps1` (candle/light) is safe to run repeatedly — it only
compiles files, it doesn't install anything. Running the resulting
`AFM_VFCC_CMS_Setup.exe`, however, installs a Windows service and changes
MySQL's root password — test that on a clean VM or a spare machine, not on a
computer with a MySQL install you care about.
