$ErrorActionPreference = 'Stop'

$adbCandidates = @(
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    'adb.exe'
)
$adb = $adbCandidates | Where-Object {
    if ([IO.Path]::IsPathRooted($_)) { Test-Path -LiteralPath $_ } else { Get-Command $_ -ErrorAction SilentlyContinue }
} | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($adb)) { throw 'adb.exe was not found.' }

$deviceLines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
if ($deviceLines.Count -ne 1) {
    throw "Expected exactly one connected Android device, found $($deviceLines.Count)."
}

$serial = ($deviceLines[0] -split '\s+')[0]
$isEmulator = (& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -eq '1'
if (-not $isEmulator) {
    throw @"
Refusing to run connected Android tests on physical device $serial.
Gradle's connected test lifecycle can uninstall the target app and erase its private model, Key, history, and profile data.
Use an isolated emulator. Physical-device override is intentionally unavailable in this repository.
"@
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Push-Location $repoRoot
try {
    & .\gradlew.bat :apps:android:app:connectedDebugAndroidTest -PcaesarConnectedTestGuard=verified --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Connected Android tests failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
