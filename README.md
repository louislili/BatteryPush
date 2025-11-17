<img src="BatteryPush.png" alt="BatteryPush" width="100%" />

# BatteryPush

面向安卓用户的电量跨设备提醒工具。在备用机低电量或充满电等场景下，自动向你的主力机发送提醒。

## 特性

- 支持通知栏消息、短信提醒（部分机型可能存在二次弹窗）、电话提醒、Webhook（飞书/钉钉等）
- 支持查看提醒记录，配置电话与Webhook等
- 可与手机管家/自动任务联动（如小米：手机管家 → 自动任务）
- 真实电量与设备信息，模板自动填充

## 快速上手

- 构建调试版：
  ```bash
  ./gradlew assembleDebug
  ```
- 安装到设备：
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- 首次打开按需授予通知、短信、电话权限

## 下载 APP

- 直接下载 APK（调试版）：
  - https://github.com/louislili/BatteryPush/raw/main/app/build/outputs/apk/debug/app-debug.apk
  - 也可在 Releases 标签页查看 `v1.0`

## 应用定位

- BatteryPush：电量通知与跨设备提醒，适配安卓自动化场景，支持通知/短信/电话/Webhook，含记录与配置。

## 开源协议

- MIT License