# CampusAI 源码安全审计

审计范围：`apps/android`、`apps/admin`、`supabase`、`.github/workflows`，以及通过官方 Supabase MCP 对项目 `mcpjecboqddqelgikvvc` 执行的只读元数据、Advisor 与近 24 小时日志检查。没有执行线上 DDL/DML。

## 线上 Supabase 只读结论（2026-08-22）

- 项目位于 `ap-northeast-2`，状态 `ACTIVE_HEALTHY`，Postgres 17.6；数据库 migration 历史为空，Edge Functions 为空。
- `public` 有 12 张旧表，业务行数均为 0；Auth 有 2 个用户，`profiles` 为 0 行，因此两个身份当前都缺少业务 profile。
- Storage 有 1 个 bucket、1 个 object；旧 Storage 有 4 条 policy，迁移时必须保留对象和元数据。
- 12 张旧业务表全部启用 RLS，但 `public` policy 总数为 0，导致客户端无法正常访问；这是当前不可用的直接原因之一。[Supabase Advisor 说明](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)
- `public.handle_new_user()` 是无固定 `search_path` 的 `SECURITY DEFINER`，并向 PUBLIC、anon、authenticated 暴露执行权限；本地 reconciliation migration 会先解除 Auth trigger、删除旧函数，再建立最小权限版本。[Advisor 说明](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable)
- Auth 泄露密码保护未开启，正式开放注册前应在控制台启用。[密码安全说明](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)
- 性能 Advisor 报告 12 个未索引外键；canonical schema 已补充对应用户、商品、订单、消息、举报和发布者索引。
- 2026-08-21 项目恢复期间出现短暂 502/521、数据库快速重启与 migration 重试；当前健康检查已恢复 200。没有证据表明是应用请求造成的数据损坏。

## 威胁模型

- 外部输入：邮箱/密码、帖子/商品/聊天内容、课程截图、`.ics` 文件、AI 消息与上下文、Storage 对象名、管理台筛选和 Supabase API 参数。
- 高价值资产：Auth 会话、私人时间与课程数据、聊天、订单状态、管理员角色、审计日志、DeepSeek 密钥与 AI 额度。
- 信任边界：Android 本地存储 ↔ Supabase；Web 浏览器 ↔ RLS；Edge Function ↔ DeepSeek；GitHub Actions ↔ 发布凭据。

## 已修复

### 严重

1. 旧 Android 数据管理器用公开 anon key 作为 Bearer 执行业务写入。旧管理器已删除，客户端只保留真实用户 JWT；AI 仍只经过 Edge Function。
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

## 自动与人工验证

- `npm audit --omit=dev`：0 vulnerabilities。
- Deno：`deno check supabase/functions/ai-chat/index.ts` 通过。
- PostgreSQL：十份 migration 均通过 PostgreSQL AST 解析；完整执行回放仍需 Docker 或付费开发分支。
- Android：JVM tests、debug APK、androidTest 编译通过；Room 3→4 迁移已在 API 35 模拟器真实执行并保留旧行。
- Web：TypeScript/Vite 构建和 9 个 Playwright 响应式/交互测试通过。
- 密钥与危险 API：仓库模式扫描未发现硬编码 provider/service-role 密钥、命令执行、WebView JS 或不安全反序列化入口；CI 同时运行 Gitleaks。
- Gradle distribution 增加官方 SHA-256；CI 的 setup-gradle 同时验证 wrapper JAR。

## 发布前残余风险

1. 线上 Supabase 已通过 MCP 完成只读结构与数量核对，但尚未生成可恢复备份；在拿到备份 ID 前禁止 apply migration。
2. 本机无 Docker，尚未在隔离 Postgres/Supabase 容器完整回放 reconciliation migration、RLS 三角色矩阵和并发 RPC 集成测试。
3. 原正式 Android release keystore 缺失，尚不能证明覆盖升级签名一致。
4. 管理台写操作与 Android 的评论、消息、订单、媒体和离线同步已实现，但尚未在迁移后的隔离 Supabase 上完成真实三角色联调；未配置管理台仍保持只读演示模式。
5. Supabase 项目当前没有开发分支；创建分支需要额外费用确认，不能擅自用生产库代替回放环境。
6. 课程截图 OCR 仍需要多所学校样本做识别率、超大图片和畸形布局测试。

这些残余项是发布门禁，不应通过客户端降级、清库或跳过备份来规避。
