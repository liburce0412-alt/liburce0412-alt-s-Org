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

- LOCAL：消息和裁剪后的学习/课程 JSON 从 Compose 进入 `LocalPromptPolicy`，随后只进入 JNI/MNN。`LocalMnnAiEngine` 没有 OkHttp、Supabase 或其他网络依赖。MNN token 先通过 `LocalOutputGuard`；显式最终段可以流式通过，无标签输出在完成后验证为干净答案再发出，思考过程、系统提示和内部 JSON 标记被丢弃或整段阻止。
- DEEPSEEK：消息和裁剪后的结构化 JSON 经 TLS 直接发往固定域名 `api.deepseek.com`。个人 Key 使用 Android Keystore AES-256-GCM 加密，密文 SharedPreferences 被排除在备份与设备迁移之外；Key 不发往 Supabase。
- AUTO：设置页明确说明在线会使用用户自己的 Key。LOCAL 失败后的云端调用必须点击“确认：本次使用我的 DeepSeek Key”。
- Android 不提供 CampusAI 平台额度，也不会在个人 Key 缺失、无效、余额不足或请求失败时静默调用 Supabase Edge Function。
- 日志：不会记录提示词、回复、课程内容或时间明细。性能记录只含设备、CPU/线程数、加载/首 token/decode/内存/温度等数值。
- 模型：下载目录中的内容永不作为代码加载；native runtime 固定随 APK 发布。下载器不接受任意 URL。

## 个性化上下文与历史

- `AiContextAssembler` 始终只提供显示名称、日期、时区和语言；普通聊天再根据问题关键词决定是否加入时间、课程或动态。对“你好”“Hello”等无关问题不附带学习汇总。
- 总时长、分类分钟、目标差距、连续天数、趋势和课程冲突均由 Kotlin/Room 计算。模型负责表达，不重新计算原始记录。
- 时间、课程和用户自己的动态默认允许但可在“依据”面板按会话关闭；公共帖子只有用户明确开启才进入。私聊、订单、账号安全资料和他人私有内容永不进入上下文。
- LOCAL 选择的数据只进入本机 MNN；DeepSeek 会在依据面板明确提示所选裁剪数据将发送至 `api.deepseek.com`。
- 对话历史仅保存在本机 Room。一个会话使用稳定 ID，继续对话更新同一行；删除支持撤销。历史不上传 Supabase，覆盖安装保留，卸载/清除数据才删除。
- 首页副文案由当前 AI 每个自然日随机生成一次并本机缓存。确定性规则会拦截无课程时的课程文案，以及虚构的具体钟点、天气、校园状态和不存在的学习完成事实；失败时只显示本机候选，不上传额外信息。

## 真机边界

Xiaomi 2410DPN6CC 已在系统飞行模式启用时完成本地结构化学习总结，证明该路径不依赖网络可用性；测试后已恢复网络。飞行模式不是抓包证据，发布前仍需在模型 Ready 的 arm64 真机上以代理或系统网络日志确认推理期间没有提示词、回复或统计上传。
