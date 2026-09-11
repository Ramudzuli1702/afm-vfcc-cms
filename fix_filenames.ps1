# Run this once from PowerShell in your project root to fix wrong-case filenames
# Usage: cd C:\Users\Ramudzuli\Downloads\AFM_VFCC_CMS\AFM_VFCC_CMS
#        .\fix_filenames.ps1

$base = "src\main\java\com\afmvfcc"

$renames = @(
    @{ From = "$base\controllers\Settingscontroller.java";  To = "$base\controllers\SettingsController.java" },
    @{ From = "$base\controllers\Welfarepdfexporter.java";  To = "$base\controllers\WelfarePdfExporter.java" },
    @{ From = "$base\utils\Smsservice.java";                To = "$base\utils\SmsService.java" },
    @{ From = "$base\utils\Emailservice.java";              To = "$base\utils\EmailService.java" }
)

foreach ($r in $renames) {
    if (Test-Path $r.From) {
        # Windows won't rename same-name different-case directly, use a temp name
        $temp = $r.To + ".tmp"
        Rename-Item -Path $r.From -NewName ([System.IO.Path]::GetFileName($temp))
        Rename-Item -Path $temp   -NewName ([System.IO.Path]::GetFileName($r.To))
        Write-Host "Renamed: $($r.From) -> $($r.To)"
    } else {
        Write-Host "Not found (already correct or missing): $($r.From)"
    }
}

Write-Host ""
Write-Host "Done. Run 'gradle run' now."
