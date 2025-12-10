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
window.requestCallbacks = {};
window.onAndroidResponse = function(id, b64) {
    const cb = window.requestCallbacks[id];
    if(!cb) return;
    try {
        const res = JSON.parse(decodeURIComponent(escape(window.atob(b64))));
        res.success ? cb.resolve(res.data) : cb.reject(res.data);
    } catch(e) { cb.reject(e.message); }
    delete window.requestCallbacks[id];
};
const call = (m, ...a) => new Promise((res, rej) => {
    if(!window.Android || !window.Android[m]) return rej("Native API not found");
    const id = 'cb_'+Math.random();
    window.requestCallbacks[id] = {resolve: res, reject: rej};
    window.Android[m](...a, id);
});
window.NativeAPI = {
    toast: (m) => window.Android?.showToast(m),
    vibrate: (ms=50) => window.Android?.vibrate(ms),
    share: (t) => window.Android?.shareText(t),
    openBrowser: (u) => window.Android?.openBrowser(u),
    info: () => { try { return JSON.parse(window.Android.getDeviceInfo()); } catch(e){ return null; } }
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

    // 🚀 通用配置生成器：支持传入 targetUrl
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
    "android.permission.WRITE_EXTERNAL_STORAGE"
  ]
}
    """.trimIndent()
}