package com.web.webapp;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WebAppInterface {
    private Context context;
    private WebView webView;
    private Activity activity;
    private Handler mainHandler;
    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private SensorEventListener sensorListener;

    // 权限请求相关
    private static final int PERMISSION_REQUEST_CODE = 100;
    private Map<String, List<Runnable>> permissionCallbacks = new HashMap<>();

    public WebAppInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.activity = (Activity) context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences("WebAppPrefs", Context.MODE_PRIVATE);
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    // ========================== 基础功能 ==========================

    // ========================== 网络请求 (绕过 CORS) ==========================

    @JavascriptInterface
    public void httpRequest(final String method, final String urlStr, final String headersJson, final String body, final String callbackId) {
        // 在子线程执行网络请求，避免阻塞主线程
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method.toUpperCase());
                conn.setConnectTimeout(15000); // 连接超时 15s
                conn.setReadTimeout(15000);    // 读取超时 15s

                // 1. 设置请求头 (Headers)
                if (headersJson != null && !headersJson.isEmpty()) {
                    try {
                        JSONObject headers = new JSONObject(headersJson);
                        Iterator<String> keys = headers.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            conn.setRequestProperty(key, headers.getString(key));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 2. 发送请求体 (Body) - 仅限 POST/PUT
                if (body != null && !body.isEmpty() && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                    conn.setDoOutput(true);
                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                }

                // 3. 获取响应状态码
                int code = conn.getResponseCode();

                // 4. 读取响应内容 (成功读 InputStream，失败读 ErrorStream)
                InputStream stream = (code < 400) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseText = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseText.append(line).append("\n");
                        }
                    }
                }

                // 5. 构造返回数据
                JSONObject resultJson = new JSONObject();
                resultJson.put("status", code);
                resultJson.put("body", responseText.toString());

                // 获取响应头
                JSONObject responseHeaders = new JSONObject();
                Map<String, List<String>> headerFields = conn.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
                    if (entry.getKey() != null) {
                        // 将 List<String> 转为逗号分隔的 String
                        StringBuilder sb = new StringBuilder();
                        for (String val : entry.getValue()) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append(val);
                        }
                        responseHeaders.put(entry.getKey(), sb.toString());
                    }
                }
                resultJson.put("headers", responseHeaders);

                // 成功回调：将 JSON 字符串传回 JS
                sendResultToJs(callbackId, true, resultJson.toString());

            } catch (Exception e) {
                e.printStackTrace();
                // 失败回调
                try {
                    JSONObject errorJson = new JSONObject();
                    errorJson.put("status", 0);
                    errorJson.put("error", e.getMessage() != null ? e.getMessage() : "Unknown Error");
                    sendResultToJs(callbackId, false, errorJson.toString());
                } catch (JSONException jsonException) {
                    jsonException.printStackTrace();
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    @JavascriptInterface
    public void showToast(final String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public void vibrate(final long milliseconds) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(milliseconds);
            }
        }
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("model", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("brand", Build.BRAND);
            info.put("device", Build.DEVICE);
            info.put("product", Build.PRODUCT);
            info.put("androidVersion", Build.VERSION.RELEASE);
            info.put("sdkInt", Build.VERSION.SDK_INT);
            info.put("screenWidth", context.getResources().getDisplayMetrics().widthPixels);
            info.put("screenHeight", context.getResources().getDisplayMetrics().heightPixels);
            info.put("density", context.getResources().getDisplayMetrics().density);
            info.put("locale", Locale.getDefault().toString());
            info.put("timezone", java.util.TimeZone.getDefault().getID());

            // 获取更多设备信息
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                info.put("phoneNumber", tm.getLine1Number() != null ? tm.getLine1Number() : "");
                info.put("simOperator", tm.getSimOperatorName());
                info.put("networkOperator", tm.getNetworkOperatorName());
            }

            // 获取 WIFI 信息
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                info.put("wifiSSID", wifiInfo.getSSID().replace("\"", ""));
                info.put("wifiBSSID", wifiInfo.getBSSID());
                info.put("wifiRSSI", wifiInfo.getRssi());
            }

            // 获取 MAC 地址
            info.put("macAddress", getMacAddress());

            // 获取存储信息
            File internalStorage = Environment.getDataDirectory();
            long internalFree = internalStorage.getFreeSpace();
            long internalTotal = internalStorage.getTotalSpace();
            info.put("internalStorageFree", internalFree);
            info.put("internalStorageTotal", internalTotal);

            // 外部存储
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalStorage = Environment.getExternalStorageDirectory();
                long externalFree = externalStorage.getFreeSpace();
                long externalTotal = externalStorage.getTotalSpace();
                info.put("externalStorageFree", externalFree);
                info.put("externalStorageTotal", externalTotal);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return info.toString();
    }

    private String getMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : interfaces) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;
                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) return "";
                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }
                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // ========================== 剪贴板功能 ==========================

    @JavascriptInterface
    public void copyToClipboard(final String text) {
        mainHandler.post(() -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("WebApp", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
    }

    @JavascriptInterface
    public void getFromClipboard(final String callbackId) {
        mainHandler.post(() -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                String text = "";
                if (clipboard.hasPrimaryClip()) {
                    ClipData clipData = clipboard.getPrimaryClip();
                    if (clipData != null && clipData.getItemCount() > 0) {
                        CharSequence content = clipData.getItemAt(0).getText();
                        if (content != null) {
                            text = content.toString();
                        }
                    }
                }
                sendResultToJs(callbackId, true, text);
            } catch (Exception e) {
                sendResultToJs(callbackId, false, e.getMessage());
            }
        });
    }

    // ========================== 本地存储 ==========================

    @JavascriptInterface
    public void saveStorage(final String key, final String value) {
        prefs.edit().putString(key, value).apply();
    }

    @JavascriptInterface
    public String getStorage(final String key) {
        return prefs.getString(key, "");
    }

    @JavascriptInterface
    public void removeStorage(final String key) {
        prefs.edit().remove(key).apply();
    }

    @JavascriptInterface
    public void clearStorage() {
        prefs.edit().clear().apply();
    }

    @JavascriptInterface
    public String getAllStorage() {
        Map<String, ?> all = prefs.getAll();
        JSONObject result = new JSONObject();
        try {
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result.toString();
    }

    // ========================== 文件系统 ==========================

    @JavascriptInterface
    public String readFile(final String path) {
        try {
            if (path.startsWith("assets/")) {
                // 读取 assets 文件
                String assetPath = path.substring(7);
                InputStream is = context.getAssets().open(assetPath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            } else {
                // 读取外部文件
                File file = new File(path);
                if (file.exists() && file.canRead()) {
                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();
                    return new String(data, "UTF-8");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @JavascriptInterface
    public boolean writeFile(final String path, final String content) {
        try {
            File file = new File(path);

            // 确保父目录存在
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JavascriptInterface
    public boolean fileExists(final String path) {
        return new File(path).exists();
    }

    @JavascriptInterface
    public boolean deleteFile(final String path) {
        File file = new File(path);
        return file.delete();
    }

    @JavascriptInterface
    public String listFiles(final String directory) {
        try {
            File dir = new File(directory);
            if (!dir.exists() || !dir.isDirectory()) {
                return "[]";
            }

            File[] files = dir.listFiles();
            JSONArray result = new JSONArray();
            for (File file : files) {
                JSONObject fileInfo = new JSONObject();
                fileInfo.put("name", file.getName());
                fileInfo.put("path", file.getAbsolutePath());
                fileInfo.put("isDirectory", file.isDirectory());
                fileInfo.put("size", file.length());
                fileInfo.put("lastModified", file.lastModified());
                result.put(fileInfo);
            }
            return result.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    // ========================== 系统功能 ==========================

    @JavascriptInterface
    public void openBrowser(final String url) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @JavascriptInterface
    public void shareText(final String text) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                context.startActivity(Intent.createChooser(intent, "分享"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @JavascriptInterface
    public void callPhone(final String phoneNumber) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @JavascriptInterface
    public void sendSMS(final String phoneNumber, final String message) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phoneNumber));
                intent.putExtra("sms_body", message);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @JavascriptInterface
    public void sendEmail(final String email, final String subject, final String body) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:"));
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
                intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                intent.putExtra(Intent.EXTRA_TEXT, body);
                context.startActivity(Intent.createChooser(intent, "发送邮件"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @JavascriptInterface
    public void openMap(final double latitude, final double longitude, final String label) {
        mainHandler.post(() -> {
            try {
                String uri = "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude + "(" + label + ")";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ========================== 屏幕控制 ==========================

    @JavascriptInterface
    public void keepScreenOn(final boolean keepOn) {
        mainHandler.post(() -> {
            if (keepOn) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    @JavascriptInterface
    public void setScreenBrightness(final float brightness) {
        mainHandler.post(() -> {
            Window window = activity.getWindow();
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.screenBrightness = brightness; // 0.0 - 1.0
            window.setAttributes(layoutParams);
        });
    }

    @JavascriptInterface
    public float getScreenBrightness() {
        Window window = activity.getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        return layoutParams.screenBrightness;
    }

    @JavascriptInterface
    public void setVolume(final int volume) {
        // 需要权限：android.permission.MODIFY_AUDIO_SETTINGS
        try {
            Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
            Method setStreamVolume = audioSystemClass.getMethod("setStreamVolume", int.class, int.class, int.class);
            setStreamVolume.invoke(null, 3, volume, 0); // 3 = STREAM_MUSIC
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========================== 传感器 ==========================

    @JavascriptInterface
    public void startSensor(final String sensorType, final String callbackId) {
        mainHandler.post(() -> {
            int sensorConstant;
            switch (sensorType.toLowerCase()) {
                case "accelerometer":
                    sensorConstant = Sensor.TYPE_ACCELEROMETER;
                    break;
                case "gyroscope":
                    sensorConstant = Sensor.TYPE_GYROSCOPE;
                    break;
                case "magnetometer":
                    sensorConstant = Sensor.TYPE_MAGNETIC_FIELD;
                    break;
                case "light":
                    sensorConstant = Sensor.TYPE_LIGHT;
                    break;
                case "proximity":
                    sensorConstant = Sensor.TYPE_PROXIMITY;
                    break;
                default:
                    sendResultToJs(callbackId, false, "Unsupported sensor type");
                    return;
            }

            Sensor sensor = sensorManager.getDefaultSensor(sensorConstant);
            if (sensor == null) {
                sendResultToJs(callbackId, false, "Sensor not available");
                return;
            }

            if (sensorListener != null) {
                sensorManager.unregisterListener(sensorListener);
            }

            sensorListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    JSONArray values = new JSONArray();
                    for (float value : event.values) {
                        try {
                            values.put(value);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    sendResultToJs(callbackId + "_data", true, values.toString());
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };

            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            sendResultToJs(callbackId, true, "Sensor started");
        });
    }

    @JavascriptInterface
    public void stopSensor() {
        mainHandler.post(() -> {
            if (sensorListener != null) {
                sensorManager.unregisterListener(sensorListener);
                sensorListener = null;
            }
        });
    }

    // ========================== 权限管理 ==========================

    @JavascriptInterface
    public void requestPermission(final String permission, final String callbackId) {
        mainHandler.post(() -> {
            // 检查是否已有权限
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                sendResultToJs(callbackId, true, "Permission already granted");
                return;
            }

            // 保存回调
            if (!permissionCallbacks.containsKey(permission)) {
                permissionCallbacks.put(permission, new ArrayList<>());
            }
            permissionCallbacks.get(permission).add(() -> {
                boolean granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
                sendResultToJs(callbackId, granted, granted ? "Permission granted" : "Permission denied");
            });

            // 请求权限
            ActivityCompat.requestPermissions(activity, new String[]{permission}, PERMISSION_REQUEST_CODE);
        });
    }

    @JavascriptInterface
    public boolean hasPermission(final String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface
    public void openAppSettings() {
        mainHandler.post(() -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            context.startActivity(intent);
        });
    }

    // ========================== 应用配置 ==========================

    @JavascriptInterface
    public String getAppConfig() {
        try {
            // 从 assets 读取 webapp.json
            InputStream is = context.getAssets().open("webapp.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    @JavascriptInterface
    public void reloadApp() {
        mainHandler.post(() -> {
            activity.recreate();
        });
    }

    @JavascriptInterface
    public void exitApp() {
        mainHandler.post(() -> {
            activity.finishAffinity();
            System.exit(0);
        });
    }

    // ========================== 日期时间 ==========================

    @JavascriptInterface
    public long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    @JavascriptInterface
    public String formatDate(final long timestamp, final String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // ========================== 工具方法 ==========================

    private void sendResultToJs(final String callbackId, final boolean success, final String data) {
        mainHandler.post(() -> {
            try {
                JSONObject result = new JSONObject();
                result.put("success", success);
                result.put("data", data);

                String jsonResult = result.toString();
                String base64Result = Base64.encodeToString(jsonResult.getBytes("UTF-8"), Base64.NO_WRAP);

                String jsCode = String.format("if(window.onAndroidResponse) window.onAndroidResponse('%s', '%s')",
                        callbackId, base64Result);
                webView.evaluateJavascript(jsCode, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 权限请求结果处理（需要在 Activity 中调用）
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                if (permissionCallbacks.containsKey(permission)) {
                    for (Runnable callback : permissionCallbacks.get(permission)) {
                        callback.run();
                    }
                    permissionCallbacks.remove(permission);
                }
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        // 清理传感器监听器
        if (sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
        }
        super.finalize();
    }
}