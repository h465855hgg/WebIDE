plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
}

val copyWebAppApk = tasks.register<Copy>("copyWebAppApk") {
    // 显式依赖 webapp 的构建任务
    dependsOn(":webapp:assembleRelease")

    // 设置输入源：webapp 的输出目录
    // 注意：这里使用 provider 机制，确保路径在执行时才解析
    from(project(":webapp").layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        // 如果你需要确定具体名字，可以用 rename
        // rename { "webapp.apk" }
        // 或者保留原名，或者像你之前那样重命名
        rename { _ -> "webapp.apk" }
    }

    // 设置输出目标：app 模块的 build 目录 (不要污染 src 目录)
    into(layout.buildDirectory.dir("generated/assets/webapp"))
}

android {
    namespace = "com.web.webide"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.web.webide"
        minSdk = 29
        targetSdk = 36
        versionCode = 14
        versionName = "0.1.4"
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
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-beta"

            signingConfig = signingConfigs.getByName("release")

        }

        release {
           // applicationIdSuffix = ".release"


            isMinifyEnabled = true
            isShrinkResources = true // 资源缩减

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

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
    }

    sourceSets {
        getByName("main") {
            // 关键点：将 copyWebAppApk 任务作为目录源添加进去
            // Gradle 会自动识别：在打包 Assets 之前，必须先运行 copyWebAppApk 任务
            assets.srcDir(copyWebAppApk)
        }
    }
}

android.applicationVariants.configureEach {
    outputs.configureEach {
        val appName = "WebIDE"
        val buildType = buildType.name
        val ver = versionName
        (this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl)?.let {
            it.outputFileName = "${appName}-${ver}-${buildType}.apk"
        }
    }
}

aboutLibraries() {
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
    implementation(libs.accompanist.navigation.animation)

    implementation(libs.aboutlibraries.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.editor)
    implementation(libs.language.textmate)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // DataStore dependencies
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    implementation(files("libs/xml.jar"))


    implementation(project(":signer"))


    implementation(libs.zipalign.java)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}