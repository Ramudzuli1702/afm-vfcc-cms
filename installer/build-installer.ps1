# Builds the single-file AFM_VFCC_CMS_Setup.exe using the WiX Toolset's
# Burn bootstrapper (installer\bundle.wxs). The resulting bundle chains:
#   1. install.ps1 - installs/hardens MySQL Server if not already present
#   2. the jpackage-built app installer (build\dist\AFM_VFCC_CMS-*.exe),
#      which already bundles its own Java runtime.
#
# Run this AFTER `gradle createInstaller` has produced the app installer.
# Requires the WiX Toolset (candle.exe / light.exe / WixBalExtension.dll) -
# tested against WiX Toolset v3.14.

$ErrorActionPreference = 'Stop'
$installerDir = $PSScriptRoot
$projectRoot  = Split-Path -Parent $installerDir
$distDir      = Join-Path $projectRoot 'build\dist'
$stageDir     = Join-Path $installerDir 'stage'

$appInstaller = Get-ChildItem -Path $distDir -Filter 'AFM_VFCC_CMS-*.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $appInstaller) {
    throw "No app installer found in $distDir. Run 'gradle createInstaller' first."
}

$mysqlMsi = Join-Path $installerDir 'redist\mysql-9.7.0-winx64.msi'
if (-not (Test-Path $mysqlMsi)) {
    throw "MySQL redistributable not found: $mysqlMsi"
}

$appIcon = Join-Path $projectRoot 'src\main\resources\com\afmvfcc\images\afm_logo.ico'
if (-not (Test-Path $appIcon)) {
    throw "App icon not found: $appIcon"
}

$licenseTxt = Join-Path $projectRoot 'src\main\resources\com\afmvfcc\license\AFM_VFCC_Terms.txt'
if (-not (Test-Path $licenseTxt)) {
    throw "License file not found: $licenseTxt"
}

# ── Locate the WiX Toolset ──────────────────────────────────────────────────
$wixEnvBin = $null
if ($env:WIX) { $wixEnvBin = Join-Path $env:WIX 'bin' }

$wixBinCandidates = @(
    $wixEnvBin,
    'C:\Program Files (x86)\WiX Toolset v3.14\bin',
    'C:\Program Files (x86)\WiX Toolset v3.11\bin',
    'C:\Program Files\WiX Toolset v3.14\bin'
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'candle.exe')) }

$wixBin = $wixBinCandidates | Select-Object -First 1
if (-not $wixBin) {
    throw "WiX Toolset not found (looked for candle.exe under common install paths and `$env:WIX). " +
          "Install the WiX Toolset v3 (https://wixtoolset.org) and try again."
}
$candle = Join-Path $wixBin 'candle.exe'
$light  = Join-Path $wixBin 'light.exe'

Write-Host "==> Using WiX Toolset at $wixBin" -ForegroundColor Cyan

if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
New-Item -ItemType Directory -Path $stageDir | Out-Null

# ── Generate a minimal RTF license from the plain-text terms (WixStdBA's
#    RtfLicense variant requires an .rtf file) ──────────────────────────────
function ConvertTo-RtfEscaped([string]$text) {
    $text.Replace('\', '\\').Replace('{', '\{').Replace('}', '\}')
}
$licenseLines = Get-Content -Path $licenseTxt
$escapedLines = $licenseLines | ForEach-Object { ConvertTo-RtfEscaped $_ }
$rtfBody = ($escapedLines -join "\par`n")
$rtfContent = "{\rtf1\ansi\deff0{\fonttbl{\f0 Segoe UI;}}\f0\fs18`n$rtfBody`n}"
$licenseRtf = Join-Path $stageDir 'license.rtf'
$rtfContent | Set-Content -Path $licenseRtf -Encoding ascii

$outputExe = Join-Path $distDir 'AFM_VFCC_CMS_Setup.exe'
$wixobj    = Join-Path $stageDir 'bundle.wixobj'

Write-Host "==> Compiling bundle.wxs (candle)..." -ForegroundColor Cyan
& $candle -ext WixBalExtension `
    -dAppInstallerPath="$($appInstaller.FullName)" `
    -dAppIcon="$appIcon" `
    -dAppLicenseRtf="$licenseRtf" `
    -dInstallPs1="$(Join-Path $installerDir 'install.ps1')" `
    -dMysqlMsi="$mysqlMsi" `
    -out "$wixobj" `
    (Join-Path $installerDir 'bundle.wxs')
if ($LASTEXITCODE -ne 0) { throw "candle.exe failed with exit code $LASTEXITCODE" }

Write-Host "==> Linking bundle (light)..." -ForegroundColor Cyan
& $light -ext WixBalExtension `
    -out "$outputExe" `
    "$wixobj"
if ($LASTEXITCODE -ne 0) { throw "light.exe failed with exit code $LASTEXITCODE" }

if (Test-Path $outputExe) {
    Write-Host "==> Created: $outputExe" -ForegroundColor Green
} else {
    throw "light.exe reported success but $outputExe was not created."
}
