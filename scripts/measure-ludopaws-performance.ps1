[CmdletBinding()]
param(
    [ValidateRange(10, 900)]
    [int]$DurationSeconds = 60,

    [ValidateRange(0.1, 100.0)]
    [double]$MaxJankPercent = 5.0,

    [ValidateRange(8, 200)]
    [int]$MaxP95FrameTimeMs = 33,

    [string]$Label = "manual",

    [string]$OutputDirectory = "",

    [switch]$SkipLaunch,

    [switch]$EnforceBudget
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$packageName = "com.rekixo.ludopaws"
$activityName = "com.rekixo.ludopaws.HomeActivity"

function Resolve-Adb {
    $candidates = @()

    $pathAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($pathAdb) {
        $candidates += $pathAdb.Source
    }

    foreach ($sdkRoot in @(
        $env:LUDOPROOF_ANDROID_SDK,
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME
    )) {
        if (![string]::IsNullOrWhiteSpace($sdkRoot)) {
            $candidates += (Join-Path $sdkRoot "platform-tools\adb.exe")
        }
    }

    $repoDrive = [System.IO.Path]::GetPathRoot($repoRoot)
    if ($repoDrive) {
        $candidates += (Join-Path $repoDrive "Dev\finworkar-tools\android-sdk\platform-tools\adb.exe")
    }

    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
    }

    foreach ($candidate in $candidates | Where-Object { $_ } | Select-Object -Unique) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "ADB was not found. Set LUDOPROOF_ANDROID_SDK / ANDROID_SDK_ROOT, or add adb.exe to PATH."
}

function Resolve-DeviceSerial(
    [string]$adb
) {
    if ($env:ANDROID_SERIAL) {
        $state = (& $adb -s $env:ANDROID_SERIAL get-state 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $state -ne "device") {
            throw "ANDROID_SERIAL '$env:ANDROID_SERIAL' is not an authorized online device."
        }
        return $env:ANDROID_SERIAL
    }

    $deviceLines = @(
        (& $adb devices) |
            Select-Object -Skip 1 |
            Where-Object { $_ -match "\tdevice$" }
    )

    if ($deviceLines.Count -eq 0) {
        throw "No authorized Android device found. Connect USB, enable USB debugging, and approve the RSA prompt."
    }

    if ($deviceLines.Count -gt 1) {
        throw "More than one Android device is connected. Set ANDROID_SERIAL to choose one device."
    }

    return (($deviceLines[0] -split "\s+")[0]).Trim()
}

function Invoke-AdbText(
    [string]$adb,
    [string[]]$adbArgs,
    [string[]]$commandArgs,
    [switch]$AllowFailure
) {
    $output = (& $adb @adbArgs @commandArgs 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE

    if (!$AllowFailure -and $exitCode -ne 0) {
        throw "ADB command failed ($exitCode): adb $($commandArgs -join ' ')`n$output"
    }

    return $output.TrimEnd()
}

function Write-Utf8File(
    [string]$path,
    [string]$content
) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $content, $utf8NoBom)
}

function Get-RegexGroup(
    [string]$text,
    [string]$pattern,
    [int]$group = 1
) {
    $match = [regex]::Match(
        $text,
        $pattern,
        [System.Text.RegularExpressions.RegexOptions]::Multiline
    )
    if (!$match.Success) {
        return $null
    }

    return $match.Groups[$group].Value
}

function Get-IntOrNull(
    [string]$value
) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $null
    }

    $parsed = 0
    if ([int]::TryParse($value, [ref]$parsed)) {
        return $parsed
    }

    return $null
}

function Get-DoubleOrNull(
    [string]$value
) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $null
    }

    $parsed = 0.0
    if ([double]::TryParse(
        $value,
        [System.Globalization.NumberStyles]::Float,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref]$parsed
    )) {
        return $parsed
    }

    return $null
}

$adb = Resolve-Adb
$serial = Resolve-DeviceSerial $adb
$adbArgs = @("-s", $serial)

$installedPath = Invoke-AdbText $adb $adbArgs @("shell", "pm", "path", $packageName) -AllowFailure
if ($LASTEXITCODE -ne 0 -or $installedPath -notmatch "package:") {
    throw "Ludo Paws is not installed on device '$serial'. Run node scripts/android-local-testing.mjs dev first."
}

$safeLabel = ($Label -replace "[^A-Za-z0-9._-]", "-").Trim("-")
if ([string]::IsNullOrWhiteSpace($safeLabel)) {
    $safeLabel = "manual"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot ("artifacts\real-device-performance\" + $stamp + "-" + $safeLabel)
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path $OutputDirectory).Path

$model = Invoke-AdbText $adb $adbArgs @("shell", "getprop", "ro.product.model")
$manufacturer = Invoke-AdbText $adb $adbArgs @("shell", "getprop", "ro.product.manufacturer")
$sdk = Invoke-AdbText $adb $adbArgs @("shell", "getprop", "ro.build.version.sdk")
$buildFingerprint = Invoke-AdbText $adb $adbArgs @("shell", "getprop", "ro.build.fingerprint")

Write-Host ""
Write-Host "Ludo Paws real-device performance capture"
Write-Host "Device: $manufacturer $model (Android API $sdk)"
Write-Host "Serial: $serial"
Write-Host "Sample: $DurationSeconds seconds"
Write-Host "Output: $OutputDirectory"

if (!$SkipLaunch) {
    Write-Host ""
    Write-Host "Launching Ludo Paws..."
    Invoke-AdbText $adb $adbArgs @("shell", "am", "force-stop", $packageName) | Out-Null
    Invoke-AdbText $adb $adbArgs @("shell", "am", "start", "-n", "$packageName/$activityName") | Out-Null
    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "Resetting frame statistics..."
Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "gfxinfo", $packageName, "reset") -AllowFailure | Out-Null
Invoke-AdbText $adb $adbArgs @("logcat", "-c") -AllowFailure | Out-Null

Write-Host "Play the target gameplay scenario now. Keep the board visible for the full sample."
Write-Host "Capturing for $DurationSeconds seconds..."
Start-Sleep -Seconds $DurationSeconds

$gfxInfo = Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "gfxinfo", $packageName, "framestats") -AllowFailure
$memInfo = Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "meminfo", $packageName) -AllowFailure
$batteryInfo = Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "battery") -AllowFailure
$thermalInfo = Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "thermalservice") -AllowFailure
$displayInfo = Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "display") -AllowFailure
$activityInfo = Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "activity", "activities") -AllowFailure
$surfaceLayers = Invoke-AdbText $adb $adbArgs @("shell", "dumpsys", "SurfaceFlinger", "--list") -AllowFailure
$logcat = Invoke-AdbText $adb $adbArgs @("logcat", "-d", "-v", "threadtime", "AndroidRuntime:E", "LudoPaws3D:V", "*:S") -AllowFailure

Write-Utf8File (Join-Path $OutputDirectory "gfxinfo-framestats.txt") $gfxInfo
Write-Utf8File (Join-Path $OutputDirectory "meminfo.txt") $memInfo
Write-Utf8File (Join-Path $OutputDirectory "battery.txt") $batteryInfo
Write-Utf8File (Join-Path $OutputDirectory "thermalservice.txt") $thermalInfo
Write-Utf8File (Join-Path $OutputDirectory "display.txt") $displayInfo
Write-Utf8File (Join-Path $OutputDirectory "activities.txt") $activityInfo
Write-Utf8File (Join-Path $OutputDirectory "surface-layers.txt") $surfaceLayers
Write-Utf8File (Join-Path $OutputDirectory "ludopaws-logcat.txt") $logcat

$totalFrames = Get-IntOrNull (Get-RegexGroup $gfxInfo "Total frames rendered:\s*(\d+)")
$jankyFrames = Get-IntOrNull (Get-RegexGroup $gfxInfo "Janky frames:\s*(\d+)")
$jankyPercent = Get-DoubleOrNull (Get-RegexGroup $gfxInfo "Janky frames:\s*\d+\s*\(([0-9.]+)%\)")
$p50 = Get-IntOrNull (Get-RegexGroup $gfxInfo "50th percentile:\s*(\d+)ms")
$p90 = Get-IntOrNull (Get-RegexGroup $gfxInfo "90th percentile:\s*(\d+)ms")
$p95 = Get-IntOrNull (Get-RegexGroup $gfxInfo "95th percentile:\s*(\d+)ms")
$p99 = Get-IntOrNull (Get-RegexGroup $gfxInfo "99th percentile:\s*(\d+)ms")

$totalPssKb = Get-IntOrNull (Get-RegexGroup $memInfo "TOTAL PSS:\s*(\d+)")
if ($null -eq $totalPssKb) {
    $totalPssKb = Get-IntOrNull (Get-RegexGroup $memInfo "^\s*TOTAL\s+(\d+)")
}

$fatalCount = ([regex]::Matches($logcat, "FATAL EXCEPTION|ANR in")).Count
$rendererFallbackCount = ([regex]::Matches($logcat, "3D pawn runtime unavailable")).Count

$violations = @()
if ($null -ne $jankyPercent -and $jankyPercent -gt $MaxJankPercent) {
    $violations += "Janky frames $jankyPercent% exceeded budget $MaxJankPercent%."
}
if ($null -ne $p95 -and $p95 -gt $MaxP95FrameTimeMs) {
    $violations += "95th percentile frame time ${p95}ms exceeded budget ${MaxP95FrameTimeMs}ms."
}
if ($fatalCount -gt 0) {
    $violations += "Detected $fatalCount fatal/ANR log entries."
}
if ($rendererFallbackCount -gt 0) {
    $violations += "Detected $rendererFallbackCount LudoPaws3D renderer fallback warnings."
}

$status = "PASS"
if ($violations.Count -gt 0) {
    $status = "WARN"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    label = $safeLabel
    status = $status
    packageName = $packageName
    durationSeconds = $DurationSeconds
    device = [ordered]@{
        serial = $serial
        manufacturer = $manufacturer
        model = $model
        androidApi = $sdk
        buildFingerprint = $buildFingerprint
    }
    frameBudget = [ordered]@{
        maxJankPercent = $MaxJankPercent
        maxP95FrameTimeMs = $MaxP95FrameTimeMs
    }
    frameStats = [ordered]@{
        totalFrames = $totalFrames
        jankyFrames = $jankyFrames
        jankyPercent = $jankyPercent
        p50Ms = $p50
        p90Ms = $p90
        p95Ms = $p95
        p99Ms = $p99
    }
    memory = [ordered]@{
        totalPssKb = $totalPssKb
    }
    diagnostics = [ordered]@{
        fatalOrAnrEntries = $fatalCount
        rendererFallbackWarnings = $rendererFallbackCount
    }
    violations = $violations
}

$reportJson = $report | ConvertTo-Json -Depth 6
$reportPath = Join-Path $OutputDirectory "performance-report.json"
Write-Utf8File $reportPath $reportJson

Write-Host ""
Write-Host "Performance capture complete: $status"
if ($null -ne $totalFrames) {
    Write-Host "Frames: $totalFrames"
}
if ($null -ne $jankyPercent) {
    Write-Host "Janky: $jankyPercent%"
}
if ($null -ne $p95) {
    Write-Host "P95: ${p95}ms"
}
if ($null -ne $totalPssKb) {
    Write-Host "TOTAL PSS: $totalPssKb KB"
}

if ($violations.Count -gt 0) {
    Write-Host ""
    Write-Host "Budget warnings:"
    $violations | ForEach-Object { Write-Host " - $_" }
}

Write-Host ""
Write-Host "Report: $reportPath"
Write-Host "Raw evidence: $OutputDirectory"

if ($EnforceBudget -and $violations.Count -gt 0) {
    exit 2
}
