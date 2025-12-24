/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    implementation(project(":web-bridge"))
        // 添加 OkHttp 用于原生网络请求
        implementation(libs.okhttp)
        // 添加 Gson 用于 JSON 序列化（可选，但强烈建议，用于处理复杂数据）
        implementation(libs.gson)
    implementation(libs.appcompat)

}