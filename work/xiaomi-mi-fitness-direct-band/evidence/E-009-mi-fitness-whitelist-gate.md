# E-009

- title: Mi Fitness 3.58.0 DataProvider whitelist is not self-service
- observed_at: 2026-08-27T01:45:00+08:00
- source_type: file
- source_ref: `evidence/mi-fitness-3.58.0-static/jadx/sources/com/xiaomi/fitness/dataprovider/`
- content_hash: `sha256:8df26f594b4d2c7f994e0582edafc7ae9dc4066794f78d15f6f80893197ac4c5`
- repro_command: |
    ```powershell
    $jadx = 'work/xiaomi-mi-fitness-direct-band/evidence/mi-fitness-3.58.0-static/jadx/sources'
    rg -n 'addWhiteList|removeWhiteList|isPrivilegedPackage|mWhiteList' -- `
      "$jadx/com/xiaomi/fitness/dataprovider/DataProviderManager.java"
    rg -n --glob '*.java' '\.(addWhiteList|removeWhiteList)\(' -- $jadx

    $smali = 'work/xiaomi-mi-fitness-direct-band/evidence/mi-fitness-3.58.0-static/apktool-decoded'
    rg -n --glob '*.smali' `
      'Lcom/xiaomi/fitness/dataprovider/DataProviderManager;->(addWhiteList|removeWhiteList)' `
      -- $smali
    # rg exit code 1 / no output is the expected result for production call sites.
    ```
- raw_excerpt: |
    - `DataProviderManager.addWhiteList(pkg, sign)` only performs an in-memory
      `mWhiteList.put(pkg, sign)` (`DataProviderManager.java:103-110`).
    - `isPrivilegedPackage` accepts a system/updated-system app, an embedded trusted signing
      certificate, an exact package plus matching certificate pair in that in-memory map, or a
      caller when the Mi Fitness host itself is debuggable (`:135-170`).
    - The constructor creates an empty `ConcurrentHashMap`; full Java and smali searches find no
      production caller of `addWhiteList` or `removeWhiteList`.
    - `DataContentProvider.call()` and `query()` run the same privilege check before dispatch.
      Empty normal permissions fail with `SecurityException`; there is no provider method that
      enrolls a caller (`DataContentProvider.java:59-90,139-163,206-226`).
    - CampusAI's current application ID is `com.aistudio.campusai.ywtpzx`. Vendor-supported
      enrollment would need Xiaomi to ship that exact package name together with the release
      signing-certificate fingerprint in Mi Fitness (or another Xiaomi-controlled privileged
      component). CampusAI cannot add itself from its own process.
- linked_workitem: WI-010
- supersedes: none

## Conclusion

Adding CampusAI to the whitelist is technically possible only through Xiaomi/vendor cooperation
or a privileged/system distribution arrangement. Patching/re-signing Mi Fitness, root/LSPosed
injection, or installing CampusAI as a system app could alter the result, but those approaches
change the official stack, are fragile across updates, and violate the current coexistence
constraint. The read-only cloud route does not require this whitelist.

## Fixed artifact hashes

| Artifact | SHA-256 |
|---|---|
| `DataProviderManager.java` | `8df26f594b4d2c7f994e0582edafc7ae9dc4066794f78d15f6f80893197ac4c5` |
| `DataContentProvider.smali` | `8de9d670eeb605ba1e4e336c8b2f30e3b874b6a9de369c503558104b7b33dd1e` |
| `y86.smali` provider registry | `85de9c6e49c8c9a04be0f12759d61c1f319287a6cd2197f7ebedf20f3a405e32` |
