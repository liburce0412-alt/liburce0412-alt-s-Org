# Timeline (append-only)

## 2026-08-26T23:00:15.9210790+08:00 | lead | init
- action: case-init
- command_or_ref: skills/scripts/case-init.ps1
- result_summary: case directory created; scope ready_for_act=true
- artifacts: [scope.md, workitems.md]
- evidence_ids: []
- next: open PRIMARY SKILL.md and ACT within scope

## 2026-08-26T23:08:00+08:00 | lead | workspace-recon
- action: map existing health and bridge architecture
- command_or_ref: `rg` over README, apps/android, scripts, and artifacts
- result_summary: current implementation depends on Gadgetbridge/Health Connect and an out-of-process CaesarBandBridge; `BandLiveGateway` is the direct replacement seam
- artifacts: [evidence/E-001-current-integration.md]
- evidence_ids: [E-001]
- next: research true direct transport and authentication

## 2026-08-26T23:16:00+08:00 | cre | system-apk-triage
- action: inspect decoded HyperOS Mi Connect wearable Binder
- command_or_ref: `PermissionChecker.java`, `MiWearCoreService.java`, decoded manifest
- result_summary: MiWearCore Binder is exported but certificate-whitelisted to Xiaomi applications, so it is not a usable third-party API
- artifacts: [evidence/E-002-mi-wear-core-gate.md]
- evidence_ids: [E-002]
- next: inspect public direct-protocol implementations

## 2026-08-26T23:28:00+08:00 | cre | oss-protocol-research
- action: pin and inspect Gadgetbridge, Band 9 recovery research, Mi Fitness MCP, and huami-token
- command_or_ref: shallow Git clones plus `git show` at recorded commits
- result_summary: Band 9 direct transport is Bluetooth Classic SPP with Xiaomi auth/protobuf; cloud history and key retrieval are separate auxiliary routes
- artifacts: [evidence/E-003-gadgetbridge-protocol.md, evidence/E-004-cloud-and-key-tools.md]
- evidence_ids: [E-003, E-004]
- next: identify runtime validation prerequisites

## 2026-08-26T23:36:00+08:00 | lead | runtime-triage
- action: query ADB and inventory local APKs
- command_or_ref: `adb devices -l`; recursive APK inventory
- result_summary: no attached Android device and no Mi Fitness APK; dynamic verification deferred by explicit phase boundary
- artifacts: [evidence/E-005-runtime-gap.md]
- evidence_ids: [E-005]
- next: deliver phase-one report and request the user's route choice

## 2026-08-26T23:45:00+08:00 | doc | report
- action: produce Evidence → Finding → Path feasibility report
- command_or_ref: docs-generator and diagram-generator workflows
- result_summary: direct in-app SPP route recommended; cloud route documented as a faster non-direct fallback; AGPL boundary recorded
- artifacts: [../../docs/2026-08-26_reverse-xiaomi-band9-direct-report.md]
- evidence_ids: [E-001, E-002, E-003, E-004, E-005]
- next: user selects dynamic validation, cloud adapter, or clean-room direct prototype

## 2026-08-27T00:05:00+08:00 | lead | phone-coexistence-triage
- action: inspect package/service state and Bluetooth Classic history without changing phone state
- command_or_ref: read-only ADB `dumpsys package`, `activity services`, and `bluetooth_manager`
- result_summary: Mi Fitness and MiWearCore remained active while the Band 9 Classic connection showed severe sub-second churn; the system dump cannot attribute every transition to a package, but a second ordinary app cannot be promised stable ownership of the single SPP session
- artifacts: [evidence/E-006-phone-coexistence.md]
- evidence_ids: [E-006]
- next: identify a non-competing downstream data path

## 2026-08-27T00:15:00+08:00 | cre | mi-fitness-static-triage
- action: pull and statically inspect Mi Fitness 3.58.0 health export surfaces
- command_or_ref: apktool plus targeted smali inspection
- result_summary: Mi Fitness has a robust Health Connect writer, but startup explicitly skips it for inland regions; its exported health ContentProvider and service are restricted to privileged/trusted callers
- artifacts: [evidence/E-007-mi-fitness-3.58-static.md]
- evidence_ids: [E-007]
- next: recommend official-owner downstream acquisition, with private cloud fallback where Health Connect is unavailable

## 2026-08-27T00:30:00+08:00 | doc | coexistence-report
- action: produce the coexistence stability addendum and correct the phase-one recommendation
- command_or_ref: docs-generator and diagram-generator workflows
- result_summary: direct SPP is rejected as a stable coexistence architecture; official-owner downstream acquisition is recommended, with Health Connect conditional on non-inland region and private cloud as the mainland fallback
- artifacts: [../../docs/2026-08-27_reverse-mi-fitness-stable-data-report.md, stable-data-architecture.mmd]
- evidence_ids: [E-003, E-004, E-006, E-007]
- next: user selects cloud protocol validation, Health Connect region validation, or an exclusive direct mode

## 2026-08-27T00:35:00+08:00 | doc | journal
- action: record the reusable SPP coexistence and exported-provider lessons without device identifiers
- command_or_ref: docs-generator Evidence → Finding → Path contract
- result_summary: anonymized field journal completed; no key, MAC, serial, account identifier, or raw health record retained
- artifacts: [field-journal.md]
- evidence_ids: [E-003, E-006, E-007]
- next: await the user's selected implementation phase

## 2026-08-27T01:20:00+08:00 | cre | cloud-protocol-validation
- action: audit pinned OSS against Mi Fitness 3.58.0 and build a standalone read-only PoC
- command_or_ref: static comparison of FitnessApiService, CloudInterceptor, VerifyToken, ri4/sxk/vh4, and pinned OSS; pure-stdlib unittest fixture
- result_summary: corrected app/v1 GET payloads and continuous RC4 behavior; isolated passToken from health-domain cookies; 18 synthetic offline tests passed
- artifacts: [evidence/E-008-mi-fitness-cloud-protocol.md, poc/mi_fitness_cloud_readonly]
- evidence_ids: [E-008]
- next: one authorized live steps GET, then separately review time-diff handling and bounded refresh

## 2026-08-27T01:45:00+08:00 | cre | provider-whitelist-audit
- action: determine whether CampusAI can enroll itself as a privileged Mi Fitness data-provider caller
- command_or_ref: targeted JADX and smali call-site searches for `addWhiteList`, `removeWhiteList`, and `isPrivilegedPackage`
- result_summary: the whitelist is an empty in-process package-to-signature map with no production writer or external enrollment surface; vendor cooperation is required
- artifacts: [evidence/E-009-mi-fitness-whitelist-gate.md]
- evidence_ids: [E-009]
- next: use the cloud route, which does not depend on local provider privileges

## 2026-08-27T15:15:49.7143915+08:00 | cre | cloud-live-read
- action: run one explicitly authorized mainland-China steps read through the standalone PoC
- command_or_ref: `readonly_poc.py --live --metric steps --days 1 --region cn --utc-offset +08:00`
- result_summary: the account exchange, dynamic STS ticket, encrypted GET, response decryption, and summary parsing all succeeded; the one-day response contained 11 records with no further page
- artifacts: [evidence/E-010-mi-fitness-cloud-live-read.md]
- evidence_ids: [E-010]
- next: add production-safe pagination, server-time correction, bounded refresh/backoff, and validated metric adapters before CampusAI scheduling

## 2026-08-27T16:42:08+08:00 | lead | campusai-readonly-integration
- action: implement and validate the CampusAI `CN + current-day steps + explicit manual refresh` integration
- command_or_ref: Android source review; `:apps:android:app:testDebugUnitTest`; `:apps:android:app:assembleDebug`
- result_summary: Keystore-backed credentials and aggregate cache, bounded read-only protocol, cancellable one-time Work, configured fail-closed gateway, fixed UI states, Band isolation, and Agent projected-tool enforcement completed; 201 unit tests and debug APK build passed
- artifacts: [evidence/E-011-campusai-mi-fitness-integration.md, ../../docs/2026-08-27_reverse-mi-fitness-cloud-report.md]
- evidence_ids: [E-011]
- next: install on the user's device only when requested, then compare the first manual steps result with Mi Fitness; do not enable background scheduling or additional metrics yet
