# 视觉回归证据

这些图片用于把实现与 `design/approved-spec.md`、两张高保真 SPECTRA 基准图进行同视口核对。

`campusai-orb-fixed.png` 记录 1440×3200 Xiaomi 真机上的 AI 状态球批准方向：固定圆形银色容器，只有内部暗银液体、局部焦散与颗粒移动；外轮廓不得挤压、拉伸或分离液滴。

## 已执行

- Web：320、375、414、768、1280、1440 px；检查无横向溢出、移动抽屉可导航、缺少 Supabase 配置时登录错误可恢复。
- Android：API 35 模拟器上的首页、时间页、课程导入来源弹窗、可编辑课程预览和独立登录页。
- SPECTRA：Web 共享单 Canvas；Android 共享 OpenGL ES 层；均有动效关闭和上下文不可用时的纯色降级。

## 仍需发布门禁

- Android 浅色、深色、四环境、200% 字体、高对比和真实低端设备 GPU/功耗矩阵。
- 截图 OCR 的多所学校样本集与误识别修正率。
- 线上 Supabase 数据接入后的加载、空、错误、离线、权限不足和部分失败状态。
- 原正式签名证书覆盖安装验证。

Web 普通回归通过 `npm run test:visual` 将截图保存为每次运行独立的 Playwright 工件，避免覆盖已被预览程序占用的版本化基准。确认需要更新基准时，在 PowerShell 中执行 `$env:UPDATE_VISUAL_BASELINES='1'; npm run test:visual`。Android 图片来自已安装 debug APK 的真实模拟器画面。

Android 当前证据：`android-home-live.png`、`android-time-live.png`、`android-import-live.png`、`android-course-preview-live.png`、`android-login-live.png`。

## 2026-08-22 截图问题修正门禁

- 已修正 AI 全屏页叠在首页之上、状态栏碰撞、系统返回无出口和流式任务重复触发。
- 已将 Profile 改为摘要＋独立底部抽屉，并接通头像、名称、简介、背景的 Supabase 真实读写。
- 已将成就改为 7 个 C＋节点光学徽章和真实进度，并部署服务端授予触发器。
- 已加强共享 GL 体积色场并降低浅色玻璃不透明度；四环境现在使用不同场色与可视胶囊样本。
- 已用批准的拉丝金属无限环位图替换 Adaptive/Legacy 启动图标；暖象牙背景和中心安全区需在圆形、圆角方形与系统默认遮罩下分别验收。
- Kotlin、单元测试、APK 与 Lint 已通过。当前本机只有 x86_64 AVD，而正式包只支持 arm64-v8a，因此这些改动尚未生成新的真机截图；上方旧 Android 图片不能作为本轮视觉通过证据。
