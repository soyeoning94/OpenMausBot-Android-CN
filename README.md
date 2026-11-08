# OpenMausBot Android 简体中文版 (v1.5.0)

本项目是基于 [milind-soni/OpenMausBot](https://github.com/milind-soni/OpenMausBot) 官方安卓伴侣客户端（`android-v1.5.0` / `android-release/v1.5.0`）制作的**全量简体中文汉化版**。

原版安卓客户端仅包含英文硬编码界面与资源，本项目对其进行了完整的简体中文本地化适配，同时 100% 保持与 OpenMausBot 桌面端及服务端通信协议的兼容性。

---

## 📥 直接下载安装 (APK)

前往 [**Releases 页面**](https://github.com/soyeoning94/OpenMausBot-Android-CN/releases) 下载最新已签名的中文版安装包：
- **`OpenMausBot-v1.5.0-中文版.apk`**（已通过 APK Signature Scheme v2/v3 签名校验，支持 Android 8.0 / API 26 及以上设备直接安装）

---

## ✨ 汉化内容概览

1. **系统资源与通知服务 (`res/values` & `res/values-zh`)**
   - 后台常驻连接服务通知、权限审批通知渠道、前后台状态提示。
2. **全量界面与交互组件 (`:app` 模块)**
   - **配对与引导**：扫码配对页 (`QrScannerScreen`)、手动配对与安全确认 (`PairingScreen`)、首次配对引导与通知权限说明 (`OnboardingScreens`)。
   - **主界面与收件箱**：智能体/群聊列表 (`RosterScreen`)、待处理审批收件箱 (`AttentionInbox`)、智能体概览 (`BotOverviewScreen`)、会话树与免打扰设置 (`BotThreadTree` / `SnoozeRules`)。
   - **对话与工具审批**：聊天主界面 (`ChatScreen`)、快捷命令面板 (`CommandHud`)、权限审批与差异对比卡片 (`PermissionRequests` / `TranscriptCardViews`)、多选问答卡片 (`QuestionCard`)、Claude Code 在线更新卡片 (`ClaudeUpdateCard`)。
   - **语音与远程桌面**：实时语音通话 (`CallScreen`)、语音引擎与 ElevenLabs 设置 (`VoiceSettingsScreen`)、本地语音听写 (`SpeechDictation`)、远程电脑屏幕与云桌面预览 (`ComputerScreen`)。
   - **自动化与设置**：例程与定时任务管理 (`TasksRoutinesScreen` / `RoutineEditorSheet`)、已连接应用授权 (`ConnectedAppsScreen`)、系统分享面板 (`ShareSheet`)、全局设置 (`SettingsScreen`)。
3. **核心状态与网络诊断 (`:core` 模块)**
   - 配对路由故障排查建议 (`Failover`)、网络与服务端错误提示 (`Client` / `Connection` / `Session`)、附件大小与格式校验提示 (`MessageAttachments`)。
   - **协议零破坏**：完整保留 WebSocket/SSE 帧类型、JSON 序列化字段、权限回调动作值（`allow` / `deny` / `Always allow`）以及斜杠指令（`/computer`、`/threads`、`/diff`、`/retry`、`/steer`），确保与原版桌面端无缝协同。

---

## 🛠️ 本地构建说明

### 环境要求
- **JDK 17** 或 **JDK 21**
- **Android SDK**（`compileSdk 37` / `build-tools 36.0.0`）

### 编译 Debug / Preview 安装包
```bash
cd android
./gradlew :app:assembleDebug
```
编译产物位于：`android/app/build/outputs/apk/debug/app-debug.apk`

### 编译 Release 安装包
若未配置签名密钥，直接运行以下命令将生成未签名的 Release 包（`app-release-unsigned.apk`）：
```bash
cd android
./gradlew :app:assembleRelease
```

如需直接生成已签名的 Release APK，可在 `android/keystore.properties`（已被 `.gitignore` 忽略，不会提交到仓库）中配置签名信息：
```properties
storeFile=/path/to/your-release.jks
storePassword=your_store_password
keyAlias=openmausbot
keyPassword=your_key_password
```
然后再执行 `./gradlew :app:assembleRelease`，即可在 `android/app/build/outputs/apk/release/app-release.apk` 生成签名版安装包。

---

## 📄 开源协议与致谢

- 上游原项目：[milind-soni/OpenMausBot](https://github.com/milind-soni/OpenMausBot)
- 本仓库遵循上游项目的开源许可协议（详见 [LICENSE](./LICENSE) 与 [LICENSING.md](./LICENSING.md)）。
