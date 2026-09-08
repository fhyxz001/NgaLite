import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 签名信息：CI 从环境变量读取，本地从 keystore.properties 读取（该文件已 gitignore，不入库）
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.ngalite.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ngalite.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 57
        versionName = "1.97"
    }

    signingConfigs {
        create("release") {
            // 本地未配置密钥时跳过（CI 始终通过环境变量提供），避免配置阶段因空路径失败
            val keystoreFile = System.getenv("KEYSTORE_FILE") ?: signingProps.getProperty("keystore.file")
            if (!keystoreFile.isNullOrBlank()) {
                storeFile = rootProject.file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: signingProps.getProperty("keystore.password")
                keyAlias = System.getenv("KEY_ALIAS") ?: signingProps.getProperty("keystore.alias")
                keyPassword = System.getenv("KEY_PASSWORD") ?: signingProps.getProperty("keystore.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

base {
    archivesName = "NgaLite-v${android.defaultConfig.versionName}"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
