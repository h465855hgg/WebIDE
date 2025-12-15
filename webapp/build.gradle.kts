plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.web.webapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.web.webapp"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = file("WebIDE.jks")
            keyAlias = "WebIDE"
            storePassword = "WebIDE"
            keyPassword = "WebIDE"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName("release")

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {

        // 添加 OkHttp 用于原生网络请求
        implementation(libs.okhttp)
        // 添加 Gson 用于 JSON 序列化（可选，但强烈建议，用于处理复杂数据）
        implementation(libs.gson)
    implementation(libs.appcompat)

}