# E-010

- title: Authorized Mi Fitness CN cloud live read
- observed_at: 2026-08-27T15:15:49.7143915+08:00
- source_type: runtime_capture
- source_ref: user-attached terminal screenshot; not copied into the case workspace
- content_hash: `sha256:ce2bf8c5862f04ef2000edfa5b99baac0a14987fa284549f8ccc8f412ab4d8f1`
- repro_command: |
    ```powershell
    py -3 readonly_poc.py --live --metric steps --days 1 --region cn --utc-offset +08:00
    ```
- safe_summary: |
    ```json
    {"cursor_present":false,"endpoint":"/app/v1/data/get_fitness_data_by_time","has_more":false,"metric":"steps","mode":"read-only","record_count":11,"region":"cn","status":"ok","token_refreshed_in_memory":false}
    ```
- linked_workitem: WI-011
- related_evidence: E-008
- supersedes: none

Scope: one explicitly authorized read of the user's own Xiaomi health-cloud data. The official Mi
Fitness/MiWearCore stack remained the Bluetooth owner. The pass token was entered through the
hidden prompt; no account identifier, credential, device key, raw response, or individual health
record is retained in this evidence item.

## Observation

The complete live path succeeded for the mainland China service:

1. `sid=miothealth` account exchange completed.
2. The dynamic STS location returned a usable service token.
3. Xiaomi accepted the encrypted `GET /app/v1/data/get_fitness_data_by_time` request for `steps`.
4. The response decrypted and parsed successfully.
5. The one-day window contained 11 records, with `has_more=false` and no cursor.
6. The account exchange did not return a different pass token
   (`token_refreshed_in_memory=false`). This field does not represent 401/service-token refresh.

The PoC printed only the summary above. It did not establish a Bluetooth connection and therefore
did not compete with Mi Fitness or MiWearCore for the Band 9 SPP channel.

## What this validates

- The target account and the current CN service accept the recovered two-hop authentication flow.
- The APK-derived GET method, payload shape, signature, continuous RC4-drop-1024 request stream,
  and response decryption are interoperable with the live server.
- The cloud route removes the previously observed Bluetooth connection-ownership conflict.

## Remaining boundaries

- This is one successful point-in-time request, not yet a long-duration reliability measurement.
- Only the `steps` metric and a non-paginated one-day window were exercised.
- Raw record schemas for sleep, heart rate, SpO2, stress, and workouts remain unverified.
- Freshness still depends on Mi Fitness syncing the band to Xiaomi Cloud.
- Bounded 401 refresh, server time-difference persistence, pagination, backoff, and private-API
  change detection must be added before production scheduling.
