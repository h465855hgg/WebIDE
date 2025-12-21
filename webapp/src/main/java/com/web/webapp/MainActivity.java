package com.web.webapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "WebIDE_Main";

    private WebView webView;
    private JSONObject appConfig;
    private FullWebChromeClient webChromeClient;
    private WebAppInterface webAppInterface;

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_CHOOSER_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 0. 基础设置：防止键盘自动顶起
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // 1. 加载配置
        loadAppConfig();

        // 2. 设置屏幕方向 (修正版)
        applyOrientation();

        // 3. 初始化 WebView
        webView = new WebView(this);
        setContentView(webView); // 必须先 setContentView

        // 4. 终极状态栏修复 (Android 16 兼容)
        applyModernStatusBar();

        // 5. 权限与 WebView 设置
        checkAndRequestPermissions();
        configureWebView();

        // 6. 加载网页
        loadWebContent();
    }

    private void loadAppConfig() {
        try {
            InputStream is = getAssets().open("webapp.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().startsWith("//")) {
                    int commentIdx = line.indexOf("//");
                    if (commentIdx != -1 && !line.contains("http:") && !line.contains("https:")) {
                        sb.append(line.substring(0, commentIdx));
                    } else {
                        sb.append(line);
                    }
                }
            }
            reader.close();
            appConfig = new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Config load failed", e);
            appConfig = new JSONObject();
        }
    }

    /**
     * 修复 1：方向映射修正
     * 0 -> Portrait (竖屏)
     * 1 -> Landscape (横屏)
     */
    private void applyOrientation() {
        if (appConfig == null) return;
        String ori = appConfig.optString("orientation", "auto").trim().toLowerCase();

        Log.d(TAG, "Orientation config: " + ori);

        // 根据你的要求修正映射
        if (ori.equals("0") || ori.equals("portrait")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (ori.equals("1") || ori.equals("landscape")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (ori.equals("auto")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /**
     * 修复 2：针对 Android 15/16 的终极状态栏修复
     * 不再依赖 flags，而是使用 WindowInsetsControllerCompat 和 手动 Padding
     */
    private void applyModernStatusBar() {
        if (appConfig == null || webView == null) return;

        boolean isFullscreen = appConfig.optBoolean("fullscreen", false);

        Window window = getWindow();
        WindowInsetsControllerCompat windowController = WindowCompat.getInsetsController(window, webView);

        if (isFullscreen) {
            // --- 全屏模式 ---
            // 1. 隐藏系统栏 (状态栏 + 导航栏)
            windowController.hide(WindowInsetsCompat.Type.systemBars());
            // 2. 允许手势滑出
            windowController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            // 3. 内容延伸到边缘
            WindowCompat.setDecorFitsSystemWindows(window, false);
            // 4. 清除 WebView Padding
            webView.setPadding(0, 0, 0, 0);
        } else {
            // --- 非全屏模式 (标准模式) ---

            // 1. 显示系统栏
            windowController.show(WindowInsetsCompat.Type.systemBars());

            // 2. 关键：针对 Android 15+ 强制 Edge-to-Edge 的特性
            // 我们手动监听系统栏高度，并把它加到 WebView 的 PaddingTop 上
            // 这是唯一能保证 "绝对不覆盖" 的方法
            ViewCompat.setOnApplyWindowInsetsListener(webView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                // 给 WebView 顶部增加 Padding，高度等于状态栏高度
                v.setPadding(0, insets.top, 0, 0);
                return WindowInsetsCompat.CONSUMED;
            });

            // 3. 设置状态栏样式
            JSONObject sbConfig = appConfig.optJSONObject("statusBar");
            String bgColorStr = "#FFFFFF";
            String style = "dark";

            if (sbConfig != null) {
                bgColorStr = sbConfig.optString("backgroundColor", "#FFFFFF");
                style = sbConfig.optString("style", "dark");
            }

            // 4. 设置文字颜色 (深色/浅色)
            // dark = 黑色文字 (背景是亮的) -> isAppearanceLightStatusBars = true
            windowController.setAppearanceLightStatusBars("dark".equalsIgnoreCase(style));

            // 5. 设置背景色
            // 注意：在 Android 15+ 上，由于强制 Edge-to-Edge，window.statusBarColor 可能会被忽略
            // 但设置它作为保底
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(Color.parseColor(bgColorStr));
            } catch (Exception e) {
                window.setStatusBarColor(Color.WHITE);
            }
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        List<String> needed = new ArrayList<>();
        JSONArray jsonPerms = appConfig.optJSONArray("permissions");
        if (jsonPerms != null) {
            for (int i = 0; i < jsonPerms.length(); i++) {
                String perm = jsonPerms.optString(i);
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(perm);
                }
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        JSONObject wvConfig = appConfig.optJSONObject("webview");
        boolean zoomEnabled = true;
        int textZoom = 100;
        String rawUserAgent = "";

        if (wvConfig != null) {
            zoomEnabled = wvConfig.optBoolean("zoomEnabled", true);
            textZoom = wvConfig.optInt("textZoom", 100);
            rawUserAgent = wvConfig.optString("userAgent", "");
        }

        settings.setSupportZoom(zoomEnabled);
        settings.setBuiltInZoomControls(zoomEnabled);
        settings.setDisplayZoomControls(false);

        // UA 处理
        if (!rawUserAgent.isEmpty()) {
            String lowerUA = rawUserAgent.toLowerCase();
            if (lowerUA.contains("windows") || lowerUA.contains("pc")) {
                settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                settings.setLoadWithOverviewMode(true);
                settings.setUseWideViewPort(true);
                settings.setTextZoom(textZoom == 100 ? 50 : textZoom);
            } else if (lowerUA.contains("ios") || lowerUA.contains("iphone")) {
                settings.setUserAgentString("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
                settings.setTextZoom(textZoom);
            } else {
                settings.setUserAgentString(rawUserAgent);
                settings.setTextZoom(textZoom);
            }
        } else {
            settings.setTextZoom(textZoom);
        }

        webAppInterface = new WebAppInterface(this, webView);
        webView.addJavascriptInterface(webAppInterface, "Android");

        // 下载监听
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                request.setTitle(filename);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(MainActivity.this, "下载中: " + filename, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ex) {}
            }
        });

        webView.setWebViewClient(new LocalContentWebViewClient());
        webChromeClient = new FullWebChromeClient();
        webView.setWebChromeClient(webChromeClient);
        WebView.setWebContentsDebuggingEnabled(true);
    }

    private void loadWebContent() {
        String targetUrl = "index.html";
        if (appConfig != null) {
            String url = appConfig.optString("targetUrl");
            if (url.isEmpty()) url = appConfig.optString("url");
            if (!url.isEmpty()) targetUrl = url;
        }

        if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            webView.loadUrl(targetUrl);
        } else {
            targetUrl = targetUrl.replace("./", "").replace("/", "");
            webView.loadUrl("http://localhost/" + targetUrl);
        }
    }

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
                    return new WebResourceResponse(getMimeType(path), "UTF-8", stream);
                } catch (IOException e) {
                    return new WebResourceResponse("text/html", "UTF-8", 404, "Not Found", null, null);
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleSpecialUrl(view, request.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleSpecialUrl(view, url);
        }

        private boolean handleSpecialUrl(WebView view, String url) {
            if (url.startsWith("http://") || url.startsWith("https://")) return false;
            try {
                Intent intent;
                if (url.startsWith("intent://")) {
                    intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                } else {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Log.w(TAG, "未安装应用: " + url);
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, "URL Scheme Error", e);
                return true;
            }
        }

        private String getMimeType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".png")) return "image/png";
            return "text/plain";
        }
    }

    private class FullWebChromeClient extends WebChromeClient {
        private ValueCallback<Uri[]> uploadMessage;

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (uploadMessage != null) uploadMessage.onReceiveValue(null);
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (webChromeClient.uploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            webChromeClient.uploadMessage.onReceiveValue(results);
            webChromeClient.uploadMessage = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}