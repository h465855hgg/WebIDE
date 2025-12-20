package com.web.webapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private WebView webView;
    private JSONObject appConfig;
    private SharedPreferences prefs;

    // 专门的文件选择/全屏处理 ChromeClient
    private FullWebChromeClient webChromeClient;

    // JS 接口
    private WebAppInterface webAppInterface;

    // 常量
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_CHOOSER_REQUEST_CODE = 101;
    private static final String CONFIG_CACHE_KEY = "last_app_config";
    private static final String PREF_NAME = "webapp_config";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 自动隐藏软键盘
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // 1. 初始化配置
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        loadAppConfig();

        // 2. 应用基础配置（方向、状态栏）
        applyWindowConfig();

        // 3. 检查权限 (动态读取 JSON)
        checkAndRequestPermissions();

        // 4. 初始化 WebView
        webView = new WebView(this);
        setContentView(webView);
        configureWebView();

        // 5. 加载网页
        loadWebContent();
    }

    /**
     * 读取 webapp.json 配置
     */
    private void loadAppConfig() {
        try {
            InputStream is = getAssets().open("webapp.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // 简单的去注释逻辑
                int commentIndex = line.indexOf("//");
                if (commentIndex != -1 && !line.trim().startsWith("http")) {
                    sb.append(line.substring(0, commentIndex));
                } else {
                    sb.append(line);
                }
            }
            reader.close();
            appConfig = new JSONObject(sb.toString());
            prefs.edit().putString(CONFIG_CACHE_KEY, sb.toString()).apply();
        } catch (Exception e) {
            // 读取失败，尝试使用缓存或默认
            try {
                String cached = prefs.getString(CONFIG_CACHE_KEY, "{}");
                appConfig = new JSONObject(cached);
            } catch (Exception ex) {
                appConfig = new JSONObject();
            }
        }
    }

    /**
     * 应用窗口级配置 (方向、状态栏、全屏)
     */
    private void applyWindowConfig() {
        if (appConfig == null) return;

        // --- 1. 屏幕方向 ---
        String orientation = appConfig.optString("orientation", "portrait");
        switch (orientation) {
            case "landscape": setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); break;
            case "portrait": setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); break;
            case "auto": setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); break;
            default: setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        // --- 2. 状态栏与全屏 ---
        updateStatusBar();
    }

    /**
     * 独立提取状态栏更新逻辑，方便 ConfigurationChanged 调用
     */
    private void updateStatusBar() {
        if (appConfig == null) return;

        boolean isFullscreen = appConfig.optBoolean("fullscreen", false);
        Window window = getWindow();
        View decorView = window.getDecorView();

        if (isFullscreen) {
            // 全屏模式：隐藏状态栏和导航栏
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(flags);
        } else {
            // 非全屏模式：处理状态栏颜色和文字颜色
            JSONObject statusBar = appConfig.optJSONObject("statusBar");
            int flags = View.SYSTEM_UI_FLAG_VISIBLE; // 清除之前的全屏标记

            // 默认白底
            String bgColor = "#FFFFFF";
            String style = "dark"; // 默认深色文字

            if (statusBar != null) {
                if (statusBar.optBoolean("hidden", false)) {
                    // 配置里明确要求隐藏状态栏，但不是全屏模式
                    flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
                bgColor = statusBar.optString("backgroundColor", "#FFFFFF");
                style = statusBar.optString("style", "dark");
            }

            // 处理文字颜色 (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 如果 style 是 "dark"，意味着我们要深色文字 (Black text)
                // 这对应 SYSTEM_UI_FLAG_LIGHT_STATUS_BAR (亮色状态栏背景 -> 深色文字)
                if ("dark".equals(style)) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    // style "light" -> 浅色文字 (White text)，清除 flag
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
            }

            decorView.setSystemUiVisibility(flags);

            // 处理背景颜色 (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.setStatusBarColor(Color.parseColor(bgColor));
                } catch (Exception e) {
                    window.setStatusBarColor(Color.WHITE);
                }
            }
        }
    }

    /**
     * 动态读取配置并申请权限
     */
    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        List<String> neededPermissions = new ArrayList<>();
        JSONArray jsonPerms = appConfig.optJSONArray("permissions");

        if (jsonPerms != null) {
            for (int i = 0; i < jsonPerms.length(); i++) {
                String perm = jsonPerms.optString(i);
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(perm);
                }
            }
        } else {
            // 默认申请网络权限 (虽然 Manifest 里通常有，但运行时检查是个好习惯)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.INTERNET);
            }
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
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

        // 混合内容 (支持 https 加载 http 图片)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 读取 webview 节点配置
        JSONObject wvConfig = appConfig.optJSONObject("webview");
        boolean zoomEnabled = false;
        int textZoom = 100;
        String userAgent = "";

        if (wvConfig != null) {
            zoomEnabled = wvConfig.optBoolean("zoomEnabled", false);
            textZoom = wvConfig.optInt("textZoom", 100);
            userAgent = wvConfig.optString("userAgent", "");
        }

        settings.setSupportZoom(zoomEnabled);
        settings.setBuiltInZoomControls(zoomEnabled);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(textZoom);

        if (!userAgent.isEmpty()) {
            settings.setUserAgentString(userAgent);
        }

        // 注入 JS 接口
        // 假设 WebAppInterface 是你定义的类
        webAppInterface = new WebAppInterface(this, webView);
        webView.addJavascriptInterface(webAppInterface, "Android");

        // 设置 Client
        webView.setWebViewClient(new LocalContentWebViewClient());

        webChromeClient = new FullWebChromeClient();
        webView.setWebChromeClient(webChromeClient);

        // 调试模式
        WebView.setWebContentsDebuggingEnabled(true);
    }

    private void loadWebContent() {
        String targetUrl = "index.html"; // 默认
        if (appConfig != null) {
            String url = appConfig.optString("targetUrl");
            if (url.isEmpty()) url = appConfig.optString("url");
            if (url.isEmpty()) url = appConfig.optString("entry");
            if (!url.isEmpty()) targetUrl = url;
        }

        if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            webView.loadUrl(targetUrl);
        } else {
            // 本地资源标准化路径
            targetUrl = targetUrl.replace("./", "").replace("/", "");
            // 使用 localhost 虚拟域名加载，规避 file:// 协议的跨域限制
            webView.loadUrl("http://localhost/" + targetUrl);
        }
    }

    // --- 内部类：处理本地资源加载 (http://localhost -> assets) ---
    private class LocalContentWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url != null && "localhost".equalsIgnoreCase(url.getHost())) {
                String path = url.getPath();
                if (path == null || path.isEmpty() || "/".equals(path)) path = "index.html";
                if (path.startsWith("/")) path = path.substring(1);

                try {
                    InputStream stream = getAssets().open(path);
                    String mimeType = getMimeType(path);
                    return new WebResourceResponse(mimeType, "UTF-8", stream);
                } catch (IOException e) {
                    // 返回 404 页面
                    String errorHtml = "<html><body><h1>404 Not Found</h1><p>File not found in assets: " + path + "</p></body></html>";
                    return new WebResourceResponse("text/html", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(errorHtml.getBytes()));
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            // 处理外部协议
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("geo:")) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; } catch (Exception e) {}
            }
            return false;
        }

        private String getMimeType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg")) return "image/jpeg";
            if (path.endsWith(".svg")) return "image/svg+xml";
            return "text/plain";
        }
    }

    // --- 内部类：处理文件选择 (input type=file) ---
    private class FullWebChromeClient extends WebChromeClient {
        private ValueCallback<Uri[]> uploadMessage;

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (uploadMessage != null) {
                uploadMessage.onReceiveValue(null);
                uploadMessage = null;
            }
            uploadMessage = filePathCallback;

            try {
                Intent intent = fileChooserParams.createIntent();
                startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                return true;
            } catch (Exception e) {
                uploadMessage = null;
                return false;
            }
        }

        @Override
        public void onConsoleMessage(String message, int lineNumber, String sourceID) {
            // Log.d("WebView", message);
        }
    }

    // --- 声明周期与回调 ---

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (webChromeClient.uploadMessage == null) return;
            webChromeClient.uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            webChromeClient.uploadMessage = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (webAppInterface != null) {
                // 转发给 JS 接口处理 (如果有回调逻辑)
                webAppInterface.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 屏幕旋转后，重新应用状态栏颜色（因为 System UI 可能会被系统重置）
        new Handler(Looper.getMainLooper()).postDelayed(this::updateStatusBar, 100);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        if (webAppInterface != null) webAppInterface.onDestroy();
        super.onDestroy();
    }
}