# E-011

- title: CampusAI Mi Fitness CN steps read-only integration
- observed_at: 2026-08-27T16:42:08+08:00
- source_type: source_and_build_validation
- source_ref: CampusAI Android source, local unit-test results, and debug APK
- content_hash: `sha256:43890612a85b5696590895ab2e2b9e34ac471c042ca80d39bbcc3cfb04ccc8e3`
- repro_commands: |
    ```powershell
    .\gradlew.bat :apps:android:app:testDebugUnitTest --max-workers=1
    .\gradlew.bat :apps:android:app:assembleDebug --max-workers=1
    .\gradlew.bat :apps:android:app:lintDebug --max-workers=1
    ```
- safe_summary: `201 tests, 0 failures, 0 errors; debug APK and lint succeeded; lint had 0 fatal and 0 error findings`
- linked_workitem: WI-012
- related_evidence: E-008, E-010
- supersedes: none

No live Xiaomi request was made while producing this evidence. Tests use synthetic credentials,
responses, clocks, and OkHttp interceptors. No account identifier, passToken, Band key, service
token, raw health response, or individual health record is included.

## Validated implementation boundary

1. Only the native save/validate action and explicit manual refresh can reach Xiaomi Cloud.
   Startup, ordinary UI refresh, and Agent health reads do not enqueue cloud work.
2. The production transport can represent only the allowlisted CN `steps` GET. Responses, cursors,
   records, and totals are bounded; records outside the CN day are rejected.
3. userId/passToken and the aggregate steps summary use Android Keystore-backed encrypted storage.
   The secure preference file is excluded from backup and device transfer. Work input is empty and
   failure output contains only a fixed allowlisted error code.
4. Mi Fitness configuration makes the health gateway fail closed on an absent Mi cache; it does not
   silently substitute Health Connect data. Without Mi credentials, the existing Health Connect
   mode remains available.
5. Configured Mi mode never queries or starts Gadgetbridge/CaesarBandBridge. Agent health tools have
   no Band or sync dependency and cannot trigger cloud refresh.
6. OkHttp calls are coroutine-cancellable, cancellation calls `Call.cancel()`, individual calls have
   a timeout, and the complete sync has a 90-second upper bound. Deleting credentials during a
   manual refresh cancels that Work first.
7. The current Agent turn may execute only tools included in that turn's projected allowlist. A
   known but unprojected health tool name is rejected with a fixed error.
8. UI states use fixed text for validation, refresh, deletion, authentication, network, and storage
   failures. Saved credentials are never echoed and form input is cleared after success.

## Test coverage

- protocol vectors, continuous RC4-drop-1024, strict Base64, cookie isolation, dynamic Xiaomi HTTPS
  STS validation, response limits, and account binding;
- pagination limits, cursor rejection, CN-day range checks, aggregation bounds, token refresh, cache
  rollback, storage failure, account isolation, period aliases, and system-time-zone mapping;
- WorkData allowlisting, immediate one-time unique Work, OkHttp cancellation, configured fail-closed
  gateway behavior, Agent/Band dependency isolation, projected-tool enforcement, and fixed UI copy.

## Remaining boundary

- The private API may change or trigger Xiaomi account risk controls.
- Freshness depends on Mi Fitness/MiWearCore uploading Band data before the user refreshes CampusAI.
- Only CN current-day steps are enabled. Sleep, heart rate, SpO2, stress, workouts, and background
  scheduling remain out of scope.
- The observed record schema and sum aggregation remain provisional; the first result must be
  compared with Mi Fitness's displayed daily total before treating it as authoritative.
- The assembled artifact is a debug APK, not a release-signed distribution build.
