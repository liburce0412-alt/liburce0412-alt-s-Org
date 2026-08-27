# E-002 — HyperOS MiWearCore Binder 的证书门禁

- source_ref:
  - `artifacts/mi-connect-audit/mi-connect-decoded/apktool/AndroidManifest.xml`
  - `artifacts/mi-connect-audit/mi-connect-decoded/jadx/sources/com/xiaomi/wearable/core/MiWearCoreService.java`
  - `artifacts/mi-connect-audit/mi-connect-decoded/jadx/sources/com/xiaomi/wearable/core/server/PermissionChecker.java`
- repro_command:

```powershell
rg -n "MiWearCoreService|checkCert|SecurityException|com.mi.health|com.xiaomi.wearable" artifacts/mi-connect-audit/mi-connect-decoded
```

- content_hash:
  - `MiWearCoreService.java`: `sha256:63f24c2dbcc7b9e88293349ba9704eb89a4b3b5c0d3213fcabf61dbbf1159e3b`
  - `PermissionChecker.java`: `sha256:72c2fb2aef9d78c896e3ac0ed5658cd99902f4214dc1ce14bb9659d7945785ad`
- observed:
  - `MiWearCoreService` 虽然 `exported=true`，但 `getMiWearCoreBinder()` 调用 `checkCert()`。
  - 白名单只有小米签名的 `com.mi.health` 与 `com.xiaomi.wearable`；失败时抛出 `SecurityException`。
  - 因此 CampusAI 不能把该系统 Binder 当作第三方公开接口。
