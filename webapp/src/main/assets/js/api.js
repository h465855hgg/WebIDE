// 1. 初始化回调池
window.requestCallbacks = {};

// 2. 统一回调入口 (Java调用)
window.onAndroidResponse = function(callbackId, base64Response) {
    const callback = window.requestCallbacks[callbackId];
    if (!callback) return;

    try {
        // Base64 解码 + UTF8 修正
        const jsonString = decodeURIComponent(escape(window.atob(base64Response)));
        const response = JSON.parse(jsonString);

        if (response.success) {
            // 智能解析 JSON 字符串
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
        callback.reject("Native Bridge Error: " + e.message);
    }
    delete window.requestCallbacks[callbackId];
};

// 3. 辅助函数：生成 Promise
const callAsync = (methodName, ...args) => {
    return new Promise((resolve, reject) => {
        if (!window.Android || !window.Android[methodName]) {
            return reject(`Method ${methodName} not found`);
        }
        const callbackId = 'cb_' + Date.now() + Math.random();
        window.requestCallbacks[callbackId] = { resolve, reject };
        // 将参数展开，最后追加 callbackId
        window.Android[methodName](...args, callbackId);
    });
};

// 4. 对外暴露的 API 对象
window.NativeAPI = {
    // --- 基础 ---
    toast: (msg) => window.Android?.showToast(msg),

    vibrate: (ms = 50) => window.Android?.vibrate(ms),

    getDeviceInfo: () => {
        if (!window.Android) return null;
        return JSON.parse(window.Android.getDeviceInfo());
    },

    // --- 剪贴板 ---
    clipboard: {
        copy: (text) => window.Android?.copyToClipboard(text),
        read: () => callAsync('getFromClipboard') // 异步读取
    },

    // --- 本地存储 (同步) ---
    storage: {
        set: (key, value) => window.Android?.saveStorage(key, typeof value === 'object' ? JSON.stringify(value) : value),
        get: (key) => {
            const val = window.Android?.getStorage(key);
            try { return JSON.parse(val); } catch(e) { return val; }
        }
    },

    // --- 网络请求 (异步) ---
    http: {
        get: (url) => callAsync('request', url, 'GET'),
        post: (url) => callAsync('request', url, 'POST') // 需Java端完善POST支持
    }
};

// 兼容旧代码
window.nativeFetch = window.NativeAPI.http.get;