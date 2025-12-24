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
# BeanShell (bsh) 防混淆规则
# ==========================================
# BeanShell 极其依赖反射，必须完全保留，不能改名
-keep class bsh.** { *; }

# 如果你的脚本里会用到某些 Java 标准库的特殊类，也可以加上这个(一般 bsh 不需要)
#-keep class java.lang.reflect.** { *; }

# ==========================================
# 你的接口类也需要防混淆
# ==========================================
# 因为你在脚本里写了 adapter.openFlashLight()
# 如果 Adapter 被混淆成 a.b()，脚本就找不到 openFlashLight 这个方法名了
-keep class rrzt.web.web_bridge.WebsApiAdapter {
    public <methods>;
}

# 如果你还在脚本里用了 context 或 webView 的特定方法，
# 通常 Android 原生类不需要我们操心，但如果你有自定义 View，也要 keep 住

# ==============================================
# BeanShell (bsh) Android 兼容性规则 (补充)
# ==============================================

# 警告：BeanShell 包含很多 Java GUI (AWT/Swing) 和 Web (Servlet) 类，Android 没有
# 必须忽略这些不存在的类，否则 R8 会报错
-dontwarn java.applet.**
-dontwarn java.awt.**
-dontwarn java.swing.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn javax.servlet.**
-dontwarn org.apache.bsf.**

# 忽略 BeanShell 尝试适配的 Java Scripting API
-dontwarn javax.script.**

# 再次确保 BeanShell 核心类不被混淆
-keep class bsh.** { *; }
-keep class org.apache.bsf.** { *; } # 如果使用 BeanShellBSFEngine