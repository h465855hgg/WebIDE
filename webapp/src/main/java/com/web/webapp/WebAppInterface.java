package com.web.webapp;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import rrzt.web.web_bridge.SharedWebInterface;

public class WebAppInterface extends SharedWebInterface {

    private final MainActivity activity;

    public WebAppInterface(MainActivity context, WebView webView) {
        super(context, webView);
        this.activity = context;
    }

    @JavascriptInterface
    @Override
    public void setBackKeyInterceptor(boolean enabled) {
        // 打包后的 App 暂时不处理返回键拦截，或者可以在这里对接 Activity 的 onBackPressed
    }

    // 处理权限请求回调
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 权限回调逻辑如果比较复杂，可以在这里实现
    }

    // --- 文件系统 (针对打包后的 Assets，只读) ---

    @JavascriptInterface
    @Override
    public String readFile(String path) {
        try {
            // 在打包后的 App 中，readFile 默认从 assets 读取
            // 路径处理：移除开头的 ./ 或 /
            if (path.startsWith("./")) path = path.substring(2);
            if (path.startsWith("/")) path = path.substring(1);

            InputStream is = activity.getAssets().open(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @JavascriptInterface
    @Override
    public boolean writeFile(String path, String content) {
        // 打包后的 App assets 是只读的
        showToast("Error: Packaged app cannot write to assets.");
        return false;
    }

    @JavascriptInterface
    @Override
    public String getAppConfig() {
        // 在打包应用中，webapp.json 也在 assets 里
        return readFile("webapp.json");
    }
}