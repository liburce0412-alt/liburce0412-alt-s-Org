# E-001 — 当前 CampusAI 健康数据链

- source_ref:
  - `README.md`
  - `apps/android/bandbridge/README.md`
  - `apps/android/app/src/main/java/com/campusai/core/health/BandLiveProviderGateway.kt`
  - `apps/android/app/src/main/java/com/campusai/core/health/HealthSyncCoordinator.kt`
  - `apps/android/app/src/main/java/com/campusai/core/health/HealthContracts.kt`
- repro_command:

```powershell
rg -n "Gadgetbridge|Health Connect|CaesarBandBridge|BandLiveProviderGateway|HealthSyncCoordinator" README.md apps/android
```

- content_hash:
  - `apps/android/bandbridge/README.md`: `sha256:d482142ef1a549d3bdab279aaa17e81f5ae2382f36f8bf184c004816c0099e42`
- observed:
  - 当前历史链是 `Band 9 → Gadgetbridge → Health Connect → HealthGateway`。
  - 实时链只通过 `content://com.campusai.caesar.bandbridge.live/snapshot` 读取独立 CaesarBandBridge。
  - 主应用已把实时能力抽象为 `BandLiveGateway`，因此可用同进程 `DirectBand9Gateway` 替换，不必改 Agent 工具契约。
  - `HealthSyncCoordinator` 仍硬编码“触发 bridge → 等待 Gadgetbridge → 等待 Health Connect”，直连后需要改为“连接 → 拉历史 → 写本地仓库/可选写 Health Connect”。
