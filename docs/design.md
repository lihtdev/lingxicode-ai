# LingxiCode AI（灵犀码）设计文档

> 生成日期：2026-08-21（实施首日，由已批准的实施计划落地）

## 1. 背景与目标

LingxiCode AI 是面向 JetBrains IDE 的 AI 能力集插件。v1 交付「AI 提交信息生成」：在 Git 提交对话框中一键调用大模型，根据将要提交的代码变更（diff）生成 Conventional Commits 格式的提交信息并填入提交消息框。架构上预留「AI 代码解释」等后续功能的扩展入口。

### 需求决策记录

| 决策点 | 结论 |
|---|---|
| 项目命名 | LingxiCode AI（中文名：灵犀码），插件 id `com.lihtdev.lingxicode` |
| AI 接入 | 预设 7 家厂商 + 任意自定义端点；**API 统一 OpenAI 兼容格式** |
| 类型 | Token Plan / Coding Plan / 按量付费；**区别仅为 baseUrl**（MiniMax 两类型同端点） |
| 模型名 | 预设列表 + 自定义输入（可编辑下拉） |
| 提交信息格式 | Conventional Commits（`type: 描述`），描述默认中文可切英文 |
| 交互入口 | 提交对话框消息区 toolbar 按钮；编辑器右键预留「LingxiCode AI」功能组 |
| 变更范围 | 跟随提交对话框勾选文件；无勾选回退默认 changelist |
| 目标平台 | 全系列 JetBrains IDE 2024.2+（仅依赖 `com.intellij.modules.platform`，sinceBuild 242） |
| 技术栈 | Kotlin + IntelliJ Platform Gradle Plugin 2.x + java.net.http.HttpClient + Gson（平台捆绑）+ Task.Backgroundable |

## 2. 架构

```
UI/Action 层
  ├── GenerateCommitMessageAction（提交框按钮）
  ├── ExplainCodeAction / ReviewCodeAction / ExplainLineByLineAction（编辑器右键三入口）
  ├── AiCodeLineMarkerProvider（编辑器行号旁 gutter 图标，单击弹出三功能菜单）
  ├── ExplainCodeStarter / ReviewCodeStarter / ExplainLineByLineStarter（右键/gutter 统一触发入口）
  └── LingxiCodeActionGroup（编辑器右键「LingxiCode AI」组，锚定第一段）
      ▼
功能层（AiFeature 抽象）
  ├── CommitMessageFeature（提交信息生成）
  ├── ExplainCodeFeature（代码解释：Markdown 结构 → 非模态对话框展示）
  ├── ReviewCodeFeature（代码评审：多维度质量报告）
  └── ExplainLineByLineFeature（逐行解释：单个代码围栏 + 逐行注释）
      ▼
执行层（AiInvocationService + Task.Backgroundable）
  │ 组 prompt（读动作）→ AiClient(带 maxTokens) → 特征化清洗 → invokeLater 回调
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
    val maxOutputTokens: Int                                    // 默认 256；解释等长文本覆写
    fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage>  // 后台线程
    fun cleanResponse(raw: String): String                      // 默认 ResponseCleaner.clean；解释覆写 cleanMarkdown
    fun handleResult(result: String, context: Any)              // EDT
}
```

新功能（如后续更多 AI 能力）只需实现 `AiFeature` 并经 plugin.xml 的 `lingxicode.aiFeature` 扩展点注册，挂载到编辑器右键 `LingxiCode.EditorGroup`，无需改动执行层与 AI 层。「代码解释」即按此框架完成。

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
| `ResponseCleaner` | 清洗输出：去围栏/引号，取首行非空；Conventional 格式校验；`cleanMarkdown` 保留 Markdown 结构（仅去整篇包裹围栏）；`cleanFencedCode` 提取并保留单个代码围栏（逐行解释用，无围栏降级 + 未闭合/孤立尾围栏容错） |
| `ExplainCodeAction` | 编辑器右键入口：`CodeContextBuilder.build` 采集目标 → `ExplainCodeStarter.trigger` |
| `ExplainCodeStarter` | 共享触发入口（右键/gutter 共用）：空目标警告 → `AiInvocationService.invoke` |
| `AiCodeLineMarkerProvider` | 编辑器行号旁 gutter 图标：仅在类/接口/方法/函数等声明级符号名称标识符上挂「AI 代码功能」图标（灰暗配色，过滤变量/字段/参数），单击弹出「解释/评审/逐行解释」三功能菜单 |
| `CodeContextBuilder` | 采集待解释代码（EDT）：选区 → 光标最近 `PsiNameIdentifierOwner` → 同缩进块兜底；`fromElement` 从声明元素采集；超 20000 字符截断 |
| `SymbolKindDetector` | 纯函数符号判别（类/接口/方法/函数/代码块），语言无关关键字启发式 |
| `PromptBuilder.buildExplainCode` | 结构化解释提示词：五段固定标题 + 条件性第六段（流程图，仅复杂控制流时由模型追加，ASCII/Unicode 制表符绘制于无语言标注围栏内）+ 语言/文件/符号类型/代码，输出语言跟随设置 |
| `MarkdownToHtml` | 受限 Markdown 子集 → HTML（标题/加粗/行内代码/代码围栏/无序与有序列表/段落 + 转义），零第三方依赖；列表按行首缩进支持多级嵌套（相对缩进，兼容 2/4 空格风格）；围栏渲染依赖等宽字体 + 空白保留（流程图对齐依赖；已知局限：CJK 与 Unicode 框线字符混排时列对齐依赖字体回退，可能不完美） |
| `ExplainCodeFeature` | 代码解释功能：组装解释 prompt，`cleanMarkdown` 清洗，渲染后弹 `AiStreamingDialog`（非模态流式展示） |
| `ReviewCodeFeature` | 代码评审功能：多维度质量报告 prompt（12 固定标题按重要性降序），`cleanMarkdown` 清洗，复用 `AiStreamingDialog` 展示 |
| `ExplainLineByLineFeature` | 逐行解释功能：`buildExplainLineByLine` prompt（单个代码围栏 + 每行实义代码上方一条注释，空行/纯闭括号/原有注释行跳过，代码原样保留），`cleanFencedCode` 清洗（保留围栏），输出上限 32768（代码原样 + 注释约为两倍量级），复用 `AiStreamingDialog` 展示 |
| `AiStreamingDialog` | 非模态流式结果对话框：思考过程单行提示 + 只读 HTML 视图（`MarkdownToHtml` 全量重渲，未闭合围栏流式宽容）+ 状态条 + 「复制全文」/「关闭」，主题适配底色 |
| `AiInvocationService` | 统一执行管线 + 通知（LingxiCodeAI 通知组）；按 `feature.maxOutputTokens` 透传输出长度 |
| `AppSettings` | 持久化（`lingxicode-ai.xml`）+ PasswordSafe 密钥（serviceName=LingxiCodeAI, key=providerId） |
| `SettingsConfigurable` | 设置页：厂商/类型/baseUrl/model（可编辑下拉）/API Key + 添加自定义/删除/测试连接 + 语言/diff 上限 |

## 4. 数据流

1. EDT：按钮点击 → 取 `CommitMessage` + `Change[]` → 校验（未配置厂商/密钥 → 警告通知）；
2. 后台（Task.Backgroundable）：读动作内组 diff → 组 prompt → HttpClient 调 `chat/completions`（可取消）；
3. 清洗输出 → `invokeLater` 回填 `CommitMessage.setCommitMessage()`（用户可继续编辑）；
4. 失败经 `onThrowable` → 错误通知（含中文原因）。

### 「代码解释」数据流

1. EDT：右键/快捷键 → `ExplainCodeAction`；或点击 gutter 图标 → `ExplainCodeLineMarkerProvider` 导航处理器。二者分别经 `CodeContextBuilder.build` / `CodeContextBuilder.fromElement` 采集上下文（纯字符串），再并入 `ExplainCodeStarter.trigger`；无目标则警告通知；
2. 后台（Task.Backgroundable，进度「正在解释代码…」）：`ExplainCodeFeature.buildPrompt`（ReadAction 内）→ `client.chat(..., 16384)`（可取消，经 `feature.maxOutputTokens` 透传）→ `cleanMarkdown`（保留 Markdown 结构）；
3. `invokeLater` 回 EDT：`MarkdownToHtml.convert` → `CodeExplainDialog.show()`（非模态，可复制/关闭）；
4. 失败经 `onThrowable` → 复用既有错误通知。

### 「解释代码」入口清单

- 编辑器右键 → `LingxiCode AI` 子菜单（锚定 `EditorPopupMenu` 第一段）→「解释代码」；
- 默认快捷键 `Alt+L`（可在 Keymap 重绑）；
- 编辑器行号旁 gutter 图标（仅类/接口/方法/函数名称标识符上，灰暗配色），单击弹出三功能菜单；
- Find Action（`Ctrl+Shift+A`）搜索「解释代码」（动作注册即自动可搜索，零成本兜底）。

### 「逐行解释」数据流

1. 入口与「解释代码」一致（右键 / 快捷键 `Alt+Shift+L` / gutter 三功能菜单），经 `ExplainLineByLineStarter.trigger` 统一进入管线；空目标复用 `notification.noExplainTarget` 警告；
2. 后台（Task.Backgroundable，进度「正在逐行解释代码…」）：`ExplainLineByLineFeature.buildPrompt`（ReadAction 内）→ `client.chatStreaming(..., 32768)`（流式，增量 150ms 节流推 EDT）→ `cleanFencedCode`（保留单个围栏；流式期间未闭合围栏由 `MarkdownToHtml.appendFencedCode` 天然宽容渲染为代码块）；
3. 完成后 `invokeLater` 回 EDT 定稿渲染：完整代码 + 逐行注释以单个等宽代码块展示于 `AiStreamingDialog`（非模态，可复制/关闭）；
4. 失败降级非流式重试一次，仍失败经 `onThrowable` → 复用既有错误通知。

## 5. 错误处理

- 未配置厂商/密钥 → 警告通知；
- 401「API Key 无效」/ 403「无访问权限」/ 404「接口地址或模型名有误」/ 429「请求过于频繁或额度不足」（附 error.message）；
- 网络/超时/解析失败 → 错误通知含异常摘要；
- 无变更 → 「没有可提交的变更」；
- 单文件内容读取失败 → 降级为文件名清单条目，不中断；
- 无解释目标（无选区且光标未命中符号）→ 「请先选中代码或将光标置于类/方法/函数内」警告，不发起请求；
- 解释输入超长（>20000 字符）→ 截断并标注「内容过长已截断」，仍解释首部。

## 6. 测试策略

- 纯逻辑单测（JUnit 5）：SimpleLineDiff / DiffFormatter / PromptBuilder（含 `buildExplainCode`）/ ResponseCleaner（含 `cleanMarkdown`）/ ChatModels（DTO 序列化，含 `maxTokens` 透传）/ SymbolKindDetector / MarkdownToHtml；
- AI 客户端测试：JDK 内置 HttpServer 模拟端点，验证请求结构/鉴权头/响应解析/错误映射/配置缺失提示、四参 `chat` 的 `max_tokens` 透传与三参缺省 256；
- 平台集成：runIde 沙箱端到端（按钮渲染、右键/快捷键/gutter 图标三种触发、对话框渲染与复制、真实调用、错误提示、gutter 图标与右键行为一致、子菜单置顶）；
- 兼容性：verifyPlugin（2024.2+，含 `LineMarkerProvider` 平台依赖边界）。

## 7. 待验证点（实施期确认）

1. `Vcs.Commit.Message.Toolbar` group id 在 242 中的正确性（备选：查平台 vcs-impl plugin.xml / 旧模态 `Vcs.MessageActionGroup`）；
2. `VcsDataKeys.CHANGES` 在非模态提交上下文的行为（备选：ChangeListManager）；
3. Gson 直接引用是否触发 verifier 报警（备选：implementation 依赖随包分发）；
4. PasswordSafe `CredentialAttributes` 构造签名以实际 SDK 为准。
