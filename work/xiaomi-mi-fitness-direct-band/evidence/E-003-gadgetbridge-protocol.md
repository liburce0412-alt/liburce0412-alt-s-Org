# E-003 — Gadgetbridge 的 Band 9 直连协议证据

- source_ref: `https://github.com/Freeyourgadget/Gadgetbridge.git`
- pinned_commit: `a0948ee1cbc2a870f91d313f8e37df5f524465f7`
- local_ref: `evidence/gadgetbridge-github`（浅克隆、无工作树）
- repro_command:

```powershell
$repo = 'work/xiaomi-mi-fitness-direct-band/evidence/gadgetbridge-github'
git -C $repo show HEAD:app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/xiaomi/miband9/MiBand9Coordinator.java
git -C $repo show HEAD:app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/xiaomi/XiaomiSppSupport.java
git -C $repo show HEAD:app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/xiaomi/XiaomiAuthService.java
git -C $repo show HEAD:app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/xiaomi/services/XiaomiHealthService.java
```

- content_hash:
  - `MiBand9Coordinator.java`: `git-blob:e096baf91d7f356e7f6b6a24bc1b148d98e7f5d7`
  - `XiaomiSppSupport.java`: `git-blob:66c225cea4aea46e5e2e441b7b8ab45044002dc5`
  - `XiaomiAuthService.java`: `git-blob:2f2a14bd6a714adb20141e2d63c90b824b449443`
  - `XiaomiHealthService.java`: `git-blob:189da3ce476c0ea843004bc98ccd51bff3f19edf`
  - `XiaomiActivityFileFetcher.java`: `git-blob:688391c02ab3b885e2a52e9a340bbbdf7cfdbe3c`
  - `XiaomiActivityParser.java`: `git-blob:801cd2783004b0da583521b33fa5e370c639eb67`
- observed:
  - Band 9 coordinator 指定 `ConnectionType.BT_CLASSIC`。
  - 传输层使用 Bluetooth RFCOMM Serial Port Profile，并按 Version、ProtobufCommand、Activity 等通道分帧。
  - 会话初始化后进入 Xiaomi 应用层认证；认证使用 16-byte 设备密钥、双方 nonce、HMAC-SHA256 派生和 AES-CCM。
  - 健康服务包含实时统计 start/stop/event（45/46/47）；事件至少携带心率和累计步数。
  - 活动文件 fetcher 与 parser 覆盖 daily、sleep、manual sample、workout/GPS 等历史数据族，并处理分块、校验与确认。
  - 仓库许可证为 AGPL-3.0-or-later。
