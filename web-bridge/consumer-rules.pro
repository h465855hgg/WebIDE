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