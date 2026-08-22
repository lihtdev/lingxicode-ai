# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

CodeSense AI（灵犀码）是面向 JetBrains IDE 的 AI 能力集插件（插件 id `com.lihtdev.codesense`）。v1 功能：在 Git 提交对话框一键调用大模型，根据勾选的代码变更生成 Conventional Commits 格式提交信息并回填提交框。目标平台 2024.2+（sinceBuild 242），仅依赖 `com.intellij.modules.platform`，全系列 IDE 可用。深入设计细节（组件职责表、数据流、错误处理）见 `docs/design.md`。

## 常用命令

> 注意：Gradle Wrapper 已入库（钉死 **Gradle 9.0.0**），请直接用 `./gradlew` 执行下述任务。不要用其它版本的本机 Gradle 重新生成 wrapper，以免构建工具链版本漂移。

```bash
# 全量单测
./gradlew test

# 单个测试类 / 单个测试方法
./gradlew test --tests "com.lihtdev.codesense.ai.ResponseCleanerTest"
./gradlew test --tests "com.lihtdev.codesense.ai.ResponseCleanerTest.方法名"

# 沙箱 IDE 中端到端调试（提交框按钮渲染、真实调用、回填）
./gradlew runIde

# 构建可安装插件包（产物在 build/distributions/）
./gradlew buildPlugin

# 插件兼容性验证（2024.2+）
./gradlew verifyPlugin
```

## 架构主线

五层调用链，改动新功能时务必保持分层边界：

```
action（GenerateCommitMessageAction 提交框按钮 / CodeSenseActionGroup 编辑器右键预留组）
  → feature（AiFeature 抽象；v1 实现：CommitMessageFeature）
    → service（AiInvocationService：统一执行管线 + 通知）
      → ai（OpenAiCompatClient：唯一协议实现）
        → settings（AppSettings 持久化 + PasswordSafe 密钥）
```

- **扩展机制（新增 AI 功能的唯一路径）**：实现 `feature/AiFeature`（`buildPrompt` 在后台线程执行、`handleResult` 在 EDT 执行），经 plugin.xml 的 `codesense.aiFeature` 扩展点注册，并挂载到 `CodeSense.EditorGroup`。执行层（AiInvocationService）与 AI 层（OpenAiCompatClient）不需改动。
- **线程模型**：EDT 取上下文并做前置校验（未配置厂商/密钥 → 警告通知）→ `Task.Backgroundable` 内经 `ReadTask` 读动作组 prompt → 后台线程 HTTP 调用（可取消，`InterruptedException` 直接向上抛）→ `ResponseCleaner` 清洗 → `invokeLater` 回 EDT 处理结果。禁止在 EDT 发起网络请求。
- **协议唯一性**：所有厂商统一 OpenAI 兼容协议（`POST {baseUrl}/chat/completions`）。厂商「类型」（按量付费 / Coding Plan / Token Plan）差异仅为 baseUrl，无协议分支；HTTP 401/403/404/429 有固定中文错误映射（见 `OpenAiCompatClient.mapError`）。
- **密钥与配置**：API Key 存 IntelliJ PasswordSafe（serviceName=CodeSenseAI，key=providerId），不落盘；其余设置经 `PersistentStateComponent` 持久化（`codesense-ai.xml`）。

## 构建与依赖约束

- JVM toolchain 固定 21（对应 2024.2 的 JBR 21），不要降级；构建工具链钉死 Gradle 9.0.0（wrapper 已入库），Kotlin 插件 2.4.10 + IntelliJ Platform Gradle Plugin 2.18.1，升级前先核对三方兼容矩阵。
- `kotlin.stdlib.default.dependency=false`：不随插件打包 Kotlin 标准库（平台提供），不要改回。
- **Gson 由平台捆绑提供**，主代码不可加 `implementation` 依赖（会触发 verifier 问题）；仅单测环境自行声明（`testImplementation`）。
- HTTP 使用 JDK 内置 `java.net.http.HttpClient`，不要引入 OkHttp 等第三方 HTTP 库。

## 测试约定

- 纯逻辑（SimpleLineDiff / DiffFormatter / PromptBuilder / ResponseCleaner / ChatModels DTO）保持纯函数形态并配 JUnit 5 单测。
- AI 客户端测试用 JDK 内置 `com.sun.net.httpserver.HttpServer` 模拟 OpenAI 兼容端点（参考 `OpenAiCompatClientTest`），不依赖外部网络。
- 依赖平台 API 的行为（按钮渲染、变更收集、消息回填）靠 `runIde` 沙箱手动验证，不写单测。

## 语言约定

注释、错误提示、通知文案与文档统一使用中文，与现有代码保持一致。
