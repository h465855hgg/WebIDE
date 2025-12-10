// WebApp JavaScript API - 浏览器/WebView 兼容层
(function() {
    if (window.WebApp) return;

    // 环境检测
    const isWebView = typeof window.Android !== 'undefined';
    const isBrowser = !isWebView;

    // 浏览器中模拟的 NativeAPI
    const BrowserNativeAPI = {
        // ========== Toast 模拟 ==========
        toast: function(message) {
            if (window.alert) {
                alert(message);
            } else {
                console.log('Toast:', message);
            }
        },

        // ========== 震动模拟 ==========
        vibrate: function(duration = 100) {
            // 浏览器中无法震动，使用控制台日志
            console.log('Vibrate:', duration + 'ms');
        },

        // ========== 设备信息模拟 ==========
        getDeviceInfo: async function() {
            return {
                model: navigator.userAgent.match(/\(([^)]+)\)/)?.[1] || 'Browser',
                manufacturer: 'Browser',
                brand: 'Browser',
                device: 'Browser',
                product: 'Browser',
                androidVersion: 'N/A',
                sdkInt: 0,
                screenWidth: screen.width,
                screenHeight: screen.height,
                density: window.devicePixelRatio || 1,
                locale: navigator.language || 'zh-CN',
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                wifiSSID: 'N/A',
                wifiBSSID: 'N/A',
                wifiRSSI: 0,
                macAddress: '00:00:00:00:00:00',
                internalStorageFree: 0,
                internalStorageTotal: 0,
                externalStorageFree: 0,
                externalStorageTotal: 0
            };
        },

        // ========== 剪贴板 API (浏览器原生) ==========
        copyToClipboard: async function(text) {
            try {
                await navigator.clipboard.writeText(text);
                console.log('Copied to clipboard:', text);
                return true;
            } catch (err) {
                // 备用方案：使用 document.execCommand
                const textArea = document.createElement('textarea');
                textArea.value = text;
                document.body.appendChild(textArea);
                textArea.select();
                const success = document.execCommand('copy');
                document.body.removeChild(textArea);
                return success;
            }
        },

        getFromClipboard: async function() {
            try {
                return await navigator.clipboard.readText();
            } catch (err) {
                console.warn('Clipboard read failed:', err);
                return '';
            }
        },

        // ========== 本地存储 (使用 localStorage) ==========
        saveStorage: function(key, value) {
            localStorage.setItem(key, value);
        },

        getStorage: function(key) {
            return localStorage.getItem(key) || '';
        },

        removeStorage: function(key) {
            localStorage.removeItem(key);
        },

        clearStorage: function() {
            localStorage.clear();
        },

        getAllStorage: function() {
            return { ...localStorage };
        },

        // ========== 文件系统模拟 ==========
        readFile: async function(path) {
            // 浏览器中只能读取网络文件或用户选择的文件
            console.warn('Browser cannot read local files directly');
            return '';
        },

        writeFile: async function(path, content) {
            // 浏览器中下载文件
            const blob = new Blob([content], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = path.split('/').pop() || 'file.txt';
            a.click();
            URL.revokeObjectURL(url);
            return true;
        },

        fileExists: function(path) {
            // 浏览器中无法检查文件是否存在
            return false;
        },

        deleteFile: function(path) {
            // 浏览器中无法删除文件
            return false;
        },

        listFiles: function(directory) {
            // 浏览器中无法列出目录
            return '[]';
        },

        // ========== 系统功能 (使用浏览器API) ==========
        openBrowser: function(url) {
            window.open(url, '_blank');
        },

        shareText: function(text) {
            if (navigator.share) {
                navigator.share({ text: text });
            } else {
                console.log('Share text:', text);
            }
        },

        callPhone: function(phoneNumber) {
            window.location.href = 'tel:' + phoneNumber;
        },

        sendSMS: function(phoneNumber, message) {
            window.location.href = 'sms:' + phoneNumber + '?body=' + encodeURIComponent(message);
        },

        sendEmail: function(email, subject, body) {
            window.location.href = 'mailto:' + email + '?subject=' + encodeURIComponent(subject) + '&body=' + encodeURIComponent(body);
        },

        openMap: function(latitude, longitude, label) {
            window.open(`https://maps.google.com/?q=${latitude},${longitude}(${encodeURIComponent(label)})`, '_blank');
        },

        // ========== 屏幕控制 ==========
        keepScreenOn: function(enable) {
            // 浏览器中无法控制屏幕常亮
            console.log('Keep screen on:', enable);
        },

        setScreenBrightness: function(brightness) {
            // 浏览器中无法控制亮度
            console.log('Set brightness:', brightness);
        },

        getScreenBrightness: function() {
            return 0.5; // 默认值
        },

        setVolume: function(volume) {
            // 浏览器中无法控制系统音量
            console.log('Set volume:', volume);
        },

        // ========== 权限管理 ==========
        requestPermission: async function(permission) {
            try {
                // 浏览器权限请求
                const permissionMap = {
                    'android.permission.CAMERA': { name: 'camera' },
                    'android.permission.RECORD_AUDIO': { name: 'microphone' },
                    'android.permission.GEOLOCATION': { name: 'geolocation' }
                };

                const browserPermission = permissionMap[permission];
                if (browserPermission && navigator.permissions) {
                    const result = await navigator.permissions.query(browserPermission);
                    return result.state === 'granted';
                }

                // 尝试请求媒体设备权限
                if (permission === 'android.permission.CAMERA' || permission === 'android.permission.RECORD_AUDIO') {
                    const stream = await navigator.mediaDevices.getUserMedia({
                        video: permission === 'android.permission.CAMERA',
                        audio: permission === 'android.permission.RECORD_AUDIO'
                    });
                    stream.getTracks().forEach(track => track.stop());
                    return true;
                }

                return false;
            } catch (error) {
                console.warn('Permission request failed:', error);
                return false;
            }
        },

        hasPermission: async function(permission) {
            return await this.requestPermission(permission);
        },

        openAppSettings: function() {
            // 浏览器中无法打开应用设置
            console.log('Cannot open app settings in browser');
        },

        // ========== 应用配置 ==========
        getAppConfig: function() {
            return JSON.stringify({
                name: 'WebApp Browser Version',
                package: 'com.example.browser',
                versionName: '1.0.0',
                versionCode: 1,
                orientation: 'portrait',
                fullscreen: false,
                statusBar: {
                    backgroundColor: '#FFFFFF',
                    style: 'dark',
                    translucent: false,
                    hidden: false
                }
            });
        },

        reloadApp: function() {
            location.reload();
        },

        exitApp: function() {
            // 浏览器中无法退出应用
            console.log('Cannot exit app in browser');
        },

        // ========== 传感器模拟 ==========
        startSensor: function(sensorType, callback) {
            // 浏览器传感器 API
            if (window.DeviceMotionEvent && (sensorType === 'accelerometer' || sensorType === 'gyroscope')) {
                const handler = (event) => {
                    const data = {
                        accelerometer: event.acceleration ? [event.acceleration.x, event.acceleration.y, event.acceleration.z] : [0, 0, 0],
                        gyroscope: event.rotationRate ? [event.rotationRate.alpha, event.rotationRate.beta, event.rotationRate.gamma] : [0, 0, 0]
                    };
                    callback(data[sensorType] || [0, 0, 0]);
                };
                window.addEventListener('devicemotion', handler);
                return () => window.removeEventListener('devicemotion', handler);
            }

            if (window.DeviceOrientationEvent && sensorType === 'magnetometer') {
                const handler = (event) => {
                    callback([event.alpha || 0, event.beta || 0, event.gamma || 0]);
                };
                window.addEventListener('deviceorientation', handler);
                return () => window.removeEventListener('deviceorientation', handler);
            }

            console.warn('Sensor not supported in browser:', sensorType);
            return null;
        },

        stopSensor: function() {
            // 停止所有传感器监听
            // 需要在具体使用时记录并移除监听器
        },

        // ========== 日期时间 ==========
        getCurrentTimeMillis: function() {
            return Date.now();
        },

        formatDate: function(timestamp, format) {
            const date = new Date(timestamp);
            const map = {
                'yyyy': date.getFullYear(),
                'MM': String(date.getMonth() + 1).padStart(2, '0'),
                'dd': String(date.getDate()).padStart(2, '0'),
                'HH': String(date.getHours()).padStart(2, '0'),
                'mm': String(date.getMinutes()).padStart(2, '0'),
                'ss': String(date.getSeconds()).padStart(2, '0')
            };
            return format.replace(/yyyy|MM|dd|HH|mm|ss/g, match => map[match]);
        }
    };

    // WebView 中的 NativeAPI (通过 Android 接口)
    const WebViewNativeAPI = {
        // 请求回调管理
        _callbacks: {},

        // 响应处理
        _onResponse: function(id, b64) {
            const cb = this._callbacks[id];
            if (!cb) return;

            try {
                const jsonStr = decodeURIComponent(escape(atob(b64)));
                const result = JSON.parse(jsonStr);
                if (result.success) {
                    cb.resolve(result.data);
                } else {
                    cb.reject(new Error(result.data));
                }
            } catch (e) {
                cb.reject(e);
            }

            delete this._callbacks[id];
        },

        // 通用调用方法
        _call: function(method, ...args) {
            return new Promise((resolve, reject) => {
                if (!window.Android || typeof window.Android[method] !== 'function') {
                    reject(new Error(`Method ${method} not available`));
                    return;
                }

                const callbackId = 'cb_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
                this._callbacks[callbackId] = { resolve, reject };

                try {
                    window.Android[method](...args, callbackId);
                } catch (e) {
                    delete this._callbacks[callbackId];
                    reject(e);
                }
            });
        },

        // ========== 基础 API ==========

        // 显示 Toast
        toast: function(message) {
            if (window.Android && window.Android.showToast) {
                window.Android.showToast(message);
            } else {
                console.log('Toast:', message);
            }
        },

        // 震动
        vibrate: function(duration = 100) {
            if (window.Android && window.Android.vibrate) {
                window.Android.vibrate(duration);
            }
        },

        // 获取设备信息
        getDeviceInfo: async function() {
            if (window.Android && window.Android.getDeviceInfo) {
                const infoStr = window.Android.getDeviceInfo();
                try {
                    return JSON.parse(infoStr);
                } catch (e) {
                    console.error('Failed to parse device info:', e);
                    return null;
                }
            }
            return null;
        },

        // ========== 异步 API ==========

        // 剪贴板 - 读取
        getClipboard: function() {
            return this._call('getFromClipboard');
        },

        // 文件系统 - 读取
        readFile: function(path) {
            return this._call('readFile', path);
        },

        // 文件系统 - 写入
        writeFile: function(path, content) {
            return this._call('writeFile', path, content);
        },

        // 文件系统 - 列表
        listFiles: function(directory) {
            return this._call('listFiles', directory);
        },

        // 权限请求
        requestPermission: function(permission) {
            return this._call('requestPermission', permission);
        },

        // 传感器控制
        startSensor: function(sensorType) {
            return this._call('startSensor', sensorType);
        },

        // ========== 同步 API ==========

        // 剪贴板 - 写入
        copyToClipboard: function(text) {
            if (window.Android && window.Android.copyToClipboard) {
                window.Android.copyToClipboard(text);
            }
        },

        // 存储 - 保存
        saveStorage: function(key, value) {
            if (window.Android && window.Android.saveStorage) {
                window.Android.saveStorage(key, value);
            }
        },

        // 存储 - 读取
        getStorage: function(key) {
            if (window.Android && window.Android.getStorage) {
                return window.Android.getStorage(key);
            }
            return '';
        },

        // 存储 - 删除
        removeStorage: function(key) {
            if (window.Android && window.Android.removeStorage) {
                window.Android.removeStorage(key);
            }
        },

        // 存储 - 清空
        clearStorage: function() {
            if (window.Android && window.Android.clearStorage) {
                window.Android.clearStorage();
            }
        },

        // 存储 - 获取全部
        getAllStorage: function() {
            if (window.Android && window.Android.getAllStorage) {
                const allStr = window.Android.getAllStorage();
                try {
                    return JSON.parse(allStr);
                } catch (e) {
                    return {};
                }
            }
            return {};
        },

        // 文件存在检查
        fileExists: function(path) {
            if (window.Android && window.Android.fileExists) {
                return window.Android.fileExists(path);
            }
            return false;
        },

        // 删除文件
        deleteFile: function(path) {
            if (window.Android && window.Android.deleteFile) {
                return window.Android.deleteFile(path);
            }
            return false;
        },

        // 权限检查
        hasPermission: function(permission) {
            if (window.Android && window.Android.hasPermission) {
                return window.Android.hasPermission(permission);
            }
            return false;
        },

        // 获取应用配置
        getAppConfig: function() {
            if (window.Android && window.Android.getAppConfig) {
                const configStr = window.Android.getAppConfig();
                try {
                    return JSON.parse(configStr);
                } catch (e) {
                    return {};
                }
            }
            return {};
        },

        // 获取当前时间戳
        getCurrentTimeMillis: function() {
            if (window.Android && window.Android.getCurrentTimeMillis) {
                return window.Android.getCurrentTimeMillis();
            }
            return Date.now();
        },

        // 格式化日期
        formatDate: function(timestamp, format) {
            if (window.Android && window.Android.formatDate) {
                return window.Android.formatDate(timestamp, format);
            }
            return new Date(timestamp).toLocaleString();
        },

        // ========== 系统功能 ==========

        // 打开浏览器
        openBrowser: function(url) {
            if (window.Android && window.Android.openBrowser) {
                window.Android.openBrowser(url);
            }
        },

        // 分享文本
        shareText: function(text) {
            if (window.Android && window.Android.shareText) {
                window.Android.shareText(text);
            }
        },

        // 拨打电话
        callPhone: function(phoneNumber) {
            if (window.Android && window.Android.callPhone) {
                window.Android.callPhone(phoneNumber);
            }
        },

        // 发送短信
        sendSMS: function(phoneNumber, message) {
            if (window.Android && window.Android.sendSMS) {
                window.Android.sendSMS(phoneNumber, message);
            }
        },

        // 发送邮件
        sendEmail: function(email, subject, body) {
            if (window.Android && window.Android.sendEmail) {
                window.Android.sendEmail(email, subject, body);
            }
        },

        // 打开地图
        openMap: function(latitude, longitude, label) {
            if (window.Android && window.Android.openMap) {
                window.Android.openMap(latitude, longitude, label);
            }
        },

        // 保持屏幕常亮
        keepScreenOn: function(enable) {
            if (window.Android && window.Android.keepScreenOn) {
                window.Android.keepScreenOn(enable);
            }
        },

        // 设置屏幕亮度
        setScreenBrightness: function(brightness) {
            if (window.Android && window.Android.setScreenBrightness) {
                window.Android.setScreenBrightness(brightness);
            }
        },

        // 获取屏幕亮度
        getScreenBrightness: function() {
            if (window.Android && window.Android.getScreenBrightness) {
                return window.Android.getScreenBrightness();
            }
            return -1;
        },

        // 设置音量
        setVolume: function(volume) {
            if (window.Android && window.Android.setVolume) {
                window.Android.setVolume(volume);
            }
        },

        // 停止传感器
        stopSensor: function() {
            if (window.Android && window.Android.stopSensor) {
                window.Android.stopSensor();
            }
        },

        // 打开应用设置
        openAppSettings: function() {
            if (window.Android && window.Android.openAppSettings) {
                window.Android.openAppSettings();
            }
        },

        // 重新加载应用
        reloadApp: function() {
            if (window.Android && window.Android.reloadApp) {
                window.Android.reloadApp();
            }
        },

        // 退出应用
        exitApp: function() {
            if (window.Android && window.Android.exitApp) {
                window.Android.exitApp();
            }
        }
    };

    // 统一 NativeAPI
    window.NativeAPI = isWebView ? WebViewNativeAPI : BrowserNativeAPI;

    // 注册全局响应处理器（仅 WebView 需要）
    if (isWebView) {
        window.onAndroidResponse = function(id, b64) {
            window.NativeAPI._onResponse(id, b64);
        };
    }

    // 统一的摄像头 API
    window.CameraAPI = {
        /**
         * 获取摄像头流（兼容浏览器和 WebView）
         * @param {Object} constraints - 媒体约束
         * @returns {Promise<MediaStream>} 媒体流
         */
        getMediaStream: async function(constraints = { video: true, audio: false }) {
            try {
                // 在浏览器中，使用标准的 getUserMedia
                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                    return await navigator.mediaDevices.getUserMedia(constraints);
                }

                // 旧版浏览器的兼容性
                const legacyGetUserMedia =
                    navigator.getUserMedia ||
                    navigator.webkitGetUserMedia ||
                    navigator.mozGetUserMedia ||
                    navigator.msGetUserMedia;

                if (legacyGetUserMedia) {
                    return new Promise((resolve, reject) => {
                        legacyGetUserMedia.call(navigator, constraints, resolve, reject);
                    });
                }

                throw new Error('Camera API not supported in this browser');

            } catch (error) {
                console.error('Camera error:', error);

                // 在 WebView 中，检查权限
                if (window.Android) {
                    try {
                        // 尝试请求摄像头权限
                        const hasPermission = await window.NativeAPI.hasPermission('android.permission.CAMERA');
                        if (!hasPermission) {
                            await window.NativeAPI.requestPermission('android.permission.CAMERA');
                        }

                        // 再次尝试
                        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                            return await navigator.mediaDevices.getUserMedia(constraints);
                        }
                    } catch (permissionError) {
                        console.error('Permission error:', permissionError);
                    }
                }

                throw error;
            }
        },

        /**
         * 拍照
         * @param {Object} options - 选项
         * @returns {Promise<string>} base64 图片数据
         */
        takePhoto: async function(options = {}) {
            try {
                // 尝试使用摄像头
                const stream = await this.getMediaStream({
                    video: {
                        facingMode: options.facingMode || 'environment',
                        width: options.width || 1280,
                        height: options.height || 720
                    },
                    audio: false
                });

                // 创建视频元素
                const video = document.createElement('video');
                video.srcObject = stream;
                video.play();

                // 等待视频就绪
                await new Promise(resolve => {
                    video.onloadedmetadata = resolve;
                });

                // 创建画布
                const canvas = document.createElement('canvas');
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
                const ctx = canvas.getContext('2d');

                // 绘制视频帧到画布
                ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

                // 停止视频流
                stream.getTracks().forEach(track => track.stop());

                // 返回 base64 图片
                return canvas.toDataURL(options.format || 'image/jpeg', options.quality || 0.9);

            } catch (error) {
                console.error('Take photo error:', error);

                // 备用方案：使用文件输入
                if (options.fallbackToFileInput !== false) {
                    return await this._takePhotoWithFileInput();
                }

                throw error;
            }
        },

        /**
         * 使用文件输入拍照（兼容所有环境）
         * @private
         */
        _takePhotoWithFileInput: function() {
            return new Promise((resolve, reject) => {
                const input = document.createElement('input');
                input.type = 'file';
                input.accept = 'image/*';
                input.capture = 'environment'; // 后置摄像头

                input.onchange = (e) => {
                    const file = e.target.files[0];
                    if (!file) {
                        reject(new Error('No file selected'));
                        return;
                    }

                    const reader = new FileReader();
                    reader.onload = (event) => {
                        resolve(event.target.result);
                    };
                    reader.onerror = reject;
                    reader.readAsDataURL(file);
                };

                // 触发点击
                input.click();
            });
        }
    };

    // 统一的文件系统 API
    window.FileSystemAPI = {
        /**
         * 读取文件（兼容浏览器和 WebView）
         */
        readFile: async function(path) {
            if (window.Android) {
                // WebView 中
                return await window.NativeAPI.readFile(path);
            } else {
                // 浏览器中：使用 Fetch API 读取网络文件
                try {
                    const response = await fetch(path);
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    return await response.text();
                } catch (error) {
                    console.warn('Cannot read file:', path, error);
                    return '';
                }
            }
        },

        /**
         * 写入文件（兼容浏览器和 WebView）
         */
        writeFile: async function(path, content) {
            if (window.Android) {
                // WebView 中
                return await window.NativeAPI.writeFile(path, content);
            } else {
                // 浏览器中：下载文件
                const blob = new Blob([content], { type: 'text/plain' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = path.split('/').pop() || 'file.txt';
                a.click();
                URL.revokeObjectURL(url);
                return true;
            }
        }
    };

    // 环境检测函数
    window.isWebView = function() {
        return typeof window.Android !== 'undefined';
    };

    window.isBrowser = function() {
        return !window.isWebView();
    };

    // 自动初始化
    document.addEventListener('DOMContentLoaded', function() {
        console.log('NativeAPI initialized in', window.isWebView() ? 'WebView' : 'Browser');
        console.log('Available APIs:', Object.keys(window.NativeAPI));
    });

})();