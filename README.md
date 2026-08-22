# CampusAI

CampusAI 是面向校园场景的 Android 主产品与响应式 Web 管理台。当前仓库以 SPECTRA 光学材质为统一设计语言，后端以 Supabase 为唯一业务数据入口。Android 可选下载 Qwen3.5-2B MNN 4-bit 本地模型；联网 DeepSeek FAST / DEEP 使用每位用户自己在设备上保存的 Key。

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

# Cloudflare Workers 静态资源部署
npm run deploy:dry-run
npm run deploy
```

Web 本地环境放在 `apps/admin/.env.local`：

```dotenv
VITE_SUPABASE_URL=https://mcpjecboqddqelgikvvc.supabase.co
VITE_SUPABASE_ANON_KEY=你的公开 publishable key（sb_publishable_...）
```

不要把 `service_role`、项目所有者的 DeepSeek 密钥或数据库密码写入 Android/Web 环境文件。
Supabase `sb_publishable_...` 是公开客户端标识，可以进入 Web/Android。Android 用户只可在应用的 AI 设置中录入自己的 DeepSeek Key；Key 由 Android Keystore 加密、不参与备份，也不会上传 Supabase。

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
- Android 的 `fast` / `deep` 直接调用固定域名 `api.deepseek.com`，且只使用当前用户在本机保存的 Key；没有 Key 时明确阻止请求，不存在平台额度回退。`supabase/functions/ai-chat` 保留为 Web/服务端能力，不被 Android 调用。
- Android 的 AI 运行方式可选自动、DeepSeek（我的 Key）、本地离线。本地模型首次安装不下载、不进入 APK，只接受版本化固定清单并存入 `noBackupFilesDir/models`。
- 本地模型下载支持 Wi-Fi 约束、明确移动网络确认、前台 WorkManager、断点续传、进程重启恢复、逐文件 SHA-256、原子 Ready、暂停、删除与重新下载。
- AI 架构、路由、隐私、模型更新、用户操作和性能记录见 [`docs/local-ai-architecture.md`](docs/local-ai-architecture.md)、[`docs/ai-routing-privacy.md`](docs/ai-routing-privacy.md)、[`docs/local-ai-model-update.md`](docs/local-ai-model-update.md)、[`docs/local-ai-user-guide.md`](docs/local-ai-user-guide.md) 与 [`docs/local-ai-performance.md`](docs/local-ai-performance.md)。
- Android 的时间记录与课程表采用 Room 本地优先；登录后由 WorkManager 同步当前账号与尚未认领的本地数据，退出后不会显示其他账号的私人记录。
- Android 登录页提供登录/注册切换；注册只需要邮箱、密码和确认密码。Supabase 已允许注册且关闭邮箱确认，所以注册成功后直接建立会话，不增加验证码步骤。
- 校园帖子、评论、举报、商品图片、会话、消息、购买和订单状态已接 canonical REST/RPC；管理台在真实配置下可执行审核、封禁、举报处理、公告和版本发布。
- 官方 Supabase MCP 已连接并应用 11 份版本化 migration。线上现有 23 张 canonical public 表、33 条 policy，全部业务表启用 RLS；旧 `legacy` schema 已移除，`pg_trgm` 位于 `extensions` schema。
- 用户明确放弃旧业务数据：旧表与旧头像文件已清理。为避免误删身份，现有 2 个 Supabase Auth 用户被保留并补建 profile；Storage 中只剩空目录占位元数据，不含旧头像内容。
- Web 管理台已部署到 [Cloudflare Workers](https://campusai-admin.campus3ai-games.workers.dev)。公开页面只使用 Supabase publishable key；管理写入仍由服务端角色和 RLS/RPC 校验。

线上接管步骤与硬性门禁见 [`docs/live-cutover.md`](docs/live-cutover.md)。
源码安全审计、已修复问题和残余发布风险见 [`docs/security-audit.md`](docs/security-audit.md)。

## 发布所需 Secrets

- Android：`ANDROID_KEYSTORE_BASE64`、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
- Cloudflare Workers：`CLOUDFLARE_API_TOKEN`、`CLOUDFLARE_ACCOUNT_ID`、`SUPABASE_ANON_KEY`。
- Supabase：`SUPABASE_ACCESS_TOKEN`、`SUPABASE_DB_PASSWORD`；只有启用服务端 `ai-chat` 时才另设 `DEEPSEEK_API_KEY`。Android 用户的个人 Key 不属于部署 Secret。

历史 release 签名证书当前不在仓库中。在找回并核对证书指纹前，不得声称新 APK 可覆盖安装旧正式版。

## 当前验证证据

- Android 24 项 JVM 测试、debug APK 构建与 Lint 全部通过；注册、个人 DeepSeek Key 和无平台回退已纳入回归。
- MNN 3.6.1 arm64 原生桥已实际编译、链接并打入 APK；固定 manifest、路由、SSE、SHA、状态机、结构化事实与 30 条中文质量集测试通过。模型本体不在 APK 中。
- API 35 模拟器已安装并验证首页、时间页、课程导入/编辑预览和独立登录页；Room 3→4 的真实数据库迁移测试通过，旧时间和课程行得到保留。
- Web 生产构建通过；320、375、414、768、1280、1440 px 视觉回归及移动导航、数据筛选、未配置写操作门禁、登录错误路径共 9 项通过。
- 11 份 Supabase migration 均通过 PostgreSQL AST 解析并已通过官方 MCP 应用到线上；本机没有 Docker，因此从空白容器完整回放与三角色并发集成测试仍交由 CI/后续环境完成。
- 当前没有连接 arm64 8 GB 真机；完整模型下载、飞行模式推理、动态网络抓包和真实性能温度数据仍是发布门禁，未伪造结果。

视觉证据与检查范围见 [`design/visual-tests/README.md`](design/visual-tests/README.md)。
