package com.web.webapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog; // 如果没有AndroidX，用 android.app.AlertDialog

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WebAppInterface {
    private Context mContext;
    private WebView mWebView;
    private OkHttpClient client;
    private Gson gson;
    private Handler mainHandler;
    private SharedPreferences prefs;

    public WebAppInterface(Context c, WebView webView) {
        mContext = c;
        mWebView = webView;
        client = new OkHttpClient();
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = mContext.getSharedPreferences("WebAppPrefs", Context.MODE_PRIVATE);
    }

    // ========================== 基础功能 ==========================

    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    /**
     * 原生震动
     * @param milliseconds 毫秒数
     */
    @JavascriptInterface
    public void vibrate(long milliseconds) {
        Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(milliseconds);
        }
    }

    /**
     * 获取设备信息 (同步返回字符串)
     */
    @JavascriptInterface
    public String getDeviceInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("model", Build.MODEL);
        info.put("manufacturer", Build.MANUFACTURER);
        info.put("android_version", Build.VERSION.RELEASE);
        info.put("sdk_int", String.valueOf(Build.VERSION.SDK_INT));
        return gson.toJson(info);
    }

    // ========================== 剪贴板 ==========================

    @JavascriptInterface
    public void copyToClipboard(String text) {
        mainHandler.post(() -> {
            ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("WebApp", text);
            clipboard.setPrimaryClip(clip);
            showToast("已复制到剪贴板");
        });
    }

    @JavascriptInterface
    public void getFromClipboard(String callbackId) {
        mainHandler.post(() -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                String text = "";
                if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                    CharSequence content = clipboard.getPrimaryClip().getItemAt(0).getText();
                    if (content != null) text = content.toString();
                }
                sendResultToJs(callbackId, true, text);
            } catch (Exception e) {
                sendResultToJs(callbackId, false, e.getMessage());
            }
        });
    }

    // ========================== 本地存储 (KV) ==========================

    @JavascriptInterface
    public void saveStorage(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @JavascriptInterface
    public String getStorage(String key) {
        // 简单数据直接同步返回即可
        return prefs.getString(key, "");
    }

    // ========================== 网络请求 ==========================

    @JavascriptInterface
    public void request(String url, String method, String callbackId) {
        Request.Builder builder = new Request.Builder().url(url);
        if ("GET".equalsIgnoreCase(method)) builder.get();

        // 可以在这里添加 request body 的处理逻辑

        client.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                sendResultToJs(callbackId, false, "Network Error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    sendResultToJs(callbackId, true, body);
                } else {
                    sendResultToJs(callbackId, false, "HTTP " + response.code() + ": " + body);
                }
            }
        });
    }

    // ========================== 内部工具 ==========================

    private void sendResultToJs(String callbackId, boolean success, String data) {
        ApiResponse result = new ApiResponse(success, data);
        String jsonResult = gson.toJson(result);
        // Base64 编码，防止特殊字符破坏 JS 语法
        String base64Result = Base64.encodeToString(
                jsonResult.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

        mainHandler.post(() -> {
            String jsCode = String.format("window.onAndroidResponse('%s', '%s')", callbackId, base64Result);
            mWebView.evaluateJavascript(jsCode, null);
        });
    }

    private static class ApiResponse {
        boolean success;
        String data;
        public ApiResponse(boolean success, String data) {
            this.success = success;
            this.data = data;
        }
    }
}