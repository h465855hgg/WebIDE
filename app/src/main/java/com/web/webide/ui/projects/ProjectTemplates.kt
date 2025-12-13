package com.web.webide.ui.projects

object ProjectTemplates {

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
body { font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #f0f2f5; }
.container { background: white; padding: 2rem; border-radius: 8px; text-align: center; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
button { background-color: #007bff; color: white; border: none; padding: 10px 20px; border-radius: 4px; font-size: 16px; }
    """.trimIndent()

    val normalJs = """
document.getElementById('clickBtn').addEventListener('click', function() {
    document.getElementById('output').innerText = '时间：' + new Date().toLocaleTimeString();
});
    """.trimIndent()

    val apiJs = """
// 1. 核心通信层：处理 Android 回调
window.requestCallbacks = {};
window.onAndroidResponse = function(id, b64) {
    const cb = window.requestCallbacks[id];
    if(!cb) return;
    try {
        const jsonStr = decodeURIComponent(escape(window.atob(b64)));
        const res = JSON.parse(jsonStr);
        res.success ? cb.resolve(res.data) : cb.reject(res.data);
    } catch(e) { cb.reject(e.message); }
    delete window.requestCallbacks[id];
};

// 2. 通用调用函数 (将 Native 方法转为 Promise)
const call = (method, ...args) => new Promise((resolve, reject) => {
    if(!window.Android || !window.Android[method]) return reject("Native API not found: " + method);
    const id = 'cb_' + Math.random().toString(36).substr(2, 9);
    window.requestCallbacks[id] = { resolve, reject };
    // 自动补全 callbackId 参数
    window.Android[method](...args, id);
});

// 3. 对外暴露的 API 对象
window.NativeAPI = {
    // --- UI 交互 ---
    toast: (msg) => window.Android?.showToast(msg),
    vibrate: (ms=50) => window.Android?.vibrate(ms),
    
    // --- 系统能力 ---
    openBrowser: (url) => window.Android?.openBrowser(url),
    share: (text) => window.Android?.shareText(text),
    keepScreenOn: (enable) => window.Android?.keepScreenOn(enable),
    
    // --- 硬件信息 ---
    info: async () => {
        const res = await call('getDeviceInfo'); 
        return JSON.parse(res); 
    },
    
    // --- 剪贴板 ---
    clipboard: {
        copy: (text) => window.Android?.copyToClipboard(text),
        read: () => call('getFromClipboard')
    },
    
    // --- 本地存储 (SharedPreferences) ---
    storage: {
        save: (k, v) => window.Android?.saveStorage(k, v),
        get: (k) => window.Android?.getStorage(k), // 同步方法可直接调用
        remove: (k) => window.Android?.removeStorage(k),
        clear: () => window.Android?.clearStorage()
    },

    // --- 文件系统 (读写文件) ---
    file: {
        read: (path) => call('readFile', path),
        write: (path, content) => call('writeFile', path, content),
        exists: (path) => window.Android?.fileExists(path),
        list: async (dir) => JSON.parse(await call('listFiles', dir)),
        delete: (path) => window.Android?.deleteFile(path)
    },

    // --- 网络请求 (绕过跨域) ---
    http: {
        request: async (method, url, headers = {}, body = "") => {
            const res = await call('httpRequest', method, url, JSON.stringify(headers), typeof body === 'object' ? JSON.stringify(body) : body);
            return JSON.parse(res); 
        },
        get: async (url, headers = {}) => {
            return await window.NativeAPI.http.request('GET', url, headers, "");
        },
        post: async (url, data, headers = {}) => {
            const h = { "Content-Type": "application/json", ...headers };
            return await window.NativeAPI.http.request('POST', url, h, data);
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
    <title>Universal Camera</title>
    <link rel="stylesheet" href="css/style.css">
    <style>
        #video-container { width: 100%; max-width: 400px; height: 300px; background: #000; margin: 10px auto; display: none; }
        video { width: 100%; height: 100%; object-fit: cover; }
        #fallback-container { display: none; margin: 20px; }
        .btn { padding: 10px 20px; background: #007bff; color: white; border-radius: 5px; text-decoration: none; display: inline-block; cursor: pointer; }
    </style>
</head>
<body>
    <div class="container">
        <h1>万能相机演示</h1>
        <p>兼容 PC 浏览器、手机浏览器 & Android WebView</p>
        
        <!-- 方案 A: HTML5 直播流 (PC/Https 完美, file:// 协议部分受限) -->
        <div id="video-container">
            <video id="video" autoplay playsinline></video>
        </div>
        <button class="btn" onclick="startCamera()">尝试打开摄像头 (直播流)</button>
        
        <hr>

        <!-- 方案 B: 通用 Input (所有环境兼容，包括 file://) -->
        <p>如果上方直播流失败，请使用下方按钮：</p>
        <label class="btn">
            📷 拍照 / 上传
            <!-- capture="environment" 在手机上会直接调起后置摄像头 -->
            <input type="file" accept="image/*" capture="environment" style="display:none" onchange="handleFile(this)">
        </label>
        
        <div id="preview-img" style="margin-top:10px"></div>
        <p id="log" style="color:red; font-size: 12px;"></p>
    </div>

    <script src="js/api.js"></script>
    <script>
        function log(msg) { document.getElementById('log').innerText = msg; console.log(msg); }

        // 方案 A: 尝试 getUserMedia
        async function startCamera() {
            try {
                const constraints = { video: { facingMode: "environment" } };
                const stream = await navigator.mediaDevices.getUserMedia(constraints);
                const video = document.getElementById('video');
                video.srcObject = stream;
                document.getElementById('video-container').style.display = 'block';
                log("摄像头启动成功 (Stream Mode)");
            } catch (err) {
                log("直播流启动失败: " + err.name + " - " + err.message + "\n建议使用下方的【拍照/上传】按钮");
                // 失败不强求，引导用户用 Input
            }
        }

        // 方案 B: 处理 Input 拍照结果
        function handleFile(input) {
            if (input.files && input.files[0]) {
                const reader = new FileReader();
                reader.onload = function (e) {
                    const img = document.createElement('img');
                    img.src = e.target.result;
                    img.style.maxWidth = '100%';
                    img.style.marginTop = '10px';
                    const container = document.getElementById('preview-img');
                    container.innerHTML = '';
                    container.appendChild(img);
                    log("图片获取成功 (Input Mode)");
                }
                reader.readAsDataURL(input.files[0]);
            }
        }
        
        // 自动尝试一次
        // startCamera(); 
    </script>
</body>
</html>
    """.trimIndent()

    val webAppIndexJs = """
const info = NativeAPI.info();
if(info) document.getElementById('info').innerText = `Running on ${'$'}{info.model}`;
    """.trimIndent()

    val webAppCss = """
body { font-family: sans-serif; padding: 20px; text-align: center; }
button { margin: 10px; padding: 10px 20px; font-size: 16px; display: block; width: 100%; }
    """.trimIndent()

    // 🚀 通用配置生成器：支持传入 targetUrl，并包含状态栏配置
    fun getConfigFile(packageName: String, appName: String, targetUrl: String): String = """
{
  "name": "$appName",
  "package": "$packageName",
  "versionName": "1.0.0",
  "versionCode": 1,
  
  "orientation": "portrait",
  "fullscreen": false,
  "targetUrl": "$targetUrl",
  "icon": "icon.png",
  
  "statusBar": {
    "backgroundColor": "#FFFFFF",
    "style": "dark",
    "translucent": false,
    "hidden": false
  },
  
  "webview": {
    "zoomEnabled": false,
    "javascriptEnabled": true,
    "domStorageEnabled": true,
    "allowFileAccess": true,
    "textZoom": 100,
    "userAgent": ""
  },

  "permissions": [
    "android.permission.INTERNET",
    "android.permission.VIBRATE",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CAMERA",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.RECORD_AUDIO"
  ]
}
    """.trimIndent()

    // 简化的配置生成器（不带权限）
    fun getSimpleConfigFile(packageName: String, appName: String, targetUrl: String): String = """
{
  "name": "$appName",
  "package": "$packageName",
  "versionName": "1.0.0",
  "versionCode": 1,
  
  "orientation": "portrait",
  "fullscreen": false,
  "targetUrl": "$targetUrl",
  
  "statusBar": {
    "backgroundColor": "#FFFFFF",
    "style": "dark",
    "translucent": false,
    "hidden": false
  },
  
  "webview": {
    "zoomEnabled": false,
    "javascriptEnabled": true,
    "domStorageEnabled": true,
    "allowFileAccess": true,
    "textZoom": 100
  },

  "permissions": [
    "android.permission.INTERNET"
  ]
}
    """.trimIndent()

    // 用于演示多种状态栏配置的示例
    fun getStatusBarDemoConfig(packageName: String, appName: String): String = """
{
  "name": "$appName 状态栏演示",
  "package": "$packageName",
  "versionName": "1.0.0",
  "versionCode": 1,
  
  "orientation": "portrait",
  "fullscreen": false,
  "targetUrl": "index.html",
  
  "statusBar": {
    "backgroundColor": "#FF5722",
    "style": "light",
    "translucent": true,
    "hidden": false
  },
  
  "webview": {
    "zoomEnabled": false,
    "javascriptEnabled": true,
    "domStorageEnabled": true,
    "allowFileAccess": true,
    "textZoom": 100
  },

  "permissions": [
    "android.permission.INTERNET"
  ]
}
    """.trimIndent()
}