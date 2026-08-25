param(
    [Parameter(Mandatory = $true, ParameterSetName = 'File')]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $Path,

    [Parameter(Mandatory = $true, ParameterSetName = 'Hex')]
    [ValidatePattern('^[0-9A-Fa-f\s]+$')]
    [string] $Hex
)

$ErrorActionPreference = 'Stop'

if ($PSCmdlet.ParameterSetName -eq 'File') {
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path))
} else {
    $normalized = $Hex -replace '\s', ''
    if (($normalized.Length % 2) -ne 0) {
        throw 'Hex input must contain complete bytes.'
    }
    $bytes = [byte[]]::new($normalized.Length / 2)
    for ($index = 0; $index -lt $bytes.Length; $index++) {
        $bytes[$index] = [Convert]::ToByte($normalized.Substring($index * 2, 2), 16)
    }
}

if ($bytes.Length -lt 17) {
    throw "Daily summary is too short: $($bytes.Length) bytes."
}

$timestampSeconds = [BitConverter]::ToInt32($bytes, 0)
$timezoneQuarters = [int][sbyte]$bytes[4]
$version = [int]$bytes[5]
$flags = [int]$bytes[6]
$type = ($flags -shr 7) -band 0x01
$subtype = ($flags -band 0x7f) -shr 2
$detailType = $flags -band 0x03
$headerSize = switch ($version) {
    3 { 3 }
    4 { 3 }
    5 { 4 }
    default { throw "Unsupported daily summary version: $version" }
}
$bodyStart = 8 + $headerSize
if ($bytes.Length -lt ($bodyStart + 6)) {
    throw "Daily summary v$version is missing its steps/calorie slots."
}

$stepsValid = (($bytes[8] -band 0x80) -ne 0)
$activeCaloriesValid = (($bytes[8] -band 0x40) -ne 0)
$steps = if ($stepsValid) { [BitConverter]::ToInt32($bytes, $bodyStart) } else { $null }
$activeCalories = if ($activeCaloriesValid) { [BitConverter]::ToUInt16($bytes, $bodyStart + 4) } else { $null }
$offset = [TimeSpan]::FromMinutes($timezoneQuarters * 15)
$observedAt = [DateTimeOffset]::FromUnixTimeSeconds($timestampSeconds).ToOffset($offset)

[ordered]@{
    observedAt = $observedAt.ToString('o')
    timezoneQuarterHours = $timezoneQuarters
    version = $version
    type = $type
    subtype = $subtype
    detailType = $detailType
    isActivityDailySummary = ($type -eq 0 -and $subtype -eq 0 -and $detailType -eq 1)
    validityBitmap = [Convert]::ToHexString($bytes[8..(8 + $headerSize - 1)])
    steps = $steps
    activeCalories = $activeCalories
} | ConvertTo-Json
