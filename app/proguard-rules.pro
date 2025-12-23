# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile




# -------------------------------------------------------------------------
# R8 error seen during release build:
#   Missing class io.github.rosemoe.oniguruma.OnigNative
# This can be handled in two ways (choose one):
#
# A) If you DO include the oniguruma/native implementation in your app:
#    - Add the correct dependency (or the native .so files) so the class exists at R8 time.
#      e.g. add the library that provides io.github.rosemoe.oniguruma.* to your Gradle dependencies
#      and ensure native libs are packaged into the release APK.
#
# B) If oniguruma is optional at runtime (the code checks availability and can work without it),
#    tell R8 to ignore the missing classes so the shrinker can continue:
#
#    The -dontwarn lines below silence the missing-class error for the package(s) in question.
#    If you prefer stricter shrinking, add only the -dontwarn for the specific class referenced.
#


# If R8 still generates missing_rules.txt in
# app/build/outputs/mapping/release/missing_rules.txt
# inspect that file and copy the suggested keep rules into this file.
#
# -------------------------------------------------------------------------


# =========================================================================
# WebIDE 核心构建功能混淆白名单 (修复 Release 包签名失败问题)
# =========================================================================

# 1. 保护 APK 签名库 (核心)
# 签名库内部大量使用了反射和动态加载，绝对不能混淆
-keep class com.mcal.apksigner.** { *; }
-keep class com.android.apksig.** { *; }

# 2. 保护加密库 (Bouncy Castle)
# 签名必须用到 SHA256withRSA 等算法，混淆会导致找不到 Provider
-keep class org.bouncycastle.** { *; }
-keep class org.spongycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**

# 3. 保护 AXML 解析与修改库 (用于修改 Manifest)
# 你的 ApkXmlEditor 依赖这些类
-keep class com.Day.Studio.** { *; }
-keep class com.Day.Studio.Function.** { *; }
-keep class com.Day.Studio.Function.axmleditor.** { *; }

# 4. 保护你自己的构建工具类
# 防止 ZipAligner 或 ApkBuilder 的方法被误删或改名
-keep class com.web.webide.build.** { *; }

# 5. 保护 Android 资源压缩逻辑 (防止 assets 里的模板被误删)
# 如果你在 assets 里放了 webapp_1.0.apk，这行能防止它被 shrinkResources 误删
-keepclassmembers class ** {
    *** getAssets(...);
}

# 6. 忽略签名库可能产生的警告
# 有些签名库引用了 Android 系统内部类，忽略警告以保证编译通过
-dontwarn com.android.apksig.**
-dontwarn java.nio.file.**