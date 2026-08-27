# E-008

> Status note: this item records the static and offline phase. The previously unverified live
> server-acceptance boundary was subsequently validated once for CN `steps`; see E-010.

- title: Mi Fitness cloud read-only protocol and offline PoC
- observed_at: 2026-08-27T01:40:00+08:00
- source_type: file
- source_ref: `poc/mi_fitness_cloud_readonly/` and Mi Fitness 3.58.0 JADX sources
- content_hash: `sha256:47bbec7604f4594a1ffec7948a0f02ea861651c7208959ac720fd388ac24720f`
- repro_command: |
    ```powershell
    Set-Location 'work/xiaomi-mi-fitness-direct-band/poc/mi_fitness_cloud_readonly'
    py -3 -m py_compile readonly_poc.py tests/test_readonly_poc.py
    py -3 -m unittest discover -s tests -v
    py -3 readonly_poc.py
    ```
- raw_excerpt: |
    - Synthetic nonce hex: `00010203040506070000002a`
    - `SHA-256(ssecurity || nonce)` hex:
      `a15ec3ba390c242aed22b789a0cef9daf58e4622c4f6c62618d6cc3cb83786c4`
    - Synthetic encrypted-response prefix (32 of 162 bytes):
      `837bff65ad29a53889a3c754c66aeb2c3ab167f255b455e55397e62fb0181c0c`
    - Decoded safe summary:
      `{"cursor_present":false,"endpoint":"/app/v1/data/get_fitness_data_by_time","has_more":false,"metric":"steps","mode":"offline-vector","record_count":1,"status":"ok"}`
- linked_workitem: WI-009
- supersedes: none

Scope: authorized interoperability research against the user's own Xiaomi account and data. No
real credential, device key, account identifier, or health record was used or retained in this
evidence item.

## Pinned evidence

- Mi Fitness Android `com.mi.health` 3.58.0 (version code 358000), local JADX/apktool extraction
  already recorded by E-007.
- `kubulashvili/mi-fitness-mcp` at
  `07b61900fcd0ae364cb5c668256cf0d0b2884c46` (MIT, copyright 2026 Aleksej
  Kubulashvili).
- `alexgetmancom/miband-bot` at
  `99a22e11bd045b18375f89e3439c120b747573bc` (GPL-3.0; protocol corroboration only).
- PyPI `mi-fitness` 0.2.0 source distribution, SHA-256
  `0fb1bb16cbec948531e3bf7de8ac6456c2665775b059e64f441c42eb625bf369`
  (GPL-3.0; protocol corroboration only).

## Finding 1 — The current app/v1 read contract

`FitnessApiService.java` declares base URL `https://hlth.io.mi.com/app/v1/`. Its `ptb`
annotations are Retrofit GETs (`defpackage/ehm.java:183-184`). The minimum own-account read
surface is:

| Purpose | Method and path | Serialized request fields | Result fields |
|---|---|---|---|
| Fitness points by time | `GET /app/v1/data/get_fitness_data_by_time` | `key`, `start_time`, `end_time`, `reverse`, `next_key` | `data_list`, `has_more`, `next_key` |
| Sport records by time | `GET /app/v1/data/get_sport_records_by_time` | `category`, `start_time`, `end_time`, `reverse`, `next_key`, `limit` | `sport_records`, `has_more`, `next_key` |

Primary source locations:

- `FitnessApiService.java:4-5,74,134`
- `GetFitnessDataByTime.java:7-23`
- `GetSportReportByTime.java:7-27`
- `FitnessDataResultByTime.java:7-16`
- `SportReportResultByTime.java:7-16`

The own-data app/v1 payload does **not** contain `relative_uid`; that field belongs to relatives
APIs. The app/v1 fitness-by-time model also has no `limit`. The separate HealthKit
`RecordsResult<T>` model serializes `hasMore`, `watermark`, `fitnessPoints` (alternates
`sportPoints`/`aggregateDatas`) and `cursor`; it must not be used to parse the app/v1 snake-case
response above (`RecordsResult.java:7-18`).

The aggregate endpoint exists, with model fields `tag`, `key`, `reverse`, `limit`, `next_key`,
`start_time`, and `end_time`, but its tag/paging semantics are deliberately excluded from the
first PoC. All non-workout metrics use the better-corroborated fitness-by-time route.

## Finding 2 — Request encryption and signatures

The APK establishes this algorithm:

1. Nonce is 8 random bytes followed by big-endian `uint32((now_ms + timeDiff) / 60000)` and then
   Base64 (`vh4.java:7-17`, called by `CloudInterceptor.java:188-190`).
2. `signedNonce = SHA-256(base64decode(ssecurity) || base64decode(nonce))`, represented as
   Base64 when included in a signature.
3. The SHA-1/Base64 signature message is uppercase method, encoded path, sorted `k=v` entries,
   and signedNonce Base64, joined with `&` (`vh4.java:20-51`).
4. Add plaintext `rc4_hash__`; create **one** RC4 object, drop 1024 bytes once, then encrypt all
   sorted values through that continuing stream (`ri4.java:16-38`, `sxk.java:11-24,51-67`).
5. Sign the encrypted map and add plaintext `_nonce`. A successful response is Base64-decoded and
   decrypted with a fresh RC4-drop-1024 stream (`CloudInterceptor.java:31-34,208-220`).

This reveals two interoperability defects in the pinned MIT implementation: it resets RC4 for
each form value and sends these app/v1 reads as POST. Its golden vectors are generated from the
same implementation, so they do not establish APK compatibility. The PoC instead has a frozen,
synthetic GET vector exercising the continuous stream.

## Finding 3 — Login and cookie state machine

The minimal pass-token exchange implemented by the PoC is:

1. Generate an ephemeral `deviceId` and make
   `GET https://account.xiaomi.com/pass/serviceLogin?sid=miothealth&_json=true&appName=com.mi.health&_locale=zh_CN`.
   Send `userId`, `passToken`, and `deviceId` only in this request's explicit Cookie header.
2. Parse the `&&&START&&&` JSON and require `userId`, `cUserId`, `ssecurity`, and `location`.
3. Permit only HTTPS locations under `*.xiaomi.com` or `*.mi.com`. Require `nonce`, then append
   `clientSign = Base64(SHA1("nonce=<nonce>&<ssecurity>"))` and
   `_userIdNeedEncrypt=true`. The APK AccountSDK derives and sends
   this value in `XMPassport.java:484-487,636-652`; its generic signature construction is visible
   in `Coder.java:20-46,81-102`.
4. Follow that verified location while sending the generated `deviceId`, then prefer the
   `miothealth_serviceToken` cookie and fall back to `serviceToken` or a final query value.
5. Purge any `userId`, `passToken`, and `deviceId` cookies, discard the login opener, and make the
   health request through a separate cookie-less opener. The health Cookie header contains
   `cUserId`, `serviceToken`, and non-secret `locale=zh_cn`, matching
   `AccountModule.java:18-27`, `VerifyToken.java:103-123`, and
   `ParameterInterceptor.java:8-18`.

All redirects are constrained to Xiaomi HTTPS hosts and strip an explicit Cookie header before
following. `ProxyHandler({})` disables inherited `HTTP_PROXY`/`HTTPS_PROXY`. The pass token is
accepted only by a hidden prompt, is not an environment/CLI option, is not returned in
`SessionMaterial`, and is never persisted or printed.

No hard-coded `healthapp/sts?p_ur=CN...` URL is used. For `sid=miothealth`, the AccountSDK control
flow always performs a second GET, but its target is the dynamic `location` returned by the first
response (`XMPassport.java:1025-1030,636-662`); guessing a fixed STS endpoint is incorrect.

## Region mapping

The APK statically proves the CN base host. Pinned OSS consistently maps `cn` (and sometimes an
empty region) to `https://hlth.io.mi.com` and maps `ru`, `de`, `i2`, `sg`, and `us` to
`https://<region>.hlth.io.mi.com`. The PoC uses a closed allowlist and performs no arbitrary-host
probing. Non-CN mappings remain OSS-corroborated rather than live-verified in this case.

## Errors, refresh, and deliberate PoC limits

- Mi Fitness `VerifyToken` refreshes on HTTP 401, serializes concurrent refresh, retries up to
  three times, and adjusts `timeDiff` from the server Date header
  (`VerifyToken.java:35-41,75-112,170-219`).
- The pinned MIT adapter retries every exception three times but does not perform a token refresh;
  it can repeat non-transient protocol/business failures.
- The PoC performs one health-data GET after authentication and no automatic 401 refresh. It
  retries only authentication transport failures and reports sanitized status/errors. Pagination,
  token persistence, background scheduling, and raw-record output are excluded.

## Offline validation artifact

Location: `poc/mi_fitness_cloud_readonly/`

- `readonly_poc.py`: pure Python standard library; read-only endpoint allowlist; login isolation;
  RC4/signing/decryption; summary-only output.
- `fixtures/protocol_vectors.json`: fully synthetic nonce, security value, login payload, encrypted
  form, encrypted response, and fake health record.
- `tests/test_readonly_poc.py`: 18 offline tests covering the frozen crypto vector, response
  decryption, exact app/v1 payloads, write rejection, strict region/redirect handling, proxy
  suppression, Cookie isolation, and a two-response fake-opener pass-token exchange.
- `README.md`, `NOTICE.md`: operator guardrails and license boundaries.

Validation commands:

```powershell
py -3 -m py_compile readonly_poc.py tests/test_readonly_poc.py
py -3 -m unittest discover -s tests -v
py -3 readonly_poc.py
```

Result: 18 tests passed; offline CLI returned a seven-field summary with `record_count=1` and no
raw value.

## Unverified boundaries

- No live Xiaomi credential or health record was used in E-008 itself. E-010 later confirmed one
  successful CN request; long-duration and account-challenge behavior remain unverified.
- The PoC assumes the local clock is accurate; it does not yet preserve the APK's server-derived
  `timeDiff` across requests.
- Non-CN region routing and individual metric-key availability can vary by account/firmware and
  need one authorized live read to validate.
- There is no certificate pinning beyond the operating system trust store.
- Cloud acquisition avoids Bluetooth ownership contention, but freshness still depends on Mi
  Fitness completing band-to-cloud sync, Internet reachability, token validity, and Xiaomi service
  availability.

## Solve path

Run the default offline vector first. E-010 records the successful controlled live GET for
`steps`, with the official Mi Fitness/Mi Connect stack left untouched. The next phase is to add
pagination, server time-difference handling, and bounded 401 refresh as separate reviewed changes
before integrating a scheduler or database sink.
