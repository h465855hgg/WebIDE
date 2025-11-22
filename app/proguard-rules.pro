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


# 1. Kotlin 标准库 ----------------------------------------------------------
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }

# 2. kotlin.Cloneable 相关
-keep interface kotlin.Cloneable { *; }
-keep class kotlin.Cloneable$DefaultImpls { *; }

# 3. Rosemoe Editor / TextMate / tm4e --------------------------------------
-keep class io.github.rosemoe.sora.** { *; }
-keep class org.eclipse.tm4e.core.** { *; }
-keep class org.eclipse.tm4e.languageconfiguration.** { *; }

# 4. Gson 反射需要 ---------------------------------------------------------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    <init>();
    <fields>;
}

# 5. Ensure tm4e grammar/theme implementations are kept
-keep class * implements org.eclipse.tm4e.core.grammar.IGrammar { *; }
-keepclassmembers class * implements org.eclipse.tm4e.core.theme.IThemeSource { *; }

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
-dontwarn io.github.rosemoe.oniguruma.**
-dontwarn org.eclipse.tm4e.core.internal.oniguruma.impl.onig.**

# Optionally keep the NativeOnigConfig if runtime reflection/availability checks must remain:
-keep class org.eclipse.tm4e.core.internal.oniguruma.impl.onig.NativeOnigConfig { *; }

# If R8 still generates missing_rules.txt in
# app/build/outputs/mapping/release/missing_rules.txt
# inspect that file and copy the suggested keep rules into this file.
#
# -------------------------------------------------------------------------
# Additional miscellaneous keeps you had at the bottom of the original file:
-keep class * implements org.eclipse.tm4e.core.grammar.IGrammar { *; }
-keepclassmembers class * implements org.eclipse.tm4e.core.theme.IThemeSource { *; }