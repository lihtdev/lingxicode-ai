# LingxiCode AI（灵犀码）

面向 JetBrains IDE 的 AI 能力集插件：让大模型根据代码变更为你生成高质量的 Conventional Commits 提交信息。

## 功能

### AI 提交信息生成（v1）

- 在 Git 提交对话框消息区 toolbar 点击「AI 生成提交信息」按钮；
- 插件读取当前勾选的变更文件，生成 diff（自动截断超大文件），调用大模型；
- 生成的 `type: 描述` 格式提交信息自动填入提交消息框，可继续编辑后提交。

### 支持的模型厂商（统一 OpenAI 兼容协议）

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

- 「类型」仅区分 baseUrl（同一厂商不同套餐使用不同端点），API 协议统一为 OpenAI 兼容格式；
- 模型名除预设列表外支持自定义输入；
- API Key 经 IntelliJ PasswordSafe 安全存储，不落盘。

## 配置

`Settings（设置）→ Tools（工具）→ LingxiCode AI`：

1. 选择厂商与类型（自动带出 baseUrl，可手动修改）；
2. 填写 API Key（可选填模型名或使用预设）；
3. 点击「测试连接」验证配置；
4. 提交信息语言默认中文，可切换 English；可调整 Diff 最大字符数。

## 环境要求

- JetBrains IDE 2024.2+（IDEA / PyCharm / GoLand / WebStorm 等全系列）

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

技术栈：Kotlin + Gradle IntelliJ Platform Gradle Plugin 2.x（目标 2024.2+，sinceBuild 242）。

## 架构

详见 [docs/design.md](docs/design.md)。

```
action（提交框按钮/右键功能组）
  → feature（AiFeature 抽象，v1: CommitMessageFeature）
    → service（AiInvocationService 统一执行管线：后台任务/通知/错误处理）
      → ai（OpenAiCompatClient，OpenAI 兼容协议）
        → settings（PersistentStateComponent + PasswordSafe）
```

后续规划：基于 `lingxicode.aiFeature` 扩展点接入「AI 代码解释」等功能。

## 许可

私有项目，版权所有。
