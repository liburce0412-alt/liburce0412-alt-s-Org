# 本地模型 manifest 与更新流程

唯一清单位于 `apps/android/app/src/main/assets/local_models/qwen3_5_2b_mnn.json`。

## 更新门禁

1. 只接受官方 MNN/taobao-mnn 仓库的不可变 40 位 revision；禁止 `main`、`latest` 或用户输入 URL。
2. 从 Hugging Face model API 获取文件名、size 与 LFS oid；对非 LFS 文件下载后计算 SHA-256。完整下载一次并重新计算所有文件 SHA-256 后才可发布。
3. `files[].size` 之和必须等于 `totalBytes`。文件名只能含字母、数字、点、下划线和连字符，不能含目录穿越。
4. 如需升级 MNN，同时锁定 release、commit、官方 Android archive SHA-256、NDK 和 CMake；在 arm64 真机重新验证冷加载、取消、内存压力和五分钟稳定性。
5. 运行 Android 单元测试、native build、APK 无模型检查与真机下载/校验。旧 active 模型不得在新版本验证前自动删除。
6. 不得把 `.mnn`、权重、tokenizer、`.part`、API key 或 service role key 提交到 Git。

当前 MNN Android archive SHA-256：`46dc7e86d45b8d4e957db81d2603e0b7f6c9ce9b84092ffdcee1b843cbfc9d71`。
