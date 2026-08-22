param(
    [string]$ApkPath = ""
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $candidates += Join-Path $env:USERPROFILE '.gradle/campusai-build/android-app/outputs/apk/debug/app-debug.apk'
    }
    $candidates += Join-Path $PSScriptRoot '../apps/android/app/build/outputs/apk/debug/app-debug.apk'
    $ApkPath = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if ([string]::IsNullOrWhiteSpace($ApkPath)) { throw 'APK not found in the repository build directory or CampusAI Windows cache.' }
if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }
$apk = Get-Item -LiteralPath $ApkPath
if ($apk.Length -ge 100MB) { throw "APK is unexpectedly large: $($apk.Length) bytes" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk.FullName)
try {
    $names = @($archive.Entries | ForEach-Object FullName)
    $forbidden = @($names | Where-Object {
        $_ -match '(^|/)(llm|visual)\.mnn($|\.)' -or
        $_ -match '(^|/)tokenizer\.txt$' -or
        $_ -match '(^|/)llm_config\.json$' -or
        $_ -match '\.part$'
    })
    if ($forbidden.Count -gt 0) { throw "Model data found in APK: $($forbidden -join ', ')" }
    if (-not ($names -contains 'lib/arm64-v8a/libcampusai_mnn.so')) { throw 'CampusAI MNN JNI runtime missing' }
    if (-not ($names -contains 'lib/arm64-v8a/libllm.so')) { throw 'Pinned MNN LLM runtime missing' }
    "APK local-AI packaging OK: $($apk.Length) bytes; no model data; arm64 runtime present."
} finally {
    $archive.Dispose()
}
