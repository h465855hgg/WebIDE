package com.web.webapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;

public class FullWebChromeClient extends WebChromeClient {

    private ValueCallback<Uri[]> filePathCallback;
    private MainActivity activity;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1;

    public FullWebChromeClient() {
    }

    public void setActivity(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        // 授予所有权限请求（相机、麦克风等）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            request.grant(request.getResources());
        }
    }

    @Override
    public boolean onShowFileChooser(WebView webView,
                                     ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {

        // 保存回调
        this.filePathCallback = filePathCallback;

        // 创建文件选择 Intent
        Intent intent = fileChooserParams.createIntent();
        try {
            activity.startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
        } catch (Exception e) {
            // 没有找到文件选择器应用
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                this.filePathCallback = null;
            }
            return false;
        }
        return true;
    }

    /**
     * 处理文件选择结果
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }
}