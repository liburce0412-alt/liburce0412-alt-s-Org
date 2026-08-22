# 本地 AI 测试结果

日期：2026-08-22。环境：Windows、JDK 21、Android NDK `28.2.13676358`、CMake `3.22.1`、MNN `3.6.1`。

## 自动化结果

- Android：`testDebugUnitTest`、`assembleDebug`、`lintDebug` 全部通过；11 个 suite、17 项测试、0 failure、0 error、0 skipped。
- Native：`campusai_mnn` 已实际编译并链接；APK 中 9 个 arm64 共享库与 MNN 3.6.1 官方归档逐文件 SHA-256 一致。
- APK：`47,342,109` bytes；包含 arm64 runtime，不含 `.mnn`、`.mnn.weight`、tokenizer、`.part` 或模型目录；debug APK v2 签名有效。
- DeepSeek：Deno check 通过；FAST/DEEP 固定模型与 thinking 映射、`meta/status/delta/done/error` SSE 兼容测试 2/2 通过。未调用线上 provider。
- 本地逻辑：AUTO/DEEPSEEK/LOCAL 路由、LOCAL 失败不回退、取消入口、状态 reducer、固定 manifest/URL、SHA 失败不可 Ready、上下文裁剪、精确统计和课程冲突测试通过。
- 质量集：30 条中文提示，`chat=8`、`study_summary=8`、`time_parse=7`、`schedule_cleanup=7`；结构和精确数字保护测试通过，真实模型回答质量待真机运行。
- Web 回归：生产构建通过；320/375/414/768/1280/1440 px、移动导航、数据页和登录错误路径共 9/9 通过；`npm audit --omit=dev` 为 0 漏洞。
- Supabase：10 个版本化 migration 通过 PostgreSQL AST 解析；Edge Function 未部署，线上数据未改动。

## 验收矩阵

| 验收项 | 当前结果 |
|---|---|
| 新 APK 不含模型 | 通过，47.34 MB |
| 未下载/下载/暂停/校验/Ready/错误状态 | reducer 与构建测试通过；完整远端下载待真机 |
| 杀进程后恢复、Range 续传 | WorkManager + `.part` 实现完成；真机待测 |
| SHA-256 错误绝不 Ready | 单测通过；worker 失败路径会删除损坏文件 |
| 飞行模式聊天/总结 | 路由与本地边界通过；真实 MNN 推理待真机 |
| LOCAL 抓包无内容上传 | 静态依赖边界通过；动态抓包待真机 |
| 本地失败不静默调用 DeepSeek | 单测通过 |
| DeepSeek FAST/DEEP 回归 | 协议与映射通过；线上调用未执行 |
| AUTO/DEEPSEEK/LOCAL 路由 | 单测通过 |
| 流式、取消、后台、内存释放 | 代码路径与构建通过；压力测试待真机 |
| 低存储/不兼容提示 | 代码与状态测试通过；设备场景待测 |
| 删除释放空间 | 下载取消→HTTP 取消→MNN 释放→目录删除顺序已实现；真机空间差值待测 |
| 模型不修改已计算数字 | Kotlin 事实与 prompt policy 单测通过 |
| 30 条中文质量集 | 数据集与结构测试通过；模型评分待真机 |
| 8 GB 真机性能与温度 | 未完成，无设备连接 |

## 已执行命令

```powershell
.\gradlew.bat :apps:android:app:testDebugUnitTest :apps:android:app:assembleDebug :apps:android:app:lintDebug --console=plain
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

`adb devices -l` 返回空设备列表。本机未下载 1.386 GB 模型，因此没有伪造完整下载、飞行模式、动态抓包、真实回复质量、切后台/内存压力、删除空间差值或性能温度结果。发布前必须在至少一台 8 GB arm64 Android 真机完成这些项目，指标表见 `local-ai-performance.md`。
