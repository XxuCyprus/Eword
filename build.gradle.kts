// Eword 根项目构建脚本：只声明插件版本，具体配置见 app/build.gradle.kts

plugins {
    // Android 应用插件
    id("com.android.application") version "8.13.2" apply false
    // Kotlin Android 插件
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    // Jetpack Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
