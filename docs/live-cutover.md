# CampusAI 线上接管状态与后续门禁

目标 Supabase 项目：`mcpjecboqddqelgikvvc`。

2026-08-22 已通过官方 Supabase MCP 将 12 份版本化 migration 应用到生产项目。用户明确不保留旧业务数据，因此空的旧业务表和 `legacy` schema 已清理；现有 2 个 Auth 身份仍保留并补建 profile，避免误删登录账号。旧头像文件已删除，Storage 只剩空目录占位元数据。

## 已完成

1. 已通过官方 Supabase MCP 确认项目位于 `ap-northeast-2`、Postgres 17.6、状态 `ACTIVE_HEALTHY`。
2. 建立并应用 canonical schema、RLS、事务 RPC、Storage 策略、管理工作流和离线同步 RPC。
3. 线上最终状态：23 张 public 表、33 条 policy、全部业务表启用 RLS、`legacy` schema 不存在、`pg_trgm` 位于 `extensions`。
4. 2 个 Auth 用户均已补建 profile；7 条成就定义及时间记录触发器已写入，其他业务表为空。
5. `lienqi0906@gmail.com` 已按用户指令设置为 `super_admin`，并写入 `audit_logs`。
5. 后置加固已修复未索引外键、重复 permissive policy、函数 `search_path` 与扩展 schema 问题。
6. Web 管理台已部署到 `https://campusai-admin.campus3ai-games.workers.dev`，根路由和 SPA 深链接均返回 200。

## 上线前仍需完成

1. 用户指定一个注册邮箱，将其 profile 角色提升为 `super_admin`；不能猜测或自动提升任意账号。
2. 用普通用户、版主、管理员三类账号执行 RLS 与管理 RPC 真机/浏览器联调。
3. 在空白 Supabase 容器完整回放 12 份 migration，并补充并发 RPC 集成测试。
4. 找回正式 Android release keystore，核对证书 SHA-256 后验证旧 APK 覆盖安装。
5. 在至少一台 8 GB arm64 真机完成本地模型下载、校验、离线推理、抓包、内存与温度测试。

## 回滚条件

出现任一情况立即停止流量切换并回滚：行数异常、媒体丢失、Auth 用户无法映射、普通用户越权、订单状态倒退、旧客户端崩溃或新 APK 签名不一致。

## 仍需用户提供

- 要设为 Web 管理员的 CampusAI 注册邮箱。
- 原 Android 正式 release keystore、alias 和密码；先比对证书 SHA-256，再验证覆盖安装。

Android 不需要平台 DeepSeek 密钥：每位用户在应用内保存自己的 Key。只有以后明确启用 Web/服务端 `ai-chat` 时，才需要单独配置服务端 DeepSeek Secret。
