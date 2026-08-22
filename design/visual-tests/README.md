# 视觉回归证据

这些图片用于把实现与 `design/approved-spec.md`、两张高保真 SPECTRA 基准图进行同视口核对。

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
