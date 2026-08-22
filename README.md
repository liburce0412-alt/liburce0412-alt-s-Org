# CampusAI

CampusAI 是面向校园场景的 Android 主产品与响应式 Web 管理台。当前仓库以 SPECTRA 光学材质为统一设计语言，后端以 Supabase 为唯一业务数据入口。Android 可选下载 Qwen3.5-2B MNN 4-bit 本地模型；DeepSeek FAST / DEEP 继续只经 Edge Function，密钥不进入客户端。

## 仓库结构

- `apps/android`：Jetpack Compose Android 应用，保留应用 ID `com.aistudio.campusai.ywtpzx`。
- `apps/admin`：React、TypeScript、Vite、TanStack Router/Query 管理台。
- `supabase`：版本化 migration、RLS、RPC、Storage 策略与 `ai-chat` Edge Function。
- `design`：已批准规范、设计令牌、视觉基准与回归证据。
- `.github/workflows`：Android/Web/Supabase 检查、签名发布和部署。

设计决策以 [`design/approved-spec.md`](design/approved-spec.md) 为准。颜色、材质、圆角、布局、字体、图标、动效或文案语言变更必须先确认。

## 最快开始

要求：JDK 21、Android SDK、NDK `28.2.13676358`、CMake `3.22.1`、Node.js 24。Supabase 本地 migration 回放还需要 Docker。

```powershell
# Android 单元测试与调试 APK
.\gradlew.bat :apps:android:app:testDebugUnitTest :apps:android:app:assembleDebug

# Web 管理台
Set-Location apps/admin
npm ci
npm run build
npm run test:visual
```

Web 本地环境放在 `apps/admin/.env.local`：

```dotenv
VITE_SUPABASE_URL=https://mcpjecboqddqelgikvvc.supabase.co
VITE_SUPABASE_ANON_KEY=你的公开 publishable key（sb_publishable_...）
```

不要把 `service_role`、DeepSeek 密钥或数据库密码写入 Android/Web 环境文件。

## 导入课程表

在 Android 打开“时间”，点击右上角文件图标：

1. 首选“选择课程表截图”。截取包含星期标题和完整时间栏的清晰图片。
2. 应用在设备本地识别中文课程名、星期和时间，不会直接写入。
3. 在预览里改正课程名或教室，删除误识别项，再点“确认导入”。
4. 重复课程会按稳定指纹自动跳过，现有课程不会被覆盖。

如果教务系统可以导出日历，可选择 `.ics` 文件；识别仍不理想时使用“手动添加课程”。导入完成后，当天课程会显示在时间页。

## Supabase 与 AI

- 目标项目：`mcpjecboqddqelgikvvc`。
- migration 是数据库结构的唯一真相。
- `fast` / `deep` 只调用 `supabase/functions/ai-chat`；DeepSeek 密钥存 Supabase Secrets。
- Android 的 AI 运行方式可选自动、DeepSeek 云端、本地离线。本地模型首次安装不下载、不进入 APK，只接受版本化固定清单并存入 `noBackupFilesDir/models`。
- 本地模型下载支持 Wi-Fi 约束、明确移动网络确认、前台 WorkManager、断点续传、进程重启恢复、逐文件 SHA-256、原子 Ready、暂停、删除与重新下载。
- AI 架构、路由、隐私、模型更新、用户操作和性能记录见 [`docs/local-ai-architecture.md`](docs/local-ai-architecture.md)、[`docs/ai-routing-privacy.md`](docs/ai-routing-privacy.md)、[`docs/local-ai-model-update.md`](docs/local-ai-model-update.md)、[`docs/local-ai-user-guide.md`](docs/local-ai-user-guide.md) 与 [`docs/local-ai-performance.md`](docs/local-ai-performance.md)。
- Android 的时间记录与课程表采用 Room 本地优先；登录后由 WorkManager 同步当前账号与尚未认领的本地数据，退出后不会显示其他账号的私人记录。
- 校园帖子、评论、举报、商品图片、会话、消息、购买和订单状态已接 canonical REST/RPC；管理台在真实配置下可执行审核、封禁、举报处理、公告和版本发布。
- 官方 Supabase MCP 已连接并完成线上只读结构核对；旧 public 表、2 个 Auth 用户和 1 个 Storage 对象已纳入 reconciliation 设计。
- 线上 migration 尚未自动应用：必须先取得可恢复备份 ID，并在隔离环境完整回放。

线上接管步骤与硬性门禁见 [`docs/live-cutover.md`](docs/live-cutover.md)。
源码安全审计、已修复问题和残余发布风险见 [`docs/security-audit.md`](docs/security-audit.md)。

## 发布所需 Secrets

- Android：`ANDROID_KEYSTORE_BASE64`、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
- Cloudflare Pages：`CLOUDFLARE_API_TOKEN`、`CLOUDFLARE_ACCOUNT_ID`、`SUPABASE_ANON_KEY`。
- Supabase：`SUPABASE_ACCESS_TOKEN`、`SUPABASE_DB_PASSWORD`；函数端另设 `DEEPSEEK_API_KEY`。

历史 release 签名证书当前不在仓库中。在找回并核对证书指纹前，不得声称新 APK 可覆盖安装旧正式版。

## 当前验证证据

- Android 17 项 JVM 测试、debug APK 构建与 Lint 通过；APK 为 47,342,109 bytes。
- MNN 3.6.1 arm64 原生桥已实际编译、链接并打入 APK；固定 manifest、路由、SSE、SHA、状态机、结构化事实与 30 条中文质量集测试通过。模型本体不在 APK 中。
- API 35 模拟器已安装并验证首页、时间页、课程导入/编辑预览和独立登录页；Room 3→4 的真实数据库迁移测试通过，旧时间和课程行得到保留。
- Web 生产构建通过；320、375、414、768、1280、1440 px 视觉回归及移动导航、数据筛选、未配置写操作门禁、登录错误路径共 9 项通过。
- 本机没有 Docker，因此 Supabase migration 的完整本地回放交由 CI；线上部署仍受备份门禁保护。
- 当前没有连接 arm64 8 GB 真机；完整模型下载、飞行模式推理、动态网络抓包和真实性能温度数据仍是发布门禁，未伪造结果。

视觉证据与检查范围见 [`design/visual-tests/README.md`](design/visual-tests/README.md)。
