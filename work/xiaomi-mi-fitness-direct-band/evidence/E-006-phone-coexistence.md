### E-006
- title: Band 9 Classic SPP connection churn while the Xiaomi wearable stack remains active
- observed_at: 2026-08-27T00:05:00+08:00
- source_type: command
- source_ref: authorized user phone; `adb shell dumpsys package/activity services/bluetooth_manager`
- content_hash: n/a (Android runtime ring buffer; device identifiers were not retained)
- repro_command: |
    ```powershell
    $adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'

    foreach ($pkg in 'com.mi.health','com.xiaomi.mi_connect_service','nodomain.freeyourgadget.gadgetbridge.nightly') {
      $dump = & $adbPath shell dumpsys package $pkg | Out-String
      [pscustomobject]@{
        Package = $pkg
        VersionName = [regex]::Match($dump, '(?m)^\s*versionName=([^\r\n]+)').Groups[1].Value
        Running = [bool]((& $adbPath shell pidof $pkg | Out-String).Trim())
      }
    }

    $bt = & $adbPath shell dumpsys bluetooth_manager | Out-String
    $events = @($bt -split "`r?`n" | Where-Object {
      $_ -match 'CLASSICAL-(CONNECTED|DISCONNECTED)' -and $_ -match '(?i)Xiaomi\s+Sm'
    })
    [pscustomobject]@{
      Events = $events.Count
      Connected = @($events | Where-Object { $_ -match 'CLASSICAL-CONNECTED' }).Count
      Disconnected = @($events | Where-Object { $_ -match 'CLASSICAL-DISCONNECTED' }).Count
    }
    ```
- raw_excerpt: |
    - Mi Fitness `com.mi.health` 3.58.0 was installed and running; its device process and wearable services were active.
    - HyperOS `com.xiaomi.mi_connect_service` 5.1.251.10 was running persistently; Mi Fitness had two active bindings to `MiWearCoreService`.
    - Gadgetbridge nightly 0.93.0 was installed but not running at capture time.
    - The Bluetooth history contained 35 Classic events for the same redacted Xiaomi wearable: 15 connected and 20 disconnected. Thirty-four events occurred within roughly ten minutes.
    - For 15 pairable connection spans, the median lifetime was 0.777 seconds; 12 ended within two seconds and 14 within ten seconds. One outlier lasted about 69.8 minutes.
    - A second RFCOMM connection attempt appeared shortly after an already connected transition in one sequence and was followed by immediate failure/disconnection.
    - The device later showed an unpair event and was absent from the current bonded-device list.
    - `dumpsys bluetooth_manager` does not attribute every RFCOMM attempt or disconnect to a package, so this evidence demonstrates contention-like churn but does not identify the actor for each event.
- linked_workitem: WI-005
- supersedes: E-005
