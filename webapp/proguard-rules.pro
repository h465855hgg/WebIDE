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
# ==========================================
# BeanShell (bsh) 桌面端/服务端兼容规则
# ==========================================

# 1. 忽略桌面端 UI 库 (AWT 和 Swing)
# BeanShell 自带了一个桌面调试控制台，但在手机上我们要么用不到，要么用 JS 模拟
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn java.applet.**

# 2. 忽略服务端和标准脚本接口
# 安卓没有 Servlet 和 javax.script.*
-dontwarn javax.servlet.**
-dontwarn javax.script.**

# 3. 忽略旧版脚本框架 (BSF)
-dontwarn org.apache.bsf.**

# 4. 保持 BeanShell 核心代码不被移除/混淆
# BeanShell 极其依赖反射，混淆会导致“找不到构造函数”等运行时错误
-keep class bsh.** { *; }