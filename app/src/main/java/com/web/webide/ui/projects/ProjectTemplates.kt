package com.web.webide.ui.projects

object ProjectTemplates {

    // ==========================================
    // 1. 普通 Web 项目模板 (纯前端)
    // ==========================================

    val normalIndexHtml = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Website</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <h1>Hello World</h1>
        <p>这是一个普通的 Web 项目</p>
        <button id="clickBtn">点击我</button>
        <p id="output"></p>
    </div>
    <script src="js/script.js"></script>
</body>
</html>
    """.trimIndent()

    val normalCss = """
body {
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    background-color: #f0f2f5;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
}
.container {
    background: white;
    padding: 2rem;
    border-radius: 10px;
    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
    text-align: center;
}
button {
    background-color: #007bff;
    color: white;
    border: none;
    padding: 10px 20px;
    border-radius: 5px;
    cursor: pointer;
    font-size: 16px;
}
button:hover {
    background-color: #0056b3;
}
    """.trimIndent()

    val normalJs = """
console.log('Script loaded!');

document.getElementById('clickBtn').addEventListener('click', function() {
    const output = document.getElementById('output');
    const now = new Date().toLocaleTimeString();
    output.innerText = '你点击了按钮，时间：' + now;
    console.log('Button clicked');
});
    """.trimIndent()


    // ==========================================
    // 2. Android WebApp 模板 (带 Native 接口)
    // ==========================================

    val apiJs = """
// Native Bridge 初始化
window.requestCallbacks = {};

window.onAndroidResponse = function(callbackId, base64Response) {
    const callback = window.requestCallbacks[callbackId];
    if (!callback) return;
    try {
        const jsonString = decodeURIComponent(escape(window.atob(base64Response)));
        const response = JSON.parse(jsonString);
        if (response.success) {
            let finalData = response.data;
            try {
                if (typeof finalData === 'string' && (finalData.startsWith('{') || finalData.startsWith('['))) {
                    finalData = JSON.parse(finalData);
                }
            } catch (e) {}
            callback.resolve(finalData);
        } else {
            callback.reject(response.data);
        }
    } catch (e) {
        callback.reject("Bridge Error: " + e.message);
    }
    delete window.requestCallbacks[callbackId];
};

const callAsync = (methodName, ...args) => {
    return new Promise((resolve, reject) => {
        if (!window.Android || !window.Android[methodName]) {
            console.warn(`Native method '${'$'}{methodName}' not found.`);
            return reject("Native method not found");
        }
        const callbackId = 'cb_' + Date.now() + Math.random();
        window.requestCallbacks[callbackId] = { resolve, reject };
        window.Android[methodName](...args, callbackId);
    });
};

window.NativeAPI = {
    toast: (msg) => window.Android?.showToast(msg),
    vibrate: (ms = 50) => window.Android?.vibrate(ms),
    getDeviceInfo: () => {
        if (!window.Android) return null;
        try { return JSON.parse(window.Android.getDeviceInfo()); } catch(e) { return null; }
    },
    clipboard: {
        copy: (text) => window.Android?.copyToClipboard(text),
        read: () => callAsync('getFromClipboard')
    },
    storage: {
        set: (key, value) => window.Android?.saveStorage(key, typeof value === 'object' ? JSON.stringify(value) : value),
        get: (key) => {
            const val = window.Android?.getStorage(key);
            try { return JSON.parse(val); } catch(e) { return val; }
        }
    }
};
    """.trimIndent()

    val webAppIndexHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Web App</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <h1>Web App Demo</h1>
        <p>点击下方按钮测试原生能力</p>
        
        <div class="card">
            <button onclick="NativeAPI.toast('来自 WebIDE 的原生 Toast！')">原生 Toast</button>
            <button onclick="NativeAPI.vibrate(100)">手机震动</button>
            <button onclick="copyInfo()">复制设备信息</button>
        </div>
        <p id="info" style="margin-top: 20px; color: #888; font-size: 12px;"></p>
    </div>
    <script src="js/api.js"></script>
    <script src="js/index.js"></script>
</body>
</html>
    """.trimIndent()

    val webAppIndexJs = """
// 显示设备信息
setTimeout(() => {
    const info = NativeAPI.getDeviceInfo();
    if(info) {
        document.getElementById('info').innerText = `运行于: ${'$'}{info.model} (Android ${'$'}{info.android_version})`;
    } else {
         document.getElementById('info').innerText = "未检测到原生环境";
    }
}, 100);

function copyInfo() {
    const info = document.getElementById('info').innerText;
    NativeAPI.clipboard.copy(info);
}
    """.trimIndent()

    val webAppCss = """
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0f2f5; display: flex; flex-direction: column; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
.container { text-align: center; width: 100%; max-width: 400px; }
.card { background: white; padding: 20px; border-radius: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px; }
button { background: #6200ee; color: white; border: none; padding: 12px; border-radius: 8px; font-size: 14px; font-weight: bold; cursor: pointer; }
button:active { opacity: 0.8; }
h1 { color: #333; margin-bottom: 5px; font-size: 24px; }
p { color: #666; margin: 0; }
    """.trimIndent()

    // WebApp 配置文件
    fun getConfigFile(packageName: String, appName: String): String = """
{
  "name": "$appName",
  "package": "$packageName",
  "versionName": "1.0.0",
  "versionCode": 1,
  "orientation": "portrait",
  "fullscreen": false,
  "targetUrl": "index.html"
}
    """.trimIndent()
}