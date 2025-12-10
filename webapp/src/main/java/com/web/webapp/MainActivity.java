package com.web.webapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private WebView webView;
    private JSONObject appConfig;
    private SharedPreferences prefs;
    private FullWebChromeClient webChromeClient;

    // 权限请求码
    private static final int PERMISSION_REQUEST_CODE = 100;

    // 文件选择器请求码
    private static final int FILE_CHOOSER_REQUEST_CODE = 1;

    // WebApp 接口实例
    private WebAppInterface webAppInterface;

    // 配置缓存键
    private static final String CONFIG_CACHE_KEY = "last_app_config";
    private static final String PREF_NAME = "webapp_config";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化配置缓存
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // 读取应用配置
        try {
            loadAppConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 检查并请求权限
        checkAndRequestPermissions();

        // 应用配置（状态栏、方向等）
        applyConfig();

        // 创建 WebView
        webView = new WebView(this);
        configureWebView();

        // 加载网页
        loadWebContent();

        setContentView(webView);
    }

    /**
     * 读取 webapp.json 配置
     */
    private void loadAppConfig() throws Exception {
        try {
            // 尝试从 assets 读取
            InputStream is = getAssets().open("webapp.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            // 解析 JSON
            String configJson = sb.toString();
            appConfig = new JSONObject(configJson);

            // 缓存到 SharedPreferences
            prefs.edit().putString(CONFIG_CACHE_KEY, configJson).apply();

        } catch (Exception e) {
            // 如果 assets 中没有，尝试从缓存读取
            try {
                String cachedConfig = prefs.getString(CONFIG_CACHE_KEY, null);
                if (cachedConfig != null) {
                    appConfig = new JSONObject(cachedConfig);
                } else {
                    // 创建默认配置
                    appConfig = createDefaultConfig();
                }
            } catch (Exception ex) {
                appConfig = createDefaultConfig();
            }
        }
    }

    /**
     * 创建默认配置
     */
    private JSONObject createDefaultConfig() throws Exception {
        String defaultConfig = "{" +
                "\"name\": \"WebApp\"," +
                "\"package\": \"com.example.webapp\"," +
                "\"orientation\": \"portrait\"," +
                "\"fullscreen\": false," +
                "\"statusBar\": {" +
                "    \"backgroundColor\": \"#FFFFFF\"," +
                "    \"style\": \"dark\"," +
                "    \"translucent\": false," +
                "    \"hidden\": false" +
                "}," +
                "\"webview\": {" +
                "    \"zoomEnabled\": false," +
                "    \"javascriptEnabled\": true," +
                "    \"domStorageEnabled\": true," +
                "    \"allowFileAccess\": true," +
                "    \"textZoom\": 100," +
                "    \"userAgent\": \"\"" +
                "}," +
                "\"permissions\": [\"android.permission.INTERNET\"]" +
                "}";
        return new JSONObject(defaultConfig);
    }

    /**
     * 检查并请求权限
     */
    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // 摄像头权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }

        // 录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }

        // 存储权限（Android 11+ 需要不同处理）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // 位置权限（如果需要）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // 电话权限（如果需要）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE);
        }

        // 如果有权限需要请求
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 将权限结果传递给 WebAppInterface
            if (webAppInterface != null) {
                webAppInterface.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }

            // 检查是否所有权限都被授予
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                // 有些权限被拒绝，可以在这里提示用户
                // 例如：显示一个对话框解释为什么需要这些权限
            }
        }
    }

    /**
     * 应用配置到 Activity
     */
    private void applyConfig() {
        try {
            // 设置屏幕方向
            String orientation = appConfig.optString("orientation", "portrait");
            switch (orientation) {
                case "landscape":
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    break;
                case "portrait":
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    break;
                case "sensor":
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    break;
                default:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }

            // 设置全屏
            boolean fullscreen = appConfig.optBoolean("fullscreen", false);
            if (fullscreen) {
                requestWindowFeature(Window.FEATURE_NO_TITLE);
                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                );
            }

            // 应用状态栏配置
            applyStatusBarConfig();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 应用状态栏配置
     */
    private void applyStatusBarConfig() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                JSONObject statusBar = appConfig.optJSONObject("statusBar");
                if (statusBar != null) {
                    Window window = getWindow();

                    // 隐藏状态栏
                    if (statusBar.optBoolean("hidden", false)) {
                        window.getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        );
                        return;
                    }

                    // 显示状态栏
                    window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

                    // 设置状态栏颜色
                    String colorStr = statusBar.optString("backgroundColor", "#FFFFFF");
                    if (colorStr.startsWith("#")) {
                        try {
                            int color = Color.parseColor(colorStr);
                            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                            window.setStatusBarColor(color);
                        } catch (Exception e) {
                            // 颜色解析失败，使用默认
                        }
                    }

                    // 设置状态栏文字颜色
                    String style = statusBar.optString("style", "dark");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        View decorView = window.getDecorView();
                        int systemUiVisibility = decorView.getSystemUiVisibility();
                        if ("light".equals(style)) {
                            // 浅色文字（深色背景）
                            systemUiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        } else {
                            // 深色文字（浅色背景）
                            systemUiVisibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        }
                        decorView.setSystemUiVisibility(systemUiVisibility);
                    }

                    // 设置透明状态栏
                    if (statusBar.optBoolean("translucent", false)) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 配置 WebView
     */
    private void configureWebView() {
        WebSettings settings = webView.getSettings();

        // 基础设置
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 应用 webview 配置
        applyWebViewConfig(settings);

        // 创建并注入 JavaScript 接口
        webAppInterface = new WebAppInterface(this, webView);
        webView.addJavascriptInterface(webAppInterface, "Android");

        // 设置 WebViewClient 和 WebChromeClient
        webView.setWebViewClient(new LocalContentWebViewClient());
        webChromeClient = new FullWebChromeClient();
        webChromeClient.setActivity(this);
        webView.setWebChromeClient(webChromeClient);

        // 开启调试（仅调试模式）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    /**
     * 应用 webview 配置
     */
    private void applyWebViewConfig(WebSettings settings) {
        try {
            JSONObject webviewConfig = appConfig.optJSONObject("webview");
            if (webviewConfig != null) {
                // 缩放
                boolean zoomEnabled = webviewConfig.optBoolean("zoomEnabled", false);
                settings.setSupportZoom(zoomEnabled);
                settings.setBuiltInZoomControls(zoomEnabled);
                settings.setDisplayZoomControls(false);

                // 文本缩放
                int textZoom = webviewConfig.optInt("textZoom", 100);
                settings.setTextZoom(textZoom);

                // 用户代理
                String userAgent = webviewConfig.optString("userAgent", "");
                if (!userAgent.isEmpty()) {
                    settings.setUserAgentString(userAgent);
                }

                // 其他布尔设置
                settings.setJavaScriptEnabled(webviewConfig.optBoolean("javascriptEnabled", true));
                settings.setDomStorageEnabled(webviewConfig.optBoolean("domStorageEnabled", true));
                settings.setAllowFileAccess(webviewConfig.optBoolean("allowFileAccess", true));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载网页内容
     */
    private void loadWebContent() {
        try {
            // 获取入口 URL
            String targetUrl = getTargetUrl();
            webView.loadUrl(targetUrl);
        } catch (Exception e) {
            e.printStackTrace();
            // 加载本地默认页面
            webView.loadUrl("http://localhost/index.html");
        }
    }

    /**
     * 获取目标 URL
     */
    private String getTargetUrl() {
        try {
            // 优先级：targetUrl > url > entry > 默认 index.html
            String targetUrl = appConfig.optString("targetUrl", "");
            if (targetUrl.isEmpty()) {
                targetUrl = appConfig.optString("url", "");
            }
            if (targetUrl.isEmpty()) {
                targetUrl = appConfig.optString("entry", "");
            }

            if (!targetUrl.isEmpty()) {
                // 如果是 http/https 链接，直接使用
                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    return targetUrl;
                }
                // 本地文件路径
                return "http://localhost/" + targetUrl;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 默认入口
        return "http://localhost/index.html";
    }

    /**
     * 本地内容 WebViewClient
     */
    private class LocalContentWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url != null && "localhost".equalsIgnoreCase(url.getHost())) {
                try {
                    String path = url.getPath();
                    if (path == null || path.equals("/") || path.equals("")) {
                        path = "index.html";
                    }
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }

                    // 尝试从 assets 读取文件
                    InputStream stream = getAssets().open(path);

                    // 根据文件扩展名设置 MIME 类型
                    String mimeType = getMimeType(path);

                    return new WebResourceResponse(mimeType, "UTF-8", stream);
                } catch (IOException e) {
                    // 文件不存在，返回 404
                    return null;
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // 处理特殊协议
            if (url.startsWith("tel:") || url.startsWith("mailto:") ||
                    url.startsWith("sms:") || url.startsWith("geo:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            // 允许 WebView 加载其他 URL
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // 页面加载完成后，可以注入一些初始化脚本
        }
    }

    /**
     * 获取 MIME 类型
     */
    private String getMimeType(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".mp3")) return "audio/mpeg";
        if (path.endsWith(".mp4")) return "video/mp4";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".ttf")) return "font/ttf";
        if (path.endsWith(".xml")) return "text/xml";
        return "text/plain";
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 配置变化时重新应用状态栏设置
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            applyStatusBarConfig();
        }, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (webChromeClient != null) {
            webChromeClient.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }

        if (webAppInterface != null) {
            // 清理 WebAppInterface 资源
            try {
                webAppInterface.finalize();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}