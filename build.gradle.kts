// CodeSense AI（灵犀码）— JetBrains IDE 插件构建脚本
// 技术栈：Kotlin + IntelliJ Platform Gradle Plugin 2.x，目标 2024.1+（sinceBuild 241）
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.5.2"
}

group = "com.lihtdev.codesense"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 以 IntelliJ IDEA Community 2024.1 作为编译目标平台（仅依赖 platform 模块，全系列 IDE 可用）
        create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.1")
        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }
    // 单测依赖（主代码的 Gson 由平台提供；单测环境需自行引入）
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

kotlin {
    // 2024.1 运行于 JBR 17
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        id = "com.lihtdev.codesense"
        name = "CodeSense AI"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
}
