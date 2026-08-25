# CampusAI 源码安全审计

审计范围：`apps/android`、`apps/admin`、`supabase`、`.github/workflows`，以及通过官方 Supabase MCP 对项目 `mcpjecboqddqelgikvvc` 执行的结构核对、migration 部署、Advisor 与近 24 小时日志检查。

## 线上 Supabase 结论（2026-08-22）

- 项目位于 `ap-northeast-2`，状态 `ACTIVE_HEALTHY`，Postgres 17.6；12 份版本化 migration 已应用，Edge Functions 仍为空。
- `public` 现有 23 张 canonical 表、33 条 policy，全部业务表启用 RLS；旧 `legacy` schema 已移除，`pg_trgm` 已移动到 `extensions`。
- Auth 保留 2 个用户并补建 2 个 profile；用户明确不要旧业务数据，旧头像文件已删除。Storage 当前仅有一个 `.emptyFolderPlaceholder` 元数据，不含用户图片。
- 注册开关已开启、邮箱确认已关闭，因此邮箱＋密码注册可直接建立会话。泄露密码保护在当前 Free 方案不可用，仍由客户端 8 位最低长度与服务端基础规则兜底。[密码安全说明](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)
- Advisor 的未索引外键、重复 permissive policy、函数 `search_path` 与扩展 schema 告警已修复。现有 SECURITY DEFINER 告警对应刻意暴露给 authenticated 的受控 RPC；函数内部再次校验 `auth.uid()`、所有权或角色。[Advisor 说明](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable)
- 2026-08-21 项目恢复期间出现短暂 502/521、数据库快速重启与 migration 重试；当前健康检查已恢复 200。没有证据表明是应用请求造成的数据损坏。

## 威胁模型

- 外部输入：邮箱/密码、帖子/商品/聊天内容、课程截图、`.ics` 文件、AI 消息与上下文、Storage 对象名、管理台筛选和 Supabase API 参数。
- 高价值资产：Auth 会话、私人时间与课程数据、聊天、订单状态、管理员角色、审计日志、用户个人 DeepSeek Key 与 AI 额度。
- 信任边界：Android Keystore/本地存储 ↔ Supabase；Android 个人 AI 请求 ↔ DeepSeek；Web 浏览器 ↔ RLS；可选 Edge Function ↔ DeepSeek；GitHub Actions ↔ 发布凭据。

## 已修复

### 严重

1. 旧 Android 数据管理器用公开 anon key 作为 Bearer 执行业务写入。旧管理器已删除，业务客户端只保留真实用户 JWT。Android AI 不使用项目所有者 Key：每位用户的个人 DeepSeek Key 经 Android Keystore 加密，只发往固定 `api.deepseek.com`，缺失或失败时不回退平台额度。
2. `moderator` 曾被 `is_admin()` 视为全局管理员，可读取私人时间、聊天、订单并设置角色。现拆分 `is_staff`、`is_admin`、`is_super_admin`；角色变更只允许 super admin。
3. 管理台路由曾仅有登录页、没有受保护路由。现配置 Supabase 后，路由加载与登录完成都验证服务端角色；无权限账号会立即退出。

### 高

1. 多个 `SECURITY DEFINER` RPC 依赖默认函数权限。现显式撤销 PUBLIC/anon，再只向 authenticated 授权。
2. 订单双方原本都能推进任意“允许”的状态。现付款仅买家确认、完成仅买家确认、争议后的完成/取消仅管理员处理，并保留版本冲突检查。
3. Storage 更新策略未限制 bucket。现只允许用户在公开业务 bucket 的本人目录更新；聊天媒体继续按会话成员验证。
4. 安全存储失败时曾退化为 Base64 保存 token。现拒绝持久化并给出可恢复错误，不把混淆误当加密。
5. 业务表原有表级写权限允许绕过客户端设置审核状态、举报结果或交易字段。现改为最小列权限；审核、封禁、公告、版本、评论、消息和同步状态只通过受控 RPC 修改并记录审计。

### 中

1. AI 请求原可接受客户端 `system` 消息、总大小未受限、上游错误正文可能进入日志。现只接受 user/assistant、按流限制 256 KiB、限制总字符，并只记录上游状态码。
2. 课程图片与 `.ics` 原缺少输入大小上限。现图片限制 25 MB、日历限制 1 MB；选择器 URI 仍由 Android 权限模型控制。
3. 点赞/收藏 RPC 作为 definer 可能操作不可见目标。现重新验证目标状态与当前身份。
4. 会话创建并发可能生成重复会话。现对参与者与关联商品的稳定键使用事务级 advisory lock。
5. Cloudflare 部署在 Secret 为空时仍可能发布演示模式。现部署前强制检查 Supabase 与 Cloudflare 配置。
6. 同一设备切换账号原可能混用本地时间与课程。Room v4 现在保存数据所有者，只展示当前账号与尚未认领的本地数据；首次同步后立即归属当前账号。
7. 帖子与商品图片上传现在限制为 JPEG/PNG/WebP、最大 15 MB；业务写入失败会补偿删除刚上传的孤儿对象。
8. 个人 DeepSeek Key 不进入 DataStore、日志、备份、设备迁移或 Supabase；只保存 AES-256-GCM 密文和 IV，已保存值仅显示掩码。Root、调试注入或已受控设备仍可能读取进程内明文，这是 BYOK 的设备信任边界。

## 自动与人工验证

- `npm audit --omit=dev`：0 vulnerabilities。
- Deno：`deno check supabase/functions/ai-chat/index.ts` 通过。
- PostgreSQL：12 份 migration 已应用线上；新增成就迁移已用回滚事务验证触发授予，完整空白环境回放仍需 Docker 或开发分支。
- Android：24 项 JVM tests、debug APK 与 Lint 通过；Room 3→4 迁移已在 API 35 模拟器真实执行并保留旧行。
- Web：TypeScript/Vite 构建和 9 个 Playwright 响应式/交互测试通过。
- 密钥与危险 API：仓库模式扫描未发现硬编码 provider/service-role 密钥、命令执行、WebView JS 或不安全反序列化入口；CI 同时运行 Gitleaks。
- Gradle distribution 增加官方 SHA-256；CI 的 setup-gradle 同时验证 wrapper JAR。

## 发布前残余风险

1. 线上 migration 已按用户放弃旧业务数据的决定完成；本机无 Docker，尚未在隔离 Postgres/Supabase 容器完整回放、验证 RLS 三角色矩阵和并发 RPC。
2. `lienqi0906@gmail.com` 已按用户指令提升为 `super_admin` 并记录审计；仍需用该账号完成管理台真实登录和写操作联调。
3. 原正式 Android release keystore 缺失，尚不能证明覆盖升级签名一致。
4. 管理台已部署到 Cloudflare，但写操作与 Android 评论、消息、订单、媒体和离线同步尚未完成真实三角色联调。
5. Supabase 项目当前没有开发分支；创建分支需要额外费用确认。
6. 课程截图 OCR 仍需要多所学校样本做识别率、超大图片和畸形布局测试。

这些残余项是发布门禁，不应通过客户端降级、清库或跳过备份来规避。
