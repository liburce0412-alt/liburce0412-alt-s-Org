# Case Scope

## meta
- case_id: xiaomi-mi-fitness-direct-band
- created: 2026-08-26T23:00:15.9210790+08:00
- operator: local
- primary_skill: apk-reverse/SKILL.md
- primary_id: R1
- lead_role: lead
- specialist_roles: [cre, doc]
- hint: Reverse Xiaomi Mi Fitness APK and direct Xiaomi Smart Band BLE protocol for local campusai integration

## auth
- status: granted
- basis: own_system
- evidence_of_auth: user-requested analysis of own app account and wearable
- MUST NOT proceed if status != granted

## in_scope
- assets:
  - Xiaomi Mi Fitness Android APK (`com.mi.health` 3.58.0 / versionCode 358000)
  - User-owned Xiaomi Smart Band 9 (firmware variant still pending)
  - User-owned HyperOS phone with Mi Fitness and Mi Connect left running
  - Local campusai repository
- surfaces: [android_apk, bluetooth_classic_spp, bluetooth_le_discovery, local_app_integration, private_cloud_api_research]
- activities: [triage, static_reverse, protocol_recovery, interoperability_research, report]

## out_of_scope
- assets: [third_party_accounts, wearables_not_owned_by_user, production_services_outside_documented_read_only_use]
- activities: [dos, phishing_real_users, unrestricted_exfil]

## network_profile
- mode: authorized_target_only
- notes: |
    offline | lab_only | authorized_target_only | unrestricted_lab
    Change mode only after auth.status = granted.

## deliverables
- report: true
- field_journal: true
- diagrams: true
- timeline: true

## constraints
- timebox: {}
- stealth: low
- data_handling: anonymize

## signoff
- ready_for_act: true
- checklist:
  - [x] auth.status = granted
  - [x] in_scope.assets non-empty OR offline sample path set
  - [x] network_profile.mode chosen
  - [x] out_of_scope reviewed
  - [x] roles assigned (see skills/ops/role-map.md)

## ops_refs
- skills/ops/scope-contract.md
- skills/ops/evidence-finding-path.md
- skills/ops/role-map.md
- skills/ops/timeline-workitem.md
- skills/ops/IDENTITY.md
