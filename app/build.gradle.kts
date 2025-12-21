plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
}


android {
    namespace = "com.web.webide"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.web.webide"
        minSdk = 29
        targetSdk = 36
        versionCode = 18
        versionName = "0.1.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
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
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(project(":web-bridge"))
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
    implementation(libs.volley)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}