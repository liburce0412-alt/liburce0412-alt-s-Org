# Mi Fitness cloud read-only PoC

This standalone PoC validates the Xiaomi Health RC4 request/response format without touching the
CampusAI application. Its default mode is fully offline and uses synthetic data only.

```powershell
python readonly_poc.py
python -m unittest discover -s tests -v
```

Live mode exchanges the pass token through `sid=miothealth`, follows only the validated Xiaomi
HTTPS location returned by the first login response, adds the AccountSDK-compatible `clientSign`
and `_userIdNeedEncrypt=true`, then performs exactly one allowlisted health-data `GET`.
(Authentication itself is a fixed two-request flow.) It prints only an endpoint/record-count summary and does not
persist credentials, cookies, decrypted responses, or health records. The pass token has neither
a CLI argument nor an environment-variable input, so it is entered only through a hidden prompt.

```powershell
python readonly_poc.py --live --metric steps --days 1 --region cn --utc-offset +08:00
```

Live validation status (2026-08-27): one authorized CN `steps` request completed successfully and
returned a summary with 11 records and no next page. The account exchange did not rotate the pass
token; this was not a 401/service-token refresh test. See
[E-010](../../evidence/E-010-mi-fitness-cloud-live-read.md). This is an interoperability check,
not yet a long-duration reliability claim.

The fixed UTC offset defines the local-day query window without requiring a third-party timezone
database on Windows. The program prompts for `userId` and hides the `passToken`. For
convenience, only the non-secret user ID may be supplied as `MI_FITNESS_USER_ID`.

The long-lived `userId` and `passToken` are sent only on the initial `account.xiaomi.com` request
and are never planted as broad `.mi.com` cookies. The generated, ephemeral `deviceId` is also sent
to the validated Xiaomi STS location, matching the AccountSDK, and is removed before data access.
The health API request uses a separate cookie-less opener and an explicit cookie header containing
only `cUserId`, `serviceToken`, and the non-secret `locale`. The opener also ignores
`HTTP_PROXY`/`HTTPS_PROXY` environment variables so an ambient debugging proxy cannot silently
receive the pass token.

Implemented read-only routes:

- fitness data by time: steps, sleep, heart rate, SpO2, calories, stand, intensity, weight, blood
  pressure and stress;
- workout history by time.

The first route intentionally uses the Mi Fitness 3.58.0 `GetFitnessDataByTime` payload exactly:
`key`, `start_time`, `end_time`, `reverse`, and `next_key`. It does not use the relatives-only
`relative_uid` field and does not guess an unsupported `limit` field. The corresponding app/v1
response uses `data_list`, `has_more`, and `next_key`.

The PoC deliberately excludes every upload, delete, invitation and relationship-management
route. Pagination, automatic 401 refresh, durable token storage, scheduled sync and CampusAI
integration are outside this standalone validation artifact.

Important protocol correction: Xiaomi Mi Fitness 3.58.0 keeps one RC4 stream across the sorted
`data` and `rc4_hash__` values. Resetting RC4 independently for every value produces a
self-consistent unit test but does not match the app implementation.

See [NOTICE.md](NOTICE.md) for source pins and licensing boundaries.
