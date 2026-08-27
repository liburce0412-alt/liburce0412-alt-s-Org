# E-004 — 私有云历史接口与配对密钥工具

## Mi Fitness MCP

- source_ref: `https://github.com/kubulashvili/mi-fitness-mcp.git`
- pinned_commit: `07b61900fcd0ae364cb5c668256cf0d0b2884c46`
- content_hash:
  - `mi_fitness_cloud.py`: `git-blob:4b72e140c9ded41afb92c84caad555bf47d80317`
  - `xiaomi_crypto.py`: `git-blob:0991debf75f4339713b9fc96d6e4a6dde3ab3f70`
- observed:
  - 使用 Xiaomi `sid=miothealth` 登录和区域 `*.hlth.io.mi.com` 后端。
  - 已实现私有端点 `/app/v1/data/get_fitness_data_by_time` 与 `/app/v1/data/get_sport_records_by_time`。
  - 已覆盖步数、热量、心率、体重和训练；源码明确将睡眠留为“响应结构尚未验证”。
  - 这是“无本地桥接 App”的云端历史路线，不是手环直连；仍依赖 Mi Fitness 先把手环数据上传。

## Huami Token

- source_ref: `https://github.com/argrento/huami-token.git`
- pinned_commit: `1b32658519d1f35cd3c4345bb9ced3ba6881bb56`
- content_hash:
  - `README.md`: `git-blob:d596c2e8d81a74c342a300e841bdada3d67df061`
  - `LICENSE.md`: `git-blob:e9364122207cad9a38e830a84277a85d6f95963c`
- observed:
  - MIT 工具可从 Xiaomi Mi Fitness 账号获取已配对设备的 Bluetooth auth key。
  - 它只解决密钥取得，不读取健康数据。
  - 账号密码、passToken 与 auth key 都不得写入命令历史、日志或 Git。

## Band 9 实机恢复研究

- source_ref: `https://github.com/glasses666/miband9-imu-recovery.git`
- pinned_commit: `283e5c9860ff083334bd51d9831581ba53246c1e`
- content_hash:
  - `GADGETBRIDGE_XIAOMI_BIND_FLOW.md`: `git-blob:c4ad402711e8794d61dfe459668e4ec6a0f382a0`
  - `PUBLIC_RESEARCH_CHAIN_20260530.md`: `git-blob:bbdd4ae7c92c0b5c5f524e9799a0b899b9ffbe3d`
- observed:
  - Android `BOND_BONDED` 不等于 Xiaomi 应用层已认证，必须进入协议的 initialized 状态才能同步。
  - 研究记录将 Mi Fitness `device_db` 中的 token/encrypt_key 与 Gadgetbridge auth key 对上，并验证“导入状态后由自有进程直连”的可行性。
