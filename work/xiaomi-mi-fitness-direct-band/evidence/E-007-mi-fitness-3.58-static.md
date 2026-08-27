### E-007
- title: Mi Fitness 3.58.0 downstream health interfaces and access gates
- observed_at: 2026-08-27T00:15:00+08:00
- source_type: file
- source_ref: `evidence/mi-fitness-3.58.0-static/com.mi.health-3.58.0-base.apk` and apktool output
- content_hash: `sha256:d784f3cab08bef7eeafff4e4bfde98c6bfcebee0b85964a6d07e40ba8b67c740`
- repro_command: |
    ```powershell
    $apktool = 'C:\path\to\apktool.bat'
    $apk = 'work\xiaomi-mi-fitness-direct-band\evidence\mi-fitness-3.58.0-static\com.mi.health-3.58.0-base.apk'
    $out = 'work\xiaomi-mi-fitness-direct-band\evidence\mi-fitness-3.58.0-static\apktool-decoded'
    & $apktool d -f $apk -o $out

    rg -n 'isInland|main land do not use health connect' -- `
      "$out\smali_classes11\com\xiaomi\fitness\repo\healthconnect\HealthConnectComponent`$checkAndStartSync`$1.smali"
    rg -n 'step_record|hr_record|sleep_segment|spo2_record|calorie_record|sport_report' -- `
      "$out\smali_classes11\com\xiaomi\fitness\repo\healthconnect\utils\HealthConnectDBTriggerUtils.smali"
    rg -n 'checkReadPermission|checkWritePermission|checkNormalPermission' -- `
      "$out\smali_classes9\com\xiaomi\fitness\dataprovider\DataContentProvider.smali"
    rg -n 'isPrivilegedPackage' -- `
      "$out\smali_classes9\com\xiaomi\fitness\dataprovider\DataProviderManager.smali"
    ```
- raw_excerpt: |
    - `HealthConnectComponent.checkAndStartSync` calls `RegionExtKt.isInland`. Only the non-inland and Health-Connect-installed branch registers DB triggers, synchronizes `step`, `hr`, `spo2`, `calorie`, `sleep`, synchronizes sport reports, and registers the receiver. The other branch logs `main land do not use health connect!!`.
    - `HealthConnectDBTriggerUtils` creates triggers for `step_record`, `hr_record`, `calorie_record`, `sleep_segment`, `spo2_record`, and `sport_report`; `RetryTaskScheduler` contains code that can enqueue unique WorkManager work named `sync_daily_records_to_health_connect`.
    - The inspected target contains no verified call site for that retry scheduler or its failure counter. SQLite triggers queue rows but do not themselves wake the exporter, and `HealthConnectDataSyncedReceiver` uses an in-process local broadcast. Therefore the APK proves export capability, not a guaranteed retry latency.
    - The APK declares Health Connect write permissions for steps, heart rate, sleep, oxygen saturation, distance, active calories, exercise, route, speed, and elevation. On the inspected phone they were all ungranted at capture time.
    - Exported `com.xiaomi.fitness.dataprovider.DataContentProvider` invokes internal read/write checks. `DataProviderManager.isPrivilegedPackage` permits system apps, trusted signatures/whitelist, or callers when the Mi Fitness build itself is debuggable.
    - Health paths such as `sleep`, `activity/steps`, `widget/steps`, and `heartrate` have empty normal-permission strings. A non-privileged caller reaches `checkNormalPermission`, where an empty permission produces `SecurityException`; ordinary CampusAI therefore cannot use this provider as a public health-data API.
    - `HealthProviderService` is protected by a signature/privileged permission. The separate exported device status provider exposes connection/battery metadata, not health history.
- linked_workitem: WI-007
- supersedes: none

#### Fixed artifact hashes

| Artifact | SHA-256 |
|---|---|
| `DataContentProvider.smali` | `8de9d670eeb605ba1e4e336c8b2f30e3b874b6a9de369c503558104b7b33dd1e` |
| `DataProviderManager.smali` | `8df26f594b4d2c7f994e0582edafc7ae9dc4066794f78d15f6f80893197ac4c5` |
| `y86.smali` provider registry | `85de9c6e49c8c9a04be0f12759d61c1f319287a6cd2197f7ebedf20f3a405e32` |
| `HealthConnectComponent$checkAndStartSync$1.smali` | `bad61865f53b986769702b184d150f38225450148a1ac284f68f3f4dc746fc6e` |
| `HealthConnectDBTriggerUtils.smali` | `8c3131dc29a3470df50ce35267df5a5aca722d47cd7f4804768ea9507730e0d8` |
| `RetryTaskScheduler.smali` | `546bded5170da8bb56d798e56013bba975465107d003b891093c1c8d842e74fb` |
