# LingxiCode AI（灵犀码）发布指引

> 面向 JetBrains Marketplace（https://plugins.jetbrains.com）的完整发布流程。
> 项目侧配置已就绪（见 `build.gradle.kts` 的 `publishing` 与 `signPlugin` 块），只需完成下述账号与密钥准备。

## 前置条件

1. **JetBrains 账号**：在 https://account.jetbrains.com 注册（免费）；已有账号则跳过。
2. **网络**：确认浏览器与 Gradle 均可访问 `plugins.jetbrains.com`（本机构建已验证 JetBrains CDN 可达）。
3. **市场查重**：在 https://plugins.jetbrains.com 搜索 "LingxiCode" / "lingxi"，确认无重名插件（本项目因市场已有 "CodeSense AI" 而改名，发布前务必再查一次）。

## 第一步：生成上传令牌（Token）

1. 登录 https://plugins.jetbrains.com；
2. 点击右上角头像 → **Profile**（或直接访问 https://plugins.jetbrains.com/author/profile/tokens）；
3. **Tokens** 页签 → **Create token**，生成**永久令牌**（永久令牌不过期，适合长期发版）；
4. 复制令牌妥善保存（只显示一次）。

## 第二步：生成插件签名密钥（PGP）

首次运行签名任务，会自动生成一对自签名证书并**打印到控制台**：

```bash
./gradlew :signPlugin
```

按控制台提示，把输出中的两部分分别保存为文件（目录可自建）：

- **Certificate chain** → `C:\Users\lihaitao\.gradle\lingxicode-signing\chain.crt`
- **Private key** → `C:\Users\lihaitao\.gradle\lingxicode-signing\private.pem`

> 密钥丢失则无法给同一插件发布新版本（市场校验签名一致性），请把这两个文件连同密码一起备份到安全位置。
> 生成/保存后再次运行 `./gradlew :signPlugin` 验证签名流程正常。

## 第三步：填写用户级机密（仓库外）

编辑 `C:\Users\lihaitao\.gradle\gradle.properties`（不存在则新建；该文件在仓库外，**不会被提交**）：

```properties
# JetBrains Marketplace 上传令牌
jetbrainsToken=<第一步生成的令牌>
# 签名密钥文件路径（正斜杠）
signingCertificateChainFile=C:/Users/lihaitao/.gradle/lingxicode-signing/chain.crt
signingPrivateKeyFile=C:/Users/lihaitao/.gradle/lingxicode-signing/private.pem
# 生成密钥时设置的密码
signingPassword=<密码>
```

## 第四步：发布

```bash
./gradlew publishPlugin
```

该任务自动串联 `buildPlugin → signPlugin → 上传`，默认通道 `default`（正式版）。
上传成功后，在 https://plugins.jetbrains.com/author/ 的插件列表中可见。

## 第五步：等待审核

- 首次发布**必经 JetBrains 人工审核**，通常 **1–3 个工作日**；
- 审核期间市场页面显示 Pending 状态；通过后插件公开可见；
- 审核反馈（如需修改）会发送到 marketplace 账号邮箱与 `plugin.xml` 中 vendor 的 email。

## 发布后完善（市场 Web 端编辑）

- **标签（Tags）**：如 AI、Git、Commit Message；
- **截图 / GIF**：建议放提交框按钮、代码解释对话框的实际截图；
- **Overview（README）**：市场页面在线编辑，可复用仓库 README；
- **Support 链接**：issues 地址等。

## 后续版本更新

1. `build.gradle.kts` 中 `version` 递增（如 `0.1.0` → `0.1.1`）；
2. 重新执行 `./gradlew publishPlugin`；
3. 更新版同样进入审核（后续版本审核通常快于首版）。

## 备选路径：手动上传

若 `publishPlugin` 上传失败（网络等原因），可改用 Web 端手动提交：

1. `./gradlew buildPlugin` 构建并确认产物 `build/distributions/lingxicode-ai-<版本>.zip`（已签名）；
2. https://plugins.jetbrains.com → **Upload plugin** → 选择 zip → 填写更新说明（首版需填写插件简介与许可信息：选 **Apache 2.0**，与仓库 LICENSE 一致）；
3. 同样进入审核流程。

## 常见问题

| 问题 | 处理 |
|---|---|
| 上传报 401 / token 无效 | 检查 `~/.gradle/gradle.properties` 的 `jetbrainsToken` 是否最新（令牌只在创建时显示一次） |
| 上传报插件签名不符 | 插件首次发布后签名密钥即绑定，必须用同一对密钥签名（见第二步的备份提醒） |
| 市场提示插件 id 已存在 | 插件 id `com.lihtdev.lingxicode` 与他人冲突，需再换 id（改 `build.gradle.kts` 与 `plugin.xml` 两处） |
| 下载依赖超时 | 本机直连 Maven Central 超时，走阿里云镜像（`~/.gradle/init.d/mirror.gradle` 与 `build.gradle.kts` 均已配置） |
