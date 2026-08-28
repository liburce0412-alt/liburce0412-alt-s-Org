# E-013

- title: Mi Fitness 3.58.0 official daily aggregate contract and CampusAI typed health implementation
- observed_at: 2026-08-28T13:44:54+08:00
- source_type: authorized_apk_static_analysis_and_source_build_validation
- source_ref: locally installed `com.mi.health` 3.58.0 base APK, JADX output, CampusAI Android source, and synthetic unit fixtures
- content_hash: `sha256:d784f3cab08bef7eeafff4e4bfde98c6bfcebee0b85964a6d07e40ba8b67c740`
- repro_commands: |
    ```powershell
    adb shell pm path com.mi.health
    adb pull /data/app/.../com.mi.health-.../base.apk com.mi.health-base.apk
    jadx --no-res --no-debug-info -j 4 -d jadx com.mi.health-base.apk
    rg -n "get_aggregated_fitness_data_by_time|SerializedName|CloudStepReport|getCloudRequestKey" jadx/sources/com/xiaomi/fit/fitness
    .\gradlew.bat :apps:android:app:testDebugUnitTest --tests 'com.campusai.core.health.mifitness.*' --max-workers=1
    ```
- safe_summary: `Mi Fitness defines a vendor daily aggregate GET endpoint and an exact snake_case request/response model; CampusAI now uses that result as the sole daily total, keeps by-time buckets as series only, and passed 56 focused synthetic tests`
- linked_workitem: WI-014
- related_evidence: E-007, E-008, E-010, E-011, E-012
- supersedes: `E-008 provisional bucket-sum interpretation; E-012 remaining daily-total ambiguity`

The APK hash matches E-007. The extraction was read-only and authorized by `scope.md`. No account
identifier, credential, service token, raw live response, device/source identifier, or real health
metric was copied into the repository or test fixtures. The pulled APK and JADX directory were
temporary analysis artifacts and were deleted after this evidence was recorded.

## Exact static contract

1. `com/xiaomi/fit/fitness/persist/server/service/FitnessApiService.java:98` declares
   `data/get_aggregated_fitness_data_by_time`. With the service base from E-008, the exact read-only
   path is `GET /app/v1/data/get_aggregated_fitness_data_by_time`.
2. `AggregateFitnessDataByTimeParam.java:14-40` is the relevant request model. Its wire fields are
   `tag`, `key`, `reverse`, `limit`, `next_key`, `start_time`, and `end_time`. The `(tag, nextKey)`
   constructor supplies `reverse=true` and `limit=100`. CampusAI sends an explicit verified key and
   the device-local half-open day bounds; it does not depend on the unrelated camelCase
   `GetAggregatedDataByTimeParam` model.
3. `AggregateFitnessDataByTime.java:15-18` maps the response to `data_list`, `next_key`, and
   `has_more`.
4. `AggregateFitnessData.java:13-45` defines each item as `tag`, `key`, `time`, `value`,
   `zone_offset`, optional `sid`, optional `zone_name`, and optional `source_sid_list`. A live
   read-only response on 2026-08-28 showed that the server may omit `zone_offset`; CampusAI therefore
   validates it when present and otherwise relies on the already explicit device-local request
   window. It retains only the contributing-source count, never the identifiers.
5. `CloudStepReport.java:14-31` proves the official step aggregate value contains required integer
   `steps`, `distance`, and `calories`, with optional `goal`. `CloudDailyHelper.java:336,348`
   constructs that report from the official daily step report and source records;
   `CloudDailyHelper.java:462` supplies the request tag/key and source list to the aggregate.
6. `CloudKey.java:140-176` supplies the exact keys. `DataCloudUtilKt.getRequestTag` maps the daily
   scalar/report types used here to `daily_report`.
7. The distinct series endpoint remains
   `GET /app/v1/data/get_fitness_data_by_time`. Its request uses only the statically and live-
   verified `key`, `start_time`, `end_time`, `reverse`, and `next_key` fields; its response preserves
   `data_list`, `has_more`, and `next_key`. Records are timestamped trend points, and no production
   API can pass those points to a daily-sum function.
8. Workout count uses the separately verified read-only endpoint
   `GET /app/v1/data/get_sport_records_by_time` with the verified `category=""` payload field;
   deleted records are excluded and source identifiers are one-way digested only for in-memory
   duplicate suppression.

## Semantically verified metric registry

The implemented `daily_report` keys and their typed fields are:

| key | verified fields | CampusAI units |
| --- | --- | --- |
| `steps` | `steps`, `distance`, `calories`, optional `goal` | count, meters, kcal |
| `calories` | `calories`, optional `goal` | kcal |
| `intensity` | `duration` | minutes |
| `valid_stand` | `count` | count |
| `sleep` | `total_duration`, deep/light/REM/awake durations, optional score | minutes, score |
| `heart_rate` | `avg_hr`, `max_hr`, `min_hr`, optional `avg_rhr` | bpm |
| `spo2` | `avg_spo2`, `max_spo2`, `min_spo2` | percent |
| `stress` | `avg_stress`, `max_stress`, `min_stress` | 0–100 score |
| `vo2_max` | `avg_vo2_max`, `max_vo2_max`, `min_vo2_max` | ml/kg/min |
| sport records | non-deleted records | count |

Other `CloudKey` mappings (for example energy, PAI, blood pressure, blood sugar, weight, hearing,
and menstruation) are evidence of a request key, not evidence of a stable value shape and unit.
They are deliberately not exposed as numeric product metrics until their report models and display
semantics are independently confirmed. Unknown keys and shapes fail closed instead of disappearing
or being shown as zero.

## Runtime semantics now enforced

- A device-local day is `[00:00, next-day 00:00)`. Records at the exclusive end are rejected.
- The daily card selects one vendor aggregate. Byte-equivalent repeated page items are deduplicated;
  conflicting items fail with an aggregate conflict. There is no bucket addition fallback.
- Page limits, record limits, missing/repeated cursors, out-of-window records, malformed integer and
  decimal values, oversized payloads, authentication failures, transport errors, and timeouts fail
  closed.
- Metric state is explicit: `AVAILABLE`, `EMPTY`, `PARTIAL`, `STALE`, or `ERROR`. Missing optional
  report fields are `PARTIAL`; an empty response is `EMPTY`; neither becomes numeric zero.
- Step trends independently enforce half-open day bounds, pagination and record limits, repeated
  cursor detection, identical-point deduplication, and same-timestamp conflict rejection. A trend
  failure becomes `PARTIAL` or `ERROR` and never changes the authoritative daily total.
- Cached format v3 contains typed scalar summaries, step-series points, units, stable diagnostic
  codes, and redacted provenance only. Earlier provisional/bucket-sum formats are ignored.
- The product performs cloud reads only. It does not open BLE, stop Mi Fitness, stop Xiaomi
  connectivity services, or attempt to win a connection race with them.

## Validation boundary

The previously recorded focused Mi Fitness suite completed with 56 tests and zero failures. The v3
trend expansion adds synthetic parser, pagination, duplicate-cursor, half-open-window, encrypted
cache, gateway, and presentation cases; those changes require a fresh single-worker focused run
before release. All response fixtures are synthetic and explicitly marked desensitized. Final
device acceptance still requires one manual sync-and-compare against the official Mi Fitness daily
cards for each metric; that real comparison must remain outside Git and CI.
