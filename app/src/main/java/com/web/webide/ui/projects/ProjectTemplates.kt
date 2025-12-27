/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.web.webide.ui.projects

object ProjectTemplates {

    val normalIndexHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Web Project</title>
</head>
<body>
    <h1>Hello World</h1>
    <button id="btn">点击测试</button>
    <p id="log"></p>
    <script src="js/script.js"></script>
</body>
</html>
    """.trimIndent()

    val normalCss = """
body { padding: 20px; font-family: sans-serif; }
button { padding: 10px 20px; font-size: 16px; margin-top: 10px; }
    """.trimIndent()

    val normalJs = """
document.getElementById('btn').onclick = function() {
    document.getElementById('log').innerText = '时间: ' + new Date().toLocaleTimeString();
};
    """.trimIndent()


    val apiJs = """
(function() {
    const isAndroid = !!(window.Android && window.Android.httpRequest);
    
    // 1. Native 通信 (Android)
    const callbacks = {};
    window.onAndroidResponse = function(id, b64) {
        const cb = callbacks[id];
        if (cb) {
            try {
                const json = JSON.parse(decodeURIComponent(escape(window.atob(b64))));
                json.success ? cb.resolve(json.data) : cb.reject(json.data);
            } catch (e) { cb.reject("Error"); }
            delete callbacks[id];
        }
    };
    const call = (method, ...args) => new Promise((resolve, reject) => {
        if (!isAndroid) return reject("Browser Mode");
        const id = 'cb_' + Math.random().toString(36).substr(2, 9);
        callbacks[id] = { resolve, reject };
        window.Android[method](...args, id);
    });

    // 2. 浏览器模拟 (Browser Polyfill)
    const toastInBrowser = (msg) => {
        const d = document.createElement('div');
        d.style.cssText = "position:fixed;bottom:20%;left:50%;transform:translateX(-50%);background:rgba(0,0,0,0.7);color:#fff;padding:8px 16px;border-radius:4px;font-size:14px;z-index:999;";
        d.innerText = msg;
        document.body.appendChild(d);
        setTimeout(() => d.remove(), 2000);
    };

    // 3. 对外接口
    window.App = {
        ui: {
            toast: (msg) => isAndroid ? window.Android.showToast(msg) : toastInBrowser(msg),
            notification: (id, t, c) => isAndroid && window.Android.showNotification(id, t, c)
        },
        sys: {
            vibrate: (ms) => isAndroid ? window.Android.vibrate(ms) : navigator.vibrate?.(ms),
            copy: (t) => isAndroid ? window.Android.copyToClipboard(t) : navigator.clipboard.writeText(t)
        },
        file: {
            write: (p, c) => isAndroid ? call('writeFile', p, c) : localStorage.setItem(p,c),
            read: (p) => isAndroid ? call('readFile', p) : Promise.resolve(localStorage.getItem(p))
        },
        http: {
            get: async (url) => isAndroid ? JSON.parse(await call('httpRequest', 'GET', url, '{}', '')) : (await fetch(url)).text()
        }
    };
})();
    """.trimIndent()

    val webAppIndexHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WebApp</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <h3>WebApp 演示</h3>
    
    <!-- 只有一个按钮 -->
    <button onclick="testToast()">点击弹出 Toast</button>

    <script src="js/api.js"></script>
    <script src="js/index.js"></script>
</body>
</html>
    """.trimIndent()


    val webAppCss = """
body { padding: 20px; font-family: sans-serif; }
button { 
    padding: 12px 24px; 
    font-size: 16px; 
    background-color: #007bff; 
    color: white; 
    border: none; 
    border-radius: 5px; 
}
button:active { background-color: #0056b3; }
    """.trimIndent()


    val webAppIndexJs = """
function testToast() {
    // 这行代码在 Android 上调用原生 Toast，在浏览器上显示模拟提示
    App.ui.toast("Hello World! 这是一个 Toast");
}
    """.trimIndent()


    fun getConfigFile(packageName: String, appName: String, targetUrl: String): String = """
{
  "name": "$appName",
  "package": "$packageName",
  "versionName": "1.0.0",
  "versionCode": 1,
  "orientation": "0",
  "fullscreen": false,
  "targetUrl": "$targetUrl",
  "statusBar": {
    "backgroundColor": "#FFFFFF",
    "style": "dark"
  },
  "webview": {
    "userAgent": "",
    "zoomEnabled": false,
    "textZoom": 100
  },
  "permissions": [
    "android.permission.INTERNET",
    "android.permission.VIBRATE"
  ]
}
""".trimIndent()
}