<img src="BatteryPush.png" alt="BatteryPush" width="100%" />

# BatteryPush

让备用机的电量状态，第一时间触达你的主力机。

## 为什么选择 BatteryPush

- 即插即用：低电量/充满电等场景自动触发提醒
- 多通道触达：通知栏、短信、电话、Webhook（飞书/钉钉）一网打尽
- 可视化记录：所有提醒历史一览无余，便于追踪与复盘
- 灵活配置：支持电话号码、Webhook、模板内容，随需而变
- 场景联动：适配安卓自动任务（如小米 手机管家 → 自动任务）

## 支持的能力

- 支持通知栏消息推送
- 支持短信提醒（部分机型可能存在二次弹窗）
- 支持电话提醒（一键直达，高优先级）
- 支持 Webhook（飞书、钉钉等）与现有业务系统打通
- 支持提醒记录查看与一键清空
- 支持设备信息与电量模板自动填充
- 支持总开关控制启动即自动执行

## 下载 APP

- [下载 BatteryPush APK（v1.0 调试版）](https://github.com/louislili/BatteryPush/raw/main/app/build/outputs/apk/debug/app-debug.apk)
- [在 Releases 查看 v1.0](https://github.com/louislili/BatteryPush/releases/tag/v1.0)

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

## 应用定位

- BatteryPush：电量通知与跨设备提醒，适配安卓自动化场景，支持通知/短信/电话/Webhook，含记录与配置。

## 开源协议

- MIT License