# AI 路由与隐私数据流

## 路由表

| 选择 | 条件 | 结果 |
|---|---|---|
| AUTO | 在线且个人 Key 已保存，FAST/DEEP | 使用用户自己的 DeepSeek Key；DEEP 不降级 |
| AUTO | 在线但个人 Key 未保存 | 阻止请求并引导保存；不调用平台额度 |
| AUTO | 离线，FAST，本地 Ready | Qwen3.5-2B 本地 |
| AUTO | 离线，FAST，本地未下载 | 明确不可用并引导下载 |
| AUTO | 离线，DEEP | 提示切换本地快速模式，不伪装深度推理 |
| DEEPSEEK | 在线且个人 Key已保存 | 固定使用用户自己的 DeepSeek Key |
| DEEPSEEK | 在线但个人 Key 未保存 | 阻止请求；不调用平台额度 |
| DEEPSEEK | 离线 | 报错，不自动切本地 |
| LOCAL | FAST 且 Ready | 固定本地，不上传内容 |
| LOCAL | DEEP | 拒绝并提示改为 FAST |
| LOCAL | 未 Ready | 引导下载；在线时只显示“本次改用云端”显式确认 |
| LOCAL | 推理失败 | 保持失败，不静默调用 DeepSeek |

## 数据流

- LOCAL：消息和裁剪后的学习/课程 JSON 从 Compose 进入 `LocalPromptPolicy`，随后只进入 JNI/MNN。`LocalMnnAiEngine` 没有 OkHttp、Supabase 或其他网络依赖。
- DEEPSEEK：消息和裁剪后的结构化 JSON 经 TLS 直接发往固定域名 `api.deepseek.com`。个人 Key 使用 Android Keystore AES-256-GCM 加密，密文 SharedPreferences 被排除在备份与设备迁移之外；Key 不发往 Supabase。
- AUTO：设置页明确说明在线会使用用户自己的 Key。LOCAL 失败后的云端调用必须点击“确认：本次使用我的 DeepSeek Key”。
- Android 不提供 CampusAI 平台额度，也不会在个人 Key 缺失、无效、余额不足或请求失败时静默调用 Supabase Edge Function。
- 日志：不会记录提示词、回复、课程内容或时间明细。性能记录只含设备、CPU/线程数、加载/首 token/decode/内存/温度等数值。
- 模型：下载目录中的内容永不作为代码加载；native runtime 固定随 APK 发布。下载器不接受任意 URL。

## 待真机验证

LOCAL 网络零上传需要在模型 Ready 的 arm64 真机上以代理/抓包重放测试；当前架构和依赖扫描已保证本地引擎无网络调用，但在没有真机和模型的环境中不能把它冒充为抓包证据。
