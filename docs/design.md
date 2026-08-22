# CodeSense AI（灵犀码）设计文档

> 生成日期：2026-08-21（实施首日，由已批准的实施计划落地）

## 1. 背景与目标

CodeSense AI 是面向 JetBrains IDE 的 AI 能力集插件。v1 交付「AI 提交信息生成」：在 Git 提交对话框中一键调用大模型，根据将要提交的代码变更（diff）生成 Conventional Commits 格式的提交信息并填入提交消息框。架构上预留「AI 代码解释」等后续功能的扩展入口。

### 需求决策记录

| 决策点 | 结论 |
|---|---|
| 项目命名 | CodeSense AI（中文名：灵犀码），插件 id `com.lihtdev.codesense` |
| AI 接入 | 预设 7 家厂商 + 任意自定义端点；**API 统一 OpenAI 兼容格式** |
| 类型 | Token Plan / Coding Plan / 按量付费；**区别仅为 baseUrl**（MiniMax 两类型同端点） |
| 模型名 | 预设列表 + 自定义输入（可编辑下拉） |
| 提交信息格式 | Conventional Commits（`type: 描述`），描述默认中文可切英文 |
| 交互入口 | 提交对话框消息区 toolbar 按钮；编辑器右键预留「CodeSense AI」功能组 |
| 变更范围 | 跟随提交对话框勾选文件；无勾选回退默认 changelist |
| 目标平台 | 全系列 JetBrains IDE 2024.2+（仅依赖 `com.intellij.modules.platform`，sinceBuild 242） |
| 技术栈 | Kotlin + IntelliJ Platform Gradle Plugin 2.x + java.net.http.HttpClient + Gson（平台捆绑）+ Task.Backgroundable |

## 2. 架构

```
UI/Action 层
  ├── GenerateCommitMessageAction（提交框按钮，v1）
  └── CodeSenseActionGroup（编辑器右键「CodeSense AI」组，预留挂载点）
      ▼
功能层（AiFeature 抽象）
  └── CommitMessageFeature（v1）    [后续：ExplainCodeFeature 等]
      ▼
执行层（AiInvocationService + Task.Backgroundable）
  │ 组 prompt（读动作）→ AiClient → 清洗 → invokeLater 回调
      ▼
AI 层（OpenAiCompatClient：POST {baseUrl}/chat/completions）
      ▼
设置层（AppSettings: PersistentStateComponent + PasswordSafe 存密钥）
```

### 可扩展功能框架

```kotlin
interface AiFeature {
    val id: String
    val displayName: String
    fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage>  // 后台线程
    fun handleResult(result: String, context: Any)                                 // EDT
}
```

后续新功能（如 AI 代码解释）只需实现 `AiFeature` 并经 plugin.xml 的 `codesense.aiFeature` 扩展点注册，挂载到编辑器右键 `CodeSense.EditorGroup`，无需改动执行层与 AI 层。

## 3. 核心组件

| 组件 | 职责 |
|---|---|
| `GenerateCommitMessageAction` | 提交框按钮：取 CommitMessage/Change[]，前置校验，启动管线 |
| `ChangeCollector` | 优先 `VcsDataKeys.CHANGES`（勾选变更），回退默认 changelist；过滤 .lock |
| `SimpleLineDiff` | LCS 行级 diff（纯函数）；超过 2000 行降级整文件替换 |
| `DiffFormatter` | 单文件 section 格式化（纯函数）；单文件 400 行截断 |
| `DiffTextBuilder` | 平台胶水：Change → 内容读取（失败降级文件名清单）→ section 拼装；总量 60000 字符（可配置）截断 |
| `PromptBuilder` | 系统提示词（Conventional Commits 规则/语言/单行输出约束）+ 用户消息（文件清单 + diff） |
| `OpenAiCompatClient` | OpenAI 兼容 HTTP 客户端；10s 连接/60s 请求超时；401/403/404/429 中文错误映射 |
| `ResponseCleaner` | 清洗输出：去围栏/引号，取首行非空；Conventional 格式校验 |
| `AiInvocationService` | 统一执行管线 + 通知（CodeSenseAI 通知组） |
| `AppSettings` | 持久化（`codesense-ai.xml`）+ PasswordSafe 密钥（serviceName=CodeSenseAI, key=providerId） |
| `SettingsConfigurable` | 设置页：厂商/类型/baseUrl/model（可编辑下拉）/API Key + 添加自定义/删除/测试连接 + 语言/diff 上限 |

## 4. 数据流

1. EDT：按钮点击 → 取 `CommitMessage` + `Change[]` → 校验（未配置厂商/密钥 → 警告通知）；
2. 后台（Task.Backgroundable）：读动作内组 diff → 组 prompt → HttpClient 调 `chat/completions`（可取消）；
3. 清洗输出 → `invokeLater` 回填 `CommitMessage.setCommitMessage()`（用户可继续编辑）；
4. 失败经 `onThrowable` → 错误通知（含中文原因）。

## 5. 错误处理

- 未配置厂商/密钥 → 警告通知；
- 401「API Key 无效」/ 403「无访问权限」/ 404「接口地址或模型名有误」/ 429「请求过于频繁或额度不足」（附 error.message）；
- 网络/超时/解析失败 → 错误通知含异常摘要；
- 无变更 → 「没有可提交的变更」；
- 单文件内容读取失败 → 降级为文件名清单条目，不中断。

## 6. 测试策略

- 纯逻辑单测（JUnit 5）：SimpleLineDiff / DiffFormatter / PromptBuilder / ResponseCleaner / ChatModels（DTO 序列化）；
- AI 客户端测试：JDK 内置 HttpServer 模拟端点，验证请求结构/鉴权头/响应解析/错误映射/配置缺失提示；
- 平台集成：runIde 沙箱端到端（按钮渲染、真实调用、回填）；
- 兼容性：verifyPlugin（2024.2+）。

## 7. 待验证点（实施期确认）

1. `Vcs.Commit.Message.Toolbar` group id 在 242 中的正确性（备选：查平台 vcs-impl plugin.xml / 旧模态 `Vcs.MessageActionGroup`）；
2. `VcsDataKeys.CHANGES` 在非模态提交上下文的行为（备选：ChangeListManager）；
3. Gson 直接引用是否触发 verifier 报警（备选：implementation 依赖随包分发）；
4. PasswordSafe `CredentialAttributes` 构造签名以实际 SDK 为准。
