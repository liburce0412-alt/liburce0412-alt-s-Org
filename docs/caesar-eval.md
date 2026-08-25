# Caesar Eval V1

Caesar Eval 是一个 **debug-only、显式 opt-in** 的真模型回放入口，用于在同一台手机上按固定顺序对比 Qwen3.5-2B Fast 和 Qwen3.5-4B Deep。

它不是 Android instrumentation test，不会运行 `connectedDebugAndroidTest`，也不进入安装、卸载或清除数据流程。

## 安全边界

- Eval Activity 只存在于 debug APK，并要求 `android.permission.DUMP`，普通 App 无法启动。
- 即使由 ADB shell 启动，也必须同时提供 `explicit_opt_in=true`、允许的模型 ID 和合法 run ID。
- 固定数据集为 50 条合成样本：保留原 30 条中文质量样本，再加入 UTF-8、多轮追问、图片、App 工具选择、健康上下文、健康工具与提示注入回归。
- 工具样本会向模型提供固定 schema，但 Eval 只记录名称和参数并评分，绝不进入 ToolRegistry 或执行业务副作用。
- 不读取 Room 业务库、Agent 记忆、Health Connect 真实数据或用户会话。图片只能使用 APK 内白名单 fixture；健康摘要也是数据集内的合成 JSON。
- 两个模型必须事先都处于 Ready。Eval 只检查本地文件，不会启动、恢复或触发下载。

## 运行

前提：手机已通过 ADB 连接，目标 debug APK 已由开发者另行安全安装，2B 和 4B 都显示 Ready。

```powershell
pwsh.exe -File .\scripts\run-caesar-eval.ps1 -IUnderstandThisRunsLocalModels
```

有多台设备时指定：

```powershell
pwsh.exe -File .\scripts\run-caesar-eval.ps1 `
  -IUnderstandThisRunsLocalModels `
  -DeviceSerial 3cc5349b
```

脚本会在每个模型开始前执行 `am force-stop`，防止 Caesar 主进程与 Eval 进程同时各加载一套权重。该操作不清数据，但会暂停当前 App 会话；完成后手动重新打开 Caesar。

脚本只通过 `run-as` 拉取 App 私有目录中的报告，默认保存到 `artifacts/caesar-eval/`：

- `*-qwen3.5-2b-mnn.json`：2B 逐样本报告。
- `*-qwen3.5-4b-mnn.json`：4B 逐样本报告。
- `*-comparison.json`：两份报告的顺序校验和汇总索引。

## 报告字段

每条样本记录：

- `firstTokenMs`：本轮可用 TTFT；native 值大于 0 时使用 native，否则退化为首个可见 token 时间，绝不把 native `0` 当成真实 TTFT。
- `nativeFirstTokenMs`：MNN 原始首 token 指标，保留 `0` 用于诊断 native 计时未上报的问题。
- `visibleFirstTokenMs`：经本地输出守卫后，UI 真正可见的首段时间。
- `engineElapsedMs` / `wallElapsedMs`：引擎生成时间和 Eval 墙钟总时间。
- `tokensPerSecond` / `outputTokens` / `loadMs`：MNN native decode 吞吐、输出 token 数和本轮加载时间。
- `error` / `unexpectedToolCalls`：错误与任何越界工具请求。
- `staticScore`：非空、完成、工具选择/参数精确匹配、`mustPreserve` 事实保留、`mustNotContain` 回归禁止项与严格 pass/fail。

`summary.visibleFirstTokenP50Ms` 是报告和 comparison 默认展示的 TTFT P50；`nativeFirstTokenP50Ms` 只作为诊断指标保留。`mustPreserve` 使用严格字面保真，不做同义词或缩写归一化；`mustNotContain` 用来锁定 `�`、传输标签、内部过程和伪造缺失数据等已知回归。

第一条样本包含冷加载，其余样本是同一模型引擎的热态回放。两个模型的数据集和顺序完全一致。

`staticScore` 是可复现的最小静态门禁，它不等价于人类语义评测，也不能单独决定 2B 是否进入正式包。

## V1 模型决策

2026-08-24 的首轮真机回放后，产品决策已固定：

- Qwen3.5-2B 保留为用户主动选择的 `FAST · 2B`，不再以冷机能耗对比作为删除门槛。
- Qwen3.5-4B 作为默认 `DEEP · 4B`，承担更高事实保真和复杂任务。
- 两个模型均保持独立下载、Ready 状态和会话锁模；选择只影响新会话，不做逐消息自动切换。
- 后续 Eval 用于防止质量回归和确定任务路由，不再用于决定是否保留 2B。
