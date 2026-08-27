# Work Items

| ID | title | role | targets | surface | status | evidence | notes |
|----|-------|------|---------|---------|--------|----------|-------|
| WI-001 | Establish scope and auth | lead | case | process | completed | `scope.md` | Authorized own-device interoperability research |
| WI-002 | Map current CampusAI health/bridge boundary | lead | local repository | local_app_integration | completed | E-001 | Direct gateway replacement point identified |
| WI-003 | Triage HyperOS wearable system Binder | cre | decoded system APK | android_apk | completed | E-002 | Exported service is Xiaomi-certificate gated |
| WI-004 | Research Band 9 direct protocol implementations | cre | public OSS repositories | bluetooth_classic_spp | completed | E-003, E-004 | Pinned commits and source blobs recorded |
| WI-005 | Validate exact user device and Mi Fitness build | lead | phone, band, Mi Fitness APK | dynamic | completed | E-005, E-006 | Read-only ADB capture shows severe Classic SPP churn while the Xiaomi stack remains active |
| WI-006 | Produce phase-one feasibility report | doc | docs | report | completed | E-001..E-005 | Includes solve path and Mermaid architecture |
| WI-007 | Reverse local downstream interfaces in Mi Fitness 3.58.0 | cre | Mi Fitness APK | android_apk | completed | E-007 | Health Connect is mainland-gated; exported health provider rejects ordinary callers |
| WI-008 | Produce coexistence stability addendum | doc | docs | report | completed | E-003, E-004, E-006, E-007 | Replaces direct-SPP recommendation under the user's coexistence constraint |
| WI-009 | Validate private cloud read protocol and offline PoC | cre | Mi Fitness APK and pinned OSS | cloud_api | completed | E-008 | Pure-stdlib, one-read PoC with 18 synthetic offline tests; live acceptance is tracked by WI-011/E-010 |
| WI-010 | Determine whether CampusAI can self-enroll in Mi Fitness's provider whitelist | cre | Mi Fitness APK and CampusAI application ID | android_ipc | completed | E-009 | No production registration path; vendor package-and-signature enrollment would be required |
| WI-011 | Validate one authorized CN cloud read | cre | User-owned Xiaomi account and Mi Fitness cloud | cloud_api | completed | E-010 | Live one-day steps GET succeeded with 11 records; no raw records or credentials retained |
| WI-012 | Integrate the bounded CN steps reader into CampusAI | lead | CampusAI Android app | local_app_integration | completed | E-011 | Manual-only cloud access, Keystore cache, configured fail-closed reads, Band isolation, Agent allowlist, 201 tests, and debug build validated |
| WI-013 | Correct and validate installed zero-step behavior | lead | CampusAI Android app and authorized phone | local_app_integration | completed | E-012 | Empty cloud responses are NO_DATA rather than 0; system proxy selection restored; account-switched UI is isolated; 210 tests and debug APK validated |

## Coverage
- [x] Recon/analysis complete for currently available in_scope assets
- [x] Critical/High candidates triaged (N/A for interoperability RE)
- [x] Validated findings have Evidence (E-*)
- [x] Path documented (solve)
- [x] Timeline continuous across major phases
- [x] Report via docs-generator
- [x] field-journal anonymized
- [x] Cloud PoC is read-only, offline-tested, and contains no real credentials or health records
- [x] Provider whitelist conclusion is backed by caller and call-site analysis
- [x] One authorized CN `steps` read is accepted by the live service without Bluetooth ownership
- [x] CampusAI production path is manual-only, encrypted, bounded, cancellable, fail-closed, Agent/Band isolated, unit-tested, and buildable

## Refs
- skills/ops/timeline-workitem.md
- skills/ops/evidence-finding-path.md
