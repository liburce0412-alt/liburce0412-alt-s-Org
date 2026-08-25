# 本地 AI 测试结果

日期：2026-08-22。环境：Windows、JDK 21、Android NDK `28.2.13676358`、CMake `3.22.1`、MNN `3.6.1`。

## 自动化结果

- Android：`testDebugUnitTest` 37/37、连接真机仪器测试 4/4、`assembleDebug`、`lintDebug` 全部通过。覆盖精确统计、上下文相关性、问候事实保护、Room 4→5 迁移、稳定会话更新、删除与恢复。
- Native：`campusai_mnn` 已实际编译并链接；APK 中 9 个 arm64 共享库与 MNN 3.6.1 官方归档逐文件 SHA-256 一致。
- APK：`49,186,733` bytes（46.91 MiB），SHA-256 `23827C661562996700BACC388340205F5B4C7EF4F97ADC54D6473812B262408B`；`verify-local-ai-package.ps1` 通过，只包含版本化模型 manifest，不含 `.mnn`、`.mnn.weight`、tokenizer、`.part` 或模型目录。
- DeepSeek：服务端 Deno 基线通过；Android 个人 Key 路径固定 `api.deepseek.com`，FAST/DEEP 模型与 thinking 映射不变，响应映射为统一事件。未使用真实 Key 调用线上 provider。
- 本地逻辑：AUTO/DEEPSEEK/LOCAL 路由、个人 Key 缺失时零引擎调用、LOCAL 失败不回退、取消入口、状态 reducer、固定 manifest/URL、SHA 失败不可 Ready、上下文裁剪、精确统计和课程冲突测试通过。输出防护测试验证：显式最终段可流式返回、无标签的干净答案可在结束后返回、完整 think 块被剥离、只有思考过程时零输出、内部数据标记即使位于最终段内也会被阻止。
- 时间分析真机调校：Kotlin 先生成 `analysisStatements`、任务名称白名单与 `suggestedActionPlan`。最终真机回复准确保留今日 85/240、剩余 155、两个 50 分钟“编程”模块、10 分钟间隔、计划后剩余 55；未再杜撰数学/英语、效率或休息习惯。快捷任务 UI 只显示“今日总结”，内部控制提示不进入聊天气泡。
- 普通聊天真机回归：输入 `HiFresh` 后 Qwen 返回自然问候，没有附带 85/240 学习统计；流式输入框紧贴 IME，Composer Loader 只沿输入框周长运行。历史页能显示并恢复该会话。
- 首页副文案真机回归：无课程条件下生成“此刻的黄昏，让脚步慢下来吧”；先前含“还有课”和虚构具体钟点的候选被确定性规则拒绝，生成结果按日缓存。
- 质量集：30 条中文提示，`chat=8`、`study_summary=8`、`time_parse=7`、`schedule_cleanup=7`；结构和精确数字保护测试通过，真实模型回答质量待真机运行。
- Web 回归：生产构建通过；320/375/414/768/1280/1440 px、移动导航、数据页和登录错误路径共 9/9 通过；`npm audit --omit=dev` 为 0 漏洞。
- Supabase：11 个版本化 migration 通过 PostgreSQL AST 解析并已应用线上；Edge Function 未部署，Android 不调用平台额度。

## 验收矩阵

| 验收项 | 当前结果 |
|---|---|
| 新 APK 不含模型 | 通过，44.77 MiB；包内只含模型 manifest |
| 未下载/下载/暂停/校验/Ready/错误状态 | reducer 与构建测试通过；真机已完成 1.2 GiB 远端下载、逐文件校验与 Ready 原子切换 |
| 杀进程后恢复、Range 续传 | WorkManager + `.part` 实现完成；真机待测 |
| SHA-256 错误绝不 Ready | 单测通过；worker 失败路径会删除损坏文件 |
| 飞行模式聊天/总结 | Xiaomi 2410DPN6CC 真机通过；85/240 分钟被原样保留并返回分析与行动 |
| LOCAL 抓包无内容上传 | 静态依赖边界通过；动态抓包待真机 |
| 本地失败不静默调用 DeepSeek | 单测通过 |
| DeepSeek FAST/DEEP 回归 | 个人 Key 路由、流解析与固定映射通过；线上调用未执行 |
| AUTO/DEEPSEEK/LOCAL 路由 | 单测通过 |
| 流式、取消、后台、内存释放 | 取消代次、native 句柄生命周期与内存压力错误回传的 JVM/构建验证通过；系统压力注入待独立真机窗口 |
| 低存储/不兼容提示 | 代码与状态测试通过；设备场景待测 |
| 删除释放空间 | 下载取消→HTTP 取消→MNN 释放→目录删除顺序已实现；真机空间差值待测 |
| 模型不修改已计算数字 | Kotlin 的总时长、目标差距、分类占比、连续天数、峰值和趋势事实测试通过；真机 85/240 保留 |
| 思考过程/内部提示不进入聊天 | 单测与真实 Qwen 真机回复通过；不再展示 `Thinking Process` |
| 30 条中文质量集 | 数据集与结构测试通过；模型评分待真机 |
| 8 GB 真机性能与温度 | 约 15 GB RAM 真机完成基础运行和 PSS 采样；固定 100-token 与五分钟温度仍未完成 |

## 已执行命令

```powershell
.\gradlew.bat :apps:android:app:testDebugUnitTest :apps:android:app:assembleDebug :apps:android:app:lintDebug --console=plain
.\scripts\run-android-device-tests.ps1 # 仅隔离模拟器；默认拒绝个人真机
.\scripts\verify-local-ai-package.ps1
npx --yes deno check supabase/functions/ai-chat/index.ts supabase/functions/ai-chat/protocol_test.ts
npx --yes deno test supabase/functions/ai-chat/protocol_test.ts
py -3 -c "from pathlib import Path; from pglast import parse_sql; files=sorted(Path('supabase/migrations').glob('*.sql')); [parse_sql(p.read_text(encoding='utf-8')) for p in files]"
Set-Location apps/admin
npm run build
npm run test:visual
npm audit --omit=dev
```

## 未完成的真机门禁

Xiaomi 2410DPN6CC（Android 16、arm64-v8a、约 15 GB RAM）已连接，完成 1.2 GiB 远端下载、校验、Ready 原子切换、覆盖安装保留、飞行模式真实回复、模板关闭思考过程和运行后 PSS 采样。下载中断/续传、动态抓包、取消与切后台压力、删除空间差值、固定 100-token、五分钟温度以及至少一台 8 GB 设备仍是发布门禁，不能以这次基础验证替代。

> 真机安全规则：个人设备只允许使用 `adb install -r` 做覆盖安装。Gradle connected test 会执行测试安装生命周期，可能卸载目标应用并清空 `noBackupFilesDir`、Android Keystore、Room 历史和本机资料；必须改在隔离模拟器运行。仓库脚本 `run-android-device-tests.ps1` 默认拒绝物理设备，只有明确接受数据清空时才允许通过危险开关绕过。

### 测试事件记录

本轮曾误在个人真机运行 connected test，Gradle 测试生命周期卸载了目标包并清空应用私有数据。随后只使用覆盖安装恢复 APK，并重新完成模型下载与 Ready 校验；本机 Key、Room 历史和未同步个人资料无法从模型目录或 Supabase 自动恢复。该事件促成了上述物理设备硬阻断脚本，后续不再把 connected test 作为个人真机回归命令。
