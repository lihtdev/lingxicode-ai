# LingxiCode AI（灵犀码）

面向 JetBrains IDE 的 AI 能力集插件：将大模型直接融入日常编码工作流。兼容 2024.2+ 全系列 JetBrains IDE（IDEA / PyCharm / GoLand / WebStorm 等）。

## 功能特性

- **AI 提交信息生成**：在 Git 提交对话框一键根据勾选的代码变更生成 Conventional Commits 格式提交信息，自动回填提交框、可继续编辑；超大变更自动按上限截断
- **AI 代码解释**：选中代码块，或将光标置于类 / 方法 / 函数内，经编辑器右键菜单（快捷键 `Alt+L`）或行号旁 gutter 图标一键生成结构化解释，独立对话框展示、支持复制全文
- **模型管理**：内置 7 家预设厂商 + 任意 OpenAI 兼容自定义端点；支持按量付费 / Coding Plan / Token Plan 套餐类型；一键从端点获取模型列表；状态栏即可快速切换当前模型，无需打开设置
- **界面语言**：设置页切换 中文 / English，保存后即时生效，无需重启 IDE；AI 输出语言与 Diff 最大字符数均可配置
- **隐私与安全**：API Key 经 IntelliJ PasswordSafe 安全存储、不落盘；请求由 IDE 直连你配置的服务商端点，不经第三方中转

## 支持的模型厂商（统一 OpenAI 兼容协议）

| 厂商 | 类型 | baseUrl |
|---|---|---|
| OpenAI | 按量付费 | https://api.openai.com/v1 |
| DeepSeek | 按量付费 | https://api.deepseek.com/v1 |
| GLM (Zhipu) | 按量付费 / Coding Plan | https://open.bigmodel.cn/api/paas/v4 / https://open.bigmodel.cn/api/coding/paas/v4 |
| Kimi (Moonshot) | 按量付费 / Coding Plan | https://api.moonshot.cn/v1 / https://api.kimi.com/coding/v1 |
| MiniMax | 按量付费 / Token Plan | https://api.minimaxi.com/v1 |
| Qwen (Alibaba) | 按量付费 / Token Plan | https://dashscope.aliyuncs.com/compatible-mode/v1 / https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1 |
| Xiaomi MIMO | 按量付费 / Token Plan | https://api.xiaomimimo.com/v1 / https://token-plan-cn.xiaomimimo.com/v1 |
| 自定义 | 按量付费 | 任意 OpenAI 兼容端点 |

- 「类型」仅区分 baseUrl（同一厂商不同套餐使用不同端点），API 协议统一为 OpenAI 兼容格式
- 模型名除预设列表外支持自定义输入，也可从端点一键获取

## 环境要求

- JetBrains IDE 2024.2+（仅依赖平台模块，全系列可用）

## 快速开始

`Settings（设置）→ Tools（工具）→ LingxiCode AI`：

1. 选择厂商与类型（自动带出 baseUrl，可手动修改）；
2. 填写 API Key（可选填模型名、使用预设或从端点获取模型列表）；
3. 点击「测试连接」验证配置；
4. 按需设置界面语言、提交信息输出语言（中文 / English）与 Diff 最大字符数。

## 开发

```bash
# 运行单测
./gradlew test

# 在沙箱 IDE 中调试
./gradlew runIde

# 构建可安装插件包
./gradlew buildPlugin

# 插件兼容性验证
./gradlew verifyPlugin
```

技术栈：Kotlin + IntelliJ Platform Gradle Plugin 2.x（目标 2024.2+，sinceBuild 242），JVM toolchain 21。

## 架构

详见 [docs/design.md](docs/design.md)。

```
action（提交框按钮 / 右键功能组 / gutter 图标 / 状态栏）
  → feature（AiFeature 扩展点抽象，当前实现：CommitMessageFeature、代码解释）
    → service（AiInvocationService 统一执行管线：后台任务 / 通知 / 错误处理）
      → ai（OpenAiCompatClient，OpenAI 兼容协议唯一实现）
        → settings（AppSettings 持久化 + PasswordSafe 密钥）
```

新 AI 功能经 plugin.xml 的 `lingxicode.aiFeature` 扩展点注册（实现 `buildPrompt` / `handleResult`），执行层与 AI 层无需改动。

## 许可

[Apache License 2.0](LICENSE) · Copyright 2026 lihaitao

第三方声明：`src/main/resources/icons/github.svg`、`user.svg` 图标来自 [IntelliJ Community](https://github.com/JetBrains/intellij-community)（JetBrains s.r.o.，Apache 2.0）。
