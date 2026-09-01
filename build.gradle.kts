// LingxiCode AI（灵犀码）— JetBrains IDE 插件构建脚本
// 技术栈：Kotlin + IntelliJ Platform Gradle Plugin 2.x，目标 2024.2+（sinceBuild 242）
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.lihtdev.lingxicode"
version = "0.1.2"

repositories {
    // 阿里云公共镜像（聚合了 central + jcenter）
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    // 谷歌仓库镜像（Android 项目需要）
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    // gradle-plugin 镜像（plugins.gradle.org）
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    // 保留官方源兜底（镜像缺失时回源）
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 以 IntelliJ IDEA Community 2024.2 作为编译目标平台（仅依赖 platform 模块，全系列 IDE 可用）
        create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2")
        pluginVerifier()
        zipSigner()
    }
    // 单测依赖（主代码的 Gson 由平台提供；单测环境需自行引入）
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

kotlin {
    // 2024.2 运行于 JBR 21
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        id = "com.lihtdev.lingxicode"
        name = "LingxiCode AI"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    // 发布到 JetBrains Marketplace：令牌经 gradle 属性/环境变量注入（存于 ~/.gradle/gradle.properties，不入库）；
    // 未配置时仅 publishPlugin 任务不可用，日常 buildPlugin/test 不受影响。
    publishing {
        token = providers.gradleProperty("jetbrainsToken")
            .orElse(providers.environmentVariable("JETBRAINS_TOKEN"))
        channels = listOf("default")
    }
}

tasks {
    // 插件包 PGP 签名：证书链与私钥均为仓库外文件（路径经 gradle 属性传入），
    // 密钥文件位于 ~/.gradle/lingxicode-signing/。属性未配置时给空值，
    // signPlugin 会走「自动生成自签名证书并打印到控制台」的首次流程。
    val signingChain = providers.gradleProperty("signingCertificateChainFile")
        .map { file(it).readText() }
        .orElse("")
    val keyPropName = "signingPrivateKeyFile"
    val signingKey = providers.gradleProperty(keyPropName)
        .map { file(it).readText() }
        .orElse("")
    val signingPassword = providers.gradleProperty("signingPassword")

    signPlugin {
        certificateChain = signingChain
        `privateKey`.set(signingKey)
        password = signingPassword
    }
}
