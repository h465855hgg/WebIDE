package com.web.webapp;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private WebView webView;
    // 使用 localhost 访问本地资源
    private static final String LAUNCH_URL = "http://localhost/index.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);

        configureWebView();

        webView.loadUrl(LAUNCH_URL);
        setContentView(webView);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // 【关键】注入 Java 对象，JS 通过 window.Android 调用
        webView.addJavascriptInterface(new WebAppInterface(this, webView), "Android");

        // 拦截 localhost 请求读取 assets
        webView.setWebViewClient(new LocalContentWebViewClient());
    }

    // 拦截器逻辑（保持原样，用于读取 assets 文件）
    private class LocalContentWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url != null && "localhost".equalsIgnoreCase(url.getHost())) {
                try {
                    String path = url.getPath();
                    if (path == null || path.equals("/")) path = "index.html";
                    if (path.startsWith("/")) path = path.substring(1);

                    InputStream stream = getAssets().open(path);
                    String mimeType = "text/html";
                    if (path.endsWith(".css")) mimeType = "text/css";
                    if (path.endsWith(".js")) mimeType = "application/javascript";

                    return new WebResourceResponse(mimeType, "UTF-8", stream);
                } catch (IOException e) {
                    return null; // 404
                }
            }
            return super.shouldInterceptRequest(view, request);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) webView.destroy();
    }
}