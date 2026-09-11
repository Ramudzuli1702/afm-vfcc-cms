# AFM VFCC CMS - MySQL prerequisite installer
#
# One step in the AFM VFCC CMS Setup bundle (installer\bundle.wxs), run by
# the WiX Burn bootstrapper before the app installer. Burn already runs
# PerMachine chain steps elevated, so this script assumes it is running as
# Administrator - it does not self-elevate.
#
# Note: the CMS application itself does NOT need a separately-installed Java
# runtime - `gradle createInstaller` bundles a custom jlink runtime directly
# into the app installer, so Java is already self-contained and needs no
# handling here. MySQL Server has no equivalent option, which is why it's
# the one prerequisite this script handles.
#
# What this script does:
#   1. Checks whether a MySQL server is already installed (any "MySQL*" service).
#      If one is found, it is left completely alone.
#   2. If none is found, silently installs MySQL Server from the bundled MSI,
#      initializes it as a Windows service bound to 127.0.0.1 only (never
#      exposed to the network), and hardens it (removes anonymous users and
#      the "test" database - the same steps `mysql_secure_installation` does).
#   3. Generates a random root password for that one-time hardening step,
#      shows it once in an on-screen dialog (and copies it to the clipboard),
#      and never writes it to disk. The technician pastes it into the CMS's
#      own "Database Setup" wizard the first time the app is launched - the
#      app takes it from there (creates the afm_app DB user, schema, and the
#      first admin account) and never stores or needs the root password again.

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

# ── Locate the bundled MySQL MSI (works both from the source tree and from
#    the flattened, self-extracted installer package) ─────────────────────
$msiCandidates = @(
    (Join-Path $scriptDir 'redist\mysql-9.7.0-winx64.msi'),
    (Join-Path $scriptDir 'mysql-9.7.0-winx64.msi')
)
$msiPath = $msiCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

$mysqlServiceName = 'MySQL_AFMVFCC'
$mysqlInstallDir  = 'C:\Program Files\MySQL\MySQL Server 9.7'
$mysqlDataDir     = 'C:\ProgramData\MySQL\MySQL Server 9.7\Data'
$mysqlPort        = 3306

$existingService = Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Select-Object -First 1

if ($existingService) {
    Write-Step "MySQL is already installed (service '$($existingService.Name)') - skipping MySQL setup."
}
else {
    if (-not $msiPath) {
        throw "MySQL installer (mysql-9.7.0-winx64.msi) was not found next to this script. " +
              "Place it in an 'installer\redist' folder alongside install.ps1."
    }

    Write-Step "Installing MySQL Server (silent)..."
    $msiArgs = @('/i', "`"$msiPath`"", '/quiet', '/norestart', "INSTALLDIR=`"$mysqlInstallDir`"")
    $proc = Start-Process -FilePath 'msiexec.exe' -ArgumentList $msiArgs -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        throw "MySQL MSI install failed with exit code $($proc.ExitCode)"
    }

    Write-Step "Initializing the MySQL data directory..."
    New-Item -ItemType Directory -Force -Path $mysqlDataDir | Out-Null

    $iniPath = Join-Path $mysqlInstallDir 'my.ini'
    @"
[mysqld]
datadir=$mysqlDataDir
port=$mysqlPort
bind-address=127.0.0.1
"@ | Set-Content -Path $iniPath -Encoding ascii

    $mysqld   = Join-Path $mysqlInstallDir 'bin\mysqld.exe'
    $mysqlCli = Join-Path $mysqlInstallDir 'bin\mysql.exe'

    & $mysqld --initialize-insecure --console --datadir="$mysqlDataDir" 2>&1 | ForEach-Object { Write-Host $_ }

    Write-Step "Registering MySQL as a Windows service ('$mysqlServiceName')..."
    & $mysqld --install $mysqlServiceName --defaults-file="$iniPath" | ForEach-Object { Write-Host $_ }
    Start-Service -Name $mysqlServiceName
    Start-Sleep -Seconds 3

    Write-Step "Securing the MySQL installation..."
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $rootPassword = ([Convert]::ToBase64String($bytes) -replace '[/+=]', '').Substring(0, 20)

    $secureSql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
DELETE FROM mysql.user WHERE User='';
DROP DATABASE IF EXISTS test;
DELETE FROM mysql.db WHERE Db='test' OR Db LIKE 'test\_%';
FLUSH PRIVILEGES;
"@
    $secureSql | & $mysqlCli -u root --default-character-set=utf8mb4

    Write-Step "MySQL Server installed and secured (bound to 127.0.0.1 only)."

    Add-Type -AssemblyName System.Windows.Forms
    try { [System.Windows.Forms.Clipboard]::SetText($rootPassword) } catch {}

    [System.Windows.Forms.MessageBox]::Show(
        "MySQL Server was installed for AFM VFCC CMS.`n`n" +
        "MySQL root password (also copied to your clipboard):`n`n    $rootPassword`n`n" +
        "You will be asked for this ONE TIME, in the CMS 'Database Setup' screen " +
        "that appears the first time the app launches. It is not stored anywhere " +
        "by this installer, so keep it safe until setup is complete.",
        "AFM VFCC CMS - MySQL Root Password",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null

    Remove-Variable rootPassword, secureSql -ErrorAction SilentlyContinue
}

Write-Step "MySQL prerequisite step complete."
