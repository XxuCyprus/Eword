import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 发布签名口令放在 local.properties（不入库），与同级 Snote 工程保持一致的做法
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.eword.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eword.app"
        // 与同级 Snote 保持一致：数据落公共目录需要 API 30+
        minSdk = 30
        targetSdk = 34
        versionCode = 6
        versionName = "4.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("eword-release.jks")
            storePassword = localProperties.getProperty("storePassword")
            keyAlias = "Eword"
            keyPassword = localProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 音频与资源包不压缩，便于直接以文件描述符播放
    androidResources {
        noCompress += listOf("mp3", "ewp")
    }

    // 产物命名与同级 Snote 保持一致，便于直接分发
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "Eword-v${versionName}.apk"
        }
    }
}

dependencies {
    // ========== Jetpack Compose ==========
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ========== Android 核心库 ==========
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")

    // ========== JSON ==========
    implementation("com.google.code.gson:gson:2.11.0")
}
