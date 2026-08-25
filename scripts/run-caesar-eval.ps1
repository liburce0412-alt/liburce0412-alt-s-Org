param(
    [Parameter(Mandatory = $true)]
    [switch]$IUnderstandThisRunsLocalModels,
    [string]$DeviceSerial = '',
    [int]$TimeoutMinutesPerModel = 45,
    [string]$TargetPackage = 'com.aistudio.campusai.ywtpzx',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

if (-not $IUnderstandThisRunsLocalModels) {
    throw 'Explicit opt-in is required: pass -IUnderstandThisRunsLocalModels.'
}
if ($TimeoutMinutesPerModel -lt 1 -or $TimeoutMinutesPerModel -gt 120) {
    throw 'TimeoutMinutesPerModel must be between 1 and 120.'
}

$adbCandidates = @(
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    'adb.exe'
)
$adb = $adbCandidates | Where-Object {
    if ([IO.Path]::IsPathRooted($_)) { Test-Path -LiteralPath $_ } else { Get-Command $_ -ErrorAction SilentlyContinue }
} | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($adb)) { throw 'adb.exe was not found.' }

$connected = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($connected.Count -ne 1) { throw "Expected exactly one connected Android device, found $($connected.Count)." }
    $DeviceSerial = ($connected[0] -split '\s+')[0]
} elseif (-not ($connected | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial })) {
    throw "Android device '$DeviceSerial' is not connected and authorized."
}

$packagePath = @(& $adb -s $DeviceSerial shell pm path $TargetPackage 2>$null)
if ($LASTEXITCODE -ne 0 -or $packagePath.Count -eq 0) {
    throw "Package '$TargetPackage' is not installed. This script never installs or replaces the app."
}

# run-as is both the report transport and a preflight that this is a debuggable build.
$runAsIdentity = @(& $adb -s $DeviceSerial exec-out run-as $TargetPackage id 2>$null)
if ($LASTEXITCODE -ne 0 -or $runAsIdentity.Count -eq 0) {
    throw "Package '$TargetPackage' is not debuggable. Install a debug APK separately; this script will not do it."
}

$models = @('qwen3.5-2b-mnn', 'qwen3.5-4b-mnn')
$readyMarkers = (@(& $adb -s $DeviceSerial exec-out run-as $TargetPackage find no_backup/models -name .ready.json -print 2>$null) -join "`n")
foreach ($modelId in $models) {
    if ($readyMarkers -notmatch [regex]::Escape("no_backup/models/$modelId-")) {
        throw "$modelId is not Ready. Eval never starts or resumes model downloads."
    }
}

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot 'artifacts\caesar-eval'
}
$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($resolvedOutput) | Out-Null

$evalAction = 'com.campusai.debug.RUN_CAESAR_EVAL'
$evalComponent = "$TargetPackage/com.campusai.debug.CaesarEvalActivity"
$batchId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$reports = [System.Collections.Generic.List[object]]::new()

foreach ($modelId in $models) {
    $runId = "$batchId-$($modelId -replace '[^A-Za-z0-9._-]', '-')"
    $deviceStatus = "files/caesar-eval/$runId.status"
    $deviceReport = "files/caesar-eval/$runId.json"
    $hostReport = Join-Path $resolvedOutput "$runId.json"

    # The explicit stop releases any foreground Caesar engine without clearing app data.
    & $adb -s $DeviceSerial shell am force-stop $TargetPackage | Out-Null
    try {
        $launch = @(& $adb -s $DeviceSerial shell am start -W -n $evalComponent -a $evalAction `
            --es model_id $modelId --es run_id $runId --ez explicit_opt_in true 2>&1)
        if ($LASTEXITCODE -ne 0 -or ($launch -join "`n") -match 'Error:|Exception|Permission Denial') {
            throw "Unable to start the shell-gated debug Eval activity:`n$($launch -join "`n")"
        }

        $deadline = [DateTime]::UtcNow.AddMinutes($TimeoutMinutesPerModel)
        $status = ''
        while ([DateTime]::UtcNow -lt $deadline) {
            $status = ((@(& $adb -s $DeviceSerial exec-out run-as $TargetPackage cat $deviceStatus 2>$null)) -join "`n").Trim()
            if ($status -in @('complete', 'failed')) { break }
            Start-Sleep -Seconds 2
        }
        if ($status -notin @('complete', 'failed')) {
            throw "$modelId Eval timed out after $TimeoutMinutesPerModel minute(s)."
        }

        $jsonText = (@(& $adb -s $DeviceSerial exec-out run-as $TargetPackage cat $deviceReport 2>$null) -join "`n")
        if ([string]::IsNullOrWhiteSpace($jsonText)) { throw "$modelId Eval produced no report." }
        $parsed = $jsonText | ConvertFrom-Json
        [IO.File]::WriteAllText($hostReport, $jsonText + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
        $reports.Add([pscustomobject]@{
            modelId = $modelId
            status = $status
            report = [IO.Path]::GetFileName($hostReport)
            caseOrder = @($parsed.configuration.caseOrder)
            ttftMetric = 'visibleFirstTokenP50Ms'
            visibleFirstTokenP50Ms = $parsed.summary.visibleFirstTokenP50Ms
            nativeFirstTokenP50Ms = $parsed.summary.nativeFirstTokenP50Ms
            summary = $parsed.summary
            fatalError = $parsed.fatalError
        })
    } finally {
        & $adb -s $DeviceSerial shell am force-stop $TargetPackage | Out-Null
    }
}

if (($reports[0].caseOrder -join '|') -ne ($reports[1].caseOrder -join '|')) {
    throw '2B and 4B reports did not use the same fixed case order.'
}

$comparisonPath = Join-Path $resolvedOutput "$batchId-comparison.json"
$comparison = [ordered]@{
    schemaVersion = 1
    batchId = $batchId
    deviceSerial = $DeviceSerial
    package = $TargetPackage
    dataset = 'caesar_eval_v1'
    preferredTtftMetric = 'visibleFirstTokenP50Ms'
    caseOrder = $reports[0].caseOrder
    models = $reports
}
$comparisonJson = $comparison | ConvertTo-Json -Depth 20
[IO.File]::WriteAllText($comparisonPath, $comparisonJson + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))

Write-Output "Caesar Eval completed. Comparison: $comparisonPath"
