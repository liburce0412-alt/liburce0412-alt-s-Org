# CampusAI 线上接管门禁

目标 Supabase 项目：`mcpjecboqddqelgikvvc`。

当前仓库已建立目标 schema、RLS、事务 RPC、Storage 策略和 AI Edge Function，但没有对线上项目执行写入。这是有意的安全边界：未知旧 schema 不能仅凭 `create table if not exists` 安全升级。

## 必须按顺序完成

1. 已通过官方 Supabase MCP 确认项目位于 `ap-northeast-2`、Postgres 17.6、状态 `ACTIVE_HEALTHY`。
2. 生成可恢复的数据库备份；导出 Auth 用户/身份映射和 Storage bucket/object 元数据；记录备份 ID。
3. 已完成只读底图：12 张旧 public 表均为 0 行、2 个 Auth 用户、1 个 Storage 对象、public policy 为 0、migration 历史为空。
4. 已建立本地 reconciliation：旧表整体隔离到不可公开访问的 `legacy` schema，再执行 `products → listings`、`user → student`、旧帖子收藏、关系与媒体路径的增量导入；现有 Auth 用户会补建 profile。
5. 为差异新增一份版本化 reconciliation migration；禁止在 Dashboard 临时改表后不回写 migration。
6. 在隔离环境从备份恢复，运行 `supabase db reset` / lint，并逐表核对数量、孤儿外键与 Storage 对象。
7. 验证普通用户、版主、管理员三类 RLS；验证订单状态、点赞/收藏、撤销和审计 RPC 的并发行为。
8. 在维护窗口执行手动 `Deploy Supabase (backup gated)` workflow，输入真实备份 ID 和确认短语。
9. 部署后运行只读核对，再开放 Android/Web 客户端。

## 回滚条件

出现任一情况立即停止流量切换并回滚：行数异常、媒体丢失、Auth 用户无法映射、普通用户越权、订单状态倒退、旧客户端崩溃或新 APK 签名不一致。

## 发布前仍需用户提供

- 线上 Supabase 的可验证备份 ID 与数据库密码。
- DeepSeek 服务端密钥。
- Cloudflare 账号与受限 API Token。
- 原 Android 正式 release keystore、alias 和密码；先比对证书 SHA-256，再验证覆盖安装。
