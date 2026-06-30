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
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
}


android {
    namespace = "com.web.webide"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.web.webide"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 38
        versionName = "0.3.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 编译时间戳：每次构建时写入当前毫秒时间，供“关于”页展示构建时间。
        // 用 Long 类型避免地区化问题，UI 端再格式化为本地时间字符串。
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }
    // 按架构分离：每个 flavor 的 APK 只含对应架构的 rootfs（48MB），而非两个都打包（96MB）
    flavorDimensions += "arch"
    productFlavors {
        create("arm64-v8a") {
            //noinspection ChromeOsAbiSupport
            ndk { abiFilters += "arm64-v8a" }
        }
        create("armeabi-v7a") {
            //noinspection ChromeOsAbiSupport
            ndk { abiFilters += "armeabi-v7a" }
        }
    }
    // flavor 使用标准 ABI 名（arm64-v8a / armeabi-v7a），但架构相关资源（rootfs 等）
    // 仍按历史约定放在 src/arm64/、src/arm32/（与 build-rootfs.sh 输出路径一致）。
    // 显式映射源集，确保各 flavor 能正确打包对应架构的 rootfs。
    // 否则 AGP 只会查找 src/<flavorName>/（即 src/arm64-v8a/），导致 rootfs 漏打入 APK。
    sourceSets {
        getByName("arm64-v8a") {
            assets.srcDir("src/arm64/assets")
        }
        getByName("armeabi-v7a") {
            assets.srcDir("src/arm32/assets")
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file("WebIDE.jks")
            keyAlias = "WebIDE"
            storePassword = "WebIDE"
            keyPassword = "WebIDE"
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
           // versionNameSuffix = "-beta-debug"

            signingConfig = signingConfigs.getByName("release")

        }

        release {
           // applicationIdSuffix = ".release"
           // versionNameSuffix = "-release"//-Preview

            isMinifyEnabled = true
            isShrinkResources = true // 资源缩减

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

        }
    }
    packaging {
        resources {
            // 2. 排除 LSP4J 和其他库可能产生的冲突文件
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/INDEX.LIST"
        }
    }
    packaging {
        resources {
            // 排除导致冲突的 JGit 配置文件（Eclipse OSGi 插件元数据，Android 运行时不使用）
            excludes += "OSGI-INF/l10n/plugin.properties"
            excludes += "plugin.properties"

            // 如果后续还有类似冲突，通常也是 META-INF 下的文件，比如：
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
// 🔥🔥🔥添加jniLibs
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    // 🔥🔥🔥添加jniLibs

    // 🔥🔥🔥不压缩bin
    androidResources {
        noCompress += listOf("bin", "proot", "so", "2")
    }
    // 🔥🔥🔥不压缩bin
}

android.applicationVariants.configureEach {
    outputs.configureEach {
        val appName = "WebIDE"
        val buildType = buildType.name
        val flavor = flavorName
        val ver = versionName
        (this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl)?.let {
            it.outputFileName = "${appName}-${ver}-${flavor}-${buildType}.apk"
        }
    }
}

aboutLibraries {
    collect {
        fetchRemoteLicense = true
    }
    export {
        prettyPrint = true
        outputFile = file("src/main/res/raw/aboutlibraries.json")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.material.kolor)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(project(":web-bridge"))
    implementation(libs.accompanist.navigation.animation)
    implementation(libs.compose.icons.simple)
    implementation(libs.compose.icons.font.awesome)

    implementation(libs.aboutlibraries.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.code)
    implementation(libs.multiplatform.markdown.renderer.coil2)
    implementation(libs.highlights)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    // git依赖
    // Source: https://mvnrepository.com/artifact/org.eclipse.jgit/org.eclipse.jgit
    implementation(libs.org.eclipse.jgit)
    // Source: https://mvnrepository.com/artifact/org.eclipse.jgit/org.eclipse.jgit.ssh.apache
    implementation(libs.org.eclipse.jgit.ssh.apache) {
        // sshd-osgi 是把 sshd-core + sshd-common 重新打包的 OSGi fat bundle，
        // 在 Android（非 OSGi）上会与分离的 core/common 构件产生重复类和重复资源
        //（如 org/apache/sshd/common/kex/group15.prime）。排除它，改用下面的分离构件。
        exclude(group = "org.apache.sshd", module = "sshd-osgi")
    }
    //noinspection UseTomlInstead
    implementation("org.slf4j:slf4j-simple:2.0.17")
    // JGit 5.13.3 传递依赖 Apache MINA SSHD 2.7.0，但 2.7.0 缺少
    // PathUtils.setUserHomeFolderResolver()（自 2.10.0 引入），该方法是 Android 上
    // 重定向 SSH 用户主目录、避免 "No user home folder available" 崩溃的关键。
    // 显式引入分离的 SSHD 2.10.0 构件（Java 8 字节码，不会引入 readNBytes），
    // 替代被排除的 sshd-osgi fat bundle（sshd-osgi 不含独有类，仅聚合 core+common）。
    implementation(libs.apache.sshd.common)
    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)

    // 🔥🔥🔥添加终端依赖
    implementation(project(":core:main"))
    // LSP 支持
    implementation(project(":editor-lsp"))
    implementation(libs.lsp4j)
    implementation(libs.androidx.compose.foundation.layout)

    //脱唐
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    //TreeSitter语言包
    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.json)

    // Media3 (Video Player)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Editor
    implementation(project(":editor"))
    implementation(project(":language-treesitter"))
    implementation(libs.language.textmate)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // DataStore dependencies
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    implementation(files("libs/xml.jar"))


    implementation(project(":signer"))

    implementation(libs.zipalign.java)

    // i18n 字符串资源（独立模块，便于社区贡献更多语言翻译）
    implementation(project(":i18n"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.volley)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
