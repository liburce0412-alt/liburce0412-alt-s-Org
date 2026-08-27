# E-012

- title: CampusAI false-zero and Android proxy coexistence runtime fix
- observed_at: 2026-08-28T02:55:00+08:00
- source_type: source_build_and_authorized_device_validation
- source_ref: CampusAI Android source, local unit-test results, debug APK, and redacted ADB runtime status
- content_hash: `sha256:dcb628c9179110cdf5d0fa14ac07ad5137e06dd7770c2091255f4ef233a42229`
- repro_commands: |
    ```powershell
    .\gradlew.bat :apps:android:app:testDebugUnitTest :apps:android:app:lintDebug :apps:android:app:assembleDebug
    adb install -r app-debug.apk
    ```
- safe_summary: `210 tests, 0 failures, 0 errors; lint and debug APK build passed; final APK installed and cold-launched; login, STS, and steps returned HTTP 200 after the phone VPN was disabled; an empty CN current-day response is now NO_DATA rather than 0`
- linked_workitem: WI-013
- related_evidence: E-010, E-011
- supersedes: none

The runtime check retained no account identifier, credential, raw response, individual record, or
step total. Diagnostic logging contains only a fixed stage name, HTTP status, transport exception
class, and allowlisted result code.

## Validated correction

1. A complete response with no records no longer enters aggregation or cache persistence. The UI
   reports that the cloud has not returned today's records instead of displaying an inferred zero.
2. Legacy summaries with `recordCount=0` are ignored. A legitimate zero remains representable only
   when at least one source record explicitly carries zero steps.
3. The request-scoped parser accepts a missing per-item `key`, requires `steps` when the field is
   present, and fails closed on missing or malformed `value.steps`.
4. Valid credentials and a refreshed passToken are persisted even when the current-day data list is
   empty; no zero summary is written.
5. Saving credentials for an account whose current-day list is empty first clears the prior
   account's visible cloud snapshot and timestamp, then reloads the account-scoped gateway.
6. The Android client no longer hard-codes `Proxy.NO_PROXY`; it follows the platform proxy selector.
   Before this correction, a running FlClash configuration resolved Xiaomi hosts to `198.18.x.x`
   fake IPs and the forced proxy bypass failed at the login connection stage.
7. With the phone VPN disabled, the installed app completed login, dynamic STS exchange, and the CN
   steps request with HTTP 200. The server returned no records for the new CN calendar day, which
   exercised the corrected `NO_DATA` path.
8. Mi Fitness and Xiaomi connectivity services remained running during the check. The separate
   CampusAI Band bridge process remained absent, so the cloud read did not contend for the wearable.

## Remaining boundary

- The proxy-selector correction is unit-tested but was not re-run live with the VPN enabled after
  the user disabled it. VPN/proxy products can still apply per-app exclusions or filtering.
- The current-day cloud list can lag the official phone-to-cloud upload. Empty data is now reported
  accurately, but it is not made real-time by CampusAI.
- A non-empty live record payload still needs a privacy-preserving comparison against the official
  daily total before the provisional sum aggregation can be declared authoritative.
