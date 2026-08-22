# CampusAI Android 本地 AI 架构

状态：代码实现完成；arm64 MNN 原生桥已编译和链接。模型下载、真机推理与性能仍需在 arm64 8 GB 真机验收。

## 边界

- 云端：`DeepSeekAiEngine → AiEdgeClient → Supabase ai-chat Edge Function → DeepSeek`。保留 `meta/status/delta/done/error` SSE。
- 本地：`LocalMnnAiEngine → MnnNativeBridge → MNN 3.6.1 CPU → Qwen3.5-2B MNN 4-bit`。不创建任何网络客户端。
- 统一入口：`AiEngineRouter` 按 `AiProvider`、FAST/DEEP、网络和 `LocalModelState` 决定引擎。聊天 UI 只消费 `AiEvent`。
- 生命周期：同一时刻只允许一个本地生成；Kotlin `Mutex` 与 native mutex 双重保护。取消按 token 生效；严重内存压力、删除模型或空闲 10 分钟后释放实例。

## 模型和运行时

- 模型：`taobao-mnn/Qwen3.5-2B-MNN`，revision `f3307fcae4c41b63c9a924e0a3de17fd7ad09ae4`，完整大小 `1,386,688,857` bytes。
- MNN：`3.6.1`，commit `d407447ed56c4121a11ccbd266dc184ca1ead0c2`。
- APK 只包含官方 arm64 runtime 与 CampusAI JNI bridge，不含 `.mnn`、权重或 tokenizer。
- 基线：CPU、4 线程、low precision/memory、4096 context、512 max output、thinking off。未启用 GPU/Vulkan/QNN 推理。

## 下载与 Ready 事务

1. WorkManager 使用固定 manifest 生成 Hugging Face revision URL；调用方不能传入 URL。
2. 每个文件写入应用私有 `noBackupFilesDir/models/.<id>-<version>.staging/<file>.part`。
3. 服务端支持 Range 时从现有长度续传；返回完整内容时安全重置该 `.part`。
4. 每个文件完成后核对固定 size 与 SHA-256，再在 staging 中改名。
5. 全部文件再次校验，写入 `.ready.json`，最后同文件系统目录改名到 active 目录。此动作之前永远不报告 Ready。
6. 校验失败删除损坏临时文件；网络失败由 WorkManager 重试；暂停保留 `.part`；进程重启后 WorkManager 和 staging 文件共同恢复进度。
7. 删除或重新下载严格按“取消 WorkManager 与 HTTP Call → 等待 MNN mutex 释放实例 → 删除目录”执行，避免仍有文件句柄时回收模型。

## CampusAI 任务接入

`CampusAiTaskFactory` 提供聊天、首页洞察、今日/周/月总结、结构化建议、课程整理和自然语言时间记录解析。总时长、分类分钟、目标率基点、逐日趋势、课程冲突与可确定的时长由 Kotlin 计算。模型只负责中文表达；结构化 JSON 被裁剪，本地 context 总字符数和消息数均有上限。

管理员审核、交易风控、账号安全、权限判断和高可靠深度推理没有交给本地模型。
