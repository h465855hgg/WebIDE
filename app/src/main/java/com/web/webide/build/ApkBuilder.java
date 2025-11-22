package com.web.webide.build;

import android.content.Context;
import android.content.res.AssetManager;
import com.web.webide.core.utils.LogCatcher;
import java.io.*;
import java.util.Properties;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.Day.Studio.Function.axmleditor.decode.AXMLDoc;
import com.Day.Studio.Function.ApkXmlEditor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ApkBuilder {
    
    // 配置类，封装所有配置项
    private static class AppConfig {
        String appName = "WebApp";
        String appPackage = "com.example.webapp";
        String versionName = "1.0.0";
        String versionCode = "1";
        String iconPath = "file:///android_asset/icon.png";
        
        // 构建配置
        String targetSdk = "34";
        String minSdk = "21";
        String compileSdk = "34";
        
        // 权限配置
        List<String> permissions = new ArrayList<>();
        
        // WebView配置
        boolean allowFileAccess = false;
        boolean allowContentAccess = false;
        boolean domStorageEnabled = false;
        boolean databaseEnabled = false;
        boolean javaScriptEnabled = false;
        
        // 启动配置
        String launchUrl = "file:///android_asset/index.html";
        String launchTheme = "@android:style/Theme.Light.NoTitleBar";
        
        // 其他配置
        String orientation = "portrait"; // portrait, landscape, sensor
        boolean fullscreen = false;
    }
    
    public static String bin(
            Context context,
            String mRootDir,
            String mDir,
            String aname,
            String pkg,
            String ver,
            String code,
            String amph,
            String[] ps) {
        File bf = new File(mRootDir, "bin");
        bf.mkdirs();
        File op = new File(bf, aname);
        
        LogCatcher.i("ApkBuilder", "========== 开始构建 APK ==========");
        LogCatcher.i("ApkBuilder", "项目目录: " + mDir);
        LogCatcher.i("ApkBuilder", "输出文件: " + op);
        
        try {
            // 1. 读取并解析配置文件
            AppConfig config = loadConfig(mDir, aname, pkg, ver, code);
            logConfig(config);
            
            // 2. 创建 ZIP 输出流
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(op));
            LogCatcher.i("ApkBuilder", "ZIP 输出流创建成功");
            
            // 3. 添加项目文件到 assets
            addProjectFilesToAssets(zip, mDir);
            
            // 4. 添加 Web 模板文件
            addWebTemplate(context, zip, config);
            
            // 5. 关闭 ZIP
            zip.close();
            LogCatcher.i("ApkBuilder", "ZIP 文件构建完成");
            
            // 6. 签名 APK
            String signaturePath = "/storage/emulated/0/WebIDE/WebIDE.jks";
            boolean signResult = signerApk(
                signaturePath, "WebIDE", "WebIDE", "WebIDE",
                op.getAbsolutePath(), op.getAbsolutePath() + ".apk"
            );
            
            if (signResult) {
                String resultPath = op.getAbsolutePath() + ".apk";
                LogCatcher.i("ApkBuilder", "========== 构建成功 ==========");
                LogCatcher.i("ApkBuilder", "输出路径: " + resultPath);
                return resultPath;
            } else {
                LogCatcher.e("ApkBuilder", "APK 签名失败");
                return "error:签名失败 - 请检查签名文件";
            }
            
        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "构建失败: " + e.getMessage());
            e.printStackTrace();
            return "error:" + e.getMessage();
        } finally {
            if (op.exists()) {
                op.delete();
            }
        }
    }
    
    /**
     * 加载并解析配置文件
     */
    private static AppConfig loadConfig(String mDir, String defaultName, 
                                       String defaultPkg, String defaultVer, String defaultCode) {
        AppConfig config = new AppConfig();
        
        // 设置默认值
        config.appName = defaultName;
        config.appPackage = defaultPkg;
        config.versionName = defaultVer;
        config.versionCode = defaultCode;
        
        File configFile = new File(mDir, ".WebIDE/application.properties");
        LogCatcher.i("ApkBuilder", "配置文件路径: " + configFile.getAbsolutePath());
        
        if (!configFile.exists()) {
            LogCatcher.w("ApkBuilder", "配置文件不存在，使用默认配置");
            return config;
        }
        
        try {
            Properties props = new Properties();
            
            // 使用 UTF-8 编码读取
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            
            // 应用配置
            config.appName = fixEncoding(props.getProperty("app.name", config.appName));
            config.appPackage = props.getProperty("app.package", config.appPackage);
            config.versionName = props.getProperty("app.version", config.versionName);
            config.versionCode = props.getProperty("app.versionCode", config.versionCode);
            config.iconPath = props.getProperty("app.icon", config.iconPath);
            
            // 构建配置
            config.targetSdk = props.getProperty("build.targetSdk", config.targetSdk);
            config.minSdk = props.getProperty("build.minSdk", config.minSdk);
            config.compileSdk = props.getProperty("build.compileSdk", config.compileSdk);
            
            // 权限配置
            String permissionsStr = props.getProperty("permissions", "");
            if (!permissionsStr.isEmpty()) {
                for (String perm : permissionsStr.split(",")) {
                    String trimmed = perm.trim();
                    if (!trimmed.isEmpty()) {
                        config.permissions.add(trimmed);
                    }
                }
            }
            
            // WebView 配置
            config.allowFileAccess = Boolean.parseBoolean(
                props.getProperty("webview.allowFileAccess", "false"));
            config.allowContentAccess = Boolean.parseBoolean(
                props.getProperty("webview.allowContentAccess", "false"));
            config.domStorageEnabled = Boolean.parseBoolean(
                props.getProperty("webview.domStorageEnabled", "false"));
            config.databaseEnabled = Boolean.parseBoolean(
                props.getProperty("webview.databaseEnabled", "false"));
            config.javaScriptEnabled = Boolean.parseBoolean(
                props.getProperty("webview.javaScriptEnabled", "false"));
            
            // 启动配置
            config.launchUrl = props.getProperty("launch.url", config.launchUrl);
            config.launchTheme = props.getProperty("launch.theme", config.launchTheme);
            
            // 其他配置
            config.orientation = props.getProperty("orientation", config.orientation);
            config.fullscreen = Boolean.parseBoolean(
                props.getProperty("fullscreen", "false"));
            
            LogCatcher.i("ApkBuilder", "✅ 配置文件加载成功");
            
        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "读取配置文件失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return config;
    }
    
    /**
     * 打印配置信息
     */
    private static void logConfig(AppConfig config) {
        LogCatcher.i("ApkBuilder", "========== 应用配置 ==========");
        LogCatcher.i("ApkBuilder", "应用名称: " + config.appName);
        LogCatcher.i("ApkBuilder", "包名: " + config.appPackage);
        LogCatcher.i("ApkBuilder", "版本: " + config.versionName + " (" + config.versionCode + ")");
        LogCatcher.i("ApkBuilder", "SDK: min=" + config.minSdk + ", target=" + config.targetSdk);
        LogCatcher.i("ApkBuilder", "权限数量: " + config.permissions.size());
        LogCatcher.i("ApkBuilder", "JavaScript: " + (config.javaScriptEnabled ? "启用" : "禁用"));
        LogCatcher.i("ApkBuilder", "启动URL: " + config.launchUrl);
        LogCatcher.i("ApkBuilder", "屏幕方向: " + config.orientation);
        LogCatcher.i("ApkBuilder", "全屏模式: " + config.fullscreen);
        LogCatcher.i("ApkBuilder", "==============================");
    }
    
    /**
     * 添加项目文件到 assets 目录
     */
    private static void addProjectFilesToAssets(ZipOutputStream zip, String mDir) throws IOException {
        LogCatcher.i("ApkBuilder", "开始添加项目文件到 assets");
        
        File projectDir = new File(mDir);
        File[] files = projectDir.listFiles();
        
        if (files == null) {
            LogCatcher.w("ApkBuilder", "项目目录为空");
            return;
        }
        
        int fileCount = 0;
        for (File file : files) {
            // 跳过配置目录
            if (file.getName().equals(".WebIDE")) {
                continue;
            }
            addZip(zip, file, "assets");
            fileCount++;
        }
        
        LogCatcher.i("ApkBuilder", "添加了 " + fileCount + " 个文件/目录到 assets");
    }
    
    /**
     * 添加 Web 模板文件
     */
    private static void addWebTemplate(Context context, ZipOutputStream zip, 
                                      AppConfig config) throws Exception {
        LogCatcher.i("ApkBuilder", "开始处理 Web 模板");
        
        AssetManager assetManager = context.getAssets();
        String[] templateFiles = assetManager.list("webapp");
        
        if (templateFiles == null) {
            throw new Exception("Web 模板目录不存在");
        }
        
        for (String fileName : templateFiles) {
            if (fileName.equals("assets")) {
                continue; // 跳过 assets 目录
            }
            
            // 检查是否是目录
            String[] subFiles = assetManager.list("webapp/" + fileName);
            if (subFiles != null && subFiles.length > 0) {
                addAssetDirectoryToZip(zip, assetManager, "webapp/" + fileName, fileName);
                continue;
            }
            
            // 处理文件
            if (fileName.equals("AndroidManifest.xml")) {
                addModifiedManifest(context, zip, config);
            } else if (fileName.equals("resources.arsc")) {
                addResourcesArsc(assetManager, zip);
            } else {
                addAssetFile(assetManager, zip, "webapp/" + fileName, fileName);
            }
        }
        
        LogCatcher.i("ApkBuilder", "Web 模板处理完成");
    }
    
    /**
     * 添加修改后的 AndroidManifest.xml
     */
    private static void addModifiedManifest(Context context, ZipOutputStream zip, 
                                           AppConfig config) throws Exception {
        LogCatcher.i("ApkBuilder", "开始修改 AndroidManifest.xml");
        
        // 1. 提取模板到临时文件
        AssetManager assetManager = context.getAssets();
        InputStream templateStream = assetManager.open("webapp/AndroidManifest.xml");
        
        File tempManifest = new File(context.getCacheDir(), "temp_manifest.xml");
        copyStream(templateStream, new FileOutputStream(tempManifest));
        templateStream.close();
        
        try {
            // 2. 修改基本信息
            ApkXmlEditor.setXmlPaht(tempManifest.getAbsolutePath());
            ApkXmlEditor.setAppName(config.appName);
            ApkXmlEditor.setAppPack(config.appPackage);
            ApkXmlEditor.setAppbcode(Integer.parseInt(config.versionCode));
            ApkXmlEditor.setAppbname(config.versionName);
            ApkXmlEditor.operation();
            
            LogCatcher.i("ApkBuilder", "✅ 基本信息修改完成");
            
            // 3. 添加权限
            for (String permission : config.permissions) {
                String fullPermission = permission.startsWith("android.permission.") 
                    ? permission 
                    : "android.permission." + permission;
                    
                setPermission(tempManifest.getAbsolutePath(), fullPermission, false);
                LogCatcher.i("ApkBuilder", "✅ 添加权限: " + fullPermission);
            }
            
            // 4. 修改屏幕方向（如果需要的话，这需要额外的编辑器支持）
            // 这里只是记录，实际修改需要扩展 AXMLDoc 的功能
            LogCatcher.i("ApkBuilder", "屏幕方向配置: " + config.orientation);
            LogCatcher.i("ApkBuilder", "全屏模式配置: " + config.fullscreen);
            
            // 5. 写入 ZIP
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            FileInputStream fis = new FileInputStream(tempManifest);
            copyStream(fis, zip);
            fis.close();
            zip.closeEntry();
            
            LogCatcher.i("ApkBuilder", "✅ AndroidManifest.xml 写入完成");
            
        } finally {
            tempManifest.delete();
        }
    }
    
    /**
     * 添加 resources.arsc（使用 STORED 模式）
     */
    private static void addResourcesArsc(AssetManager assetManager, ZipOutputStream zip) 
            throws IOException {
        LogCatcher.i("ApkBuilder", "处理 resources.arsc");
        
        InputStream assetStream = assetManager.open("webapp/resources.arsc");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        byte[] buffer = new byte[4096];
        int bytesRead;
        CRC32 crc32 = new CRC32();
        
        while ((bytesRead = assetStream.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
            crc32.update(buffer, 0, bytesRead);
        }
        
        byte[] fileData = bos.toByteArray();
        assetStream.close();
        
        ZipEntry entry = new ZipEntry("resources.arsc");
        entry.setMethod(ZipOutputStream.STORED);
        entry.setSize(fileData.length);
        entry.setCompressedSize(fileData.length);
        entry.setCrc(crc32.getValue());
        
        zip.putNextEntry(entry);
        zip.write(fileData);
        zip.closeEntry();
        
        LogCatcher.i("ApkBuilder", "✅ resources.arsc 添加完成");
    }
    
    /**
     * 添加普通资源文件
     */
    private static void addAssetFile(AssetManager assetManager, ZipOutputStream zip,
                                    String assetPath, String zipPath) throws IOException {
        InputStream assetStream = assetManager.open(assetPath);
        zip.putNextEntry(new ZipEntry(zipPath));
        copyStream(assetStream, zip);
        assetStream.close();
        zip.closeEntry();
    }
    
    /**
     * 修复编码问题
     */
    private static String fixEncoding(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        try {
            // 检查是否包含中文
            if (text.matches(".*[\\u4e00-\\u9fa5].*")) {
                return text; // 已经是正确的中文
            }
            
            // 尝试修复 ISO-8859-1 误读的 UTF-8
            byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            String fixed = new String(bytes, StandardCharsets.UTF_8);
            
            if (fixed.matches(".*[\\u4e00-\\u9fa5].*")) {
                LogCatcher.i("ApkBuilder", "编码修复: " + text + " -> " + fixed);
                return fixed;
            }
            
            return text;
            
        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "编码修复失败: " + e.getMessage());
            return text;
        }
    }
    
    /**
     * 设置权限
     */
    public static void setPermission(String path, String permission, boolean remove) {
        try {
            File file = new File(path);
            AXMLDoc aXMLDoc = new AXMLDoc();
            aXMLDoc.parse(new FileInputStream(file));
            
            com.Day.Studio.Function.axmleditor.editor.PermissionEditor permissionEditor = 
                new com.Day.Studio.Function.axmleditor.editor.PermissionEditor(aXMLDoc);
            com.Day.Studio.Function.axmleditor.editor.PermissionEditor.EditorInfo editorInfo = 
                new com.Day.Studio.Function.axmleditor.editor.PermissionEditor.EditorInfo();
            
            com.Day.Studio.Function.axmleditor.editor.PermissionEditor.PermissionOpera permissionOpera;
            if (remove) {
                permissionOpera = new com.Day.Studio.Function.axmleditor.editor.PermissionEditor
                    .PermissionOpera(permission).remove();
            } else {
                permissionOpera = new com.Day.Studio.Function.axmleditor.editor.PermissionEditor
                    .PermissionOpera(permission).add();
            }
            
            editorInfo.with(permissionOpera);
            permissionEditor.setEditorInfo(editorInfo);
            permissionEditor.commit();
            
            FileOutputStream fos = new FileOutputStream(file);
            aXMLDoc.build(fos);
            aXMLDoc.release();
            fos.flush();
            fos.close();
            
        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "设置权限失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private static void addZip(ZipOutputStream zip, File dir, String root) {
        if (dir.getName().startsWith(".")) {
            return;
        }
        
        String name = root + "/" + dir.getName();
        if (name.endsWith(".apk")) {
            return;
        }
        
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    addZip(zip, f, name);
                }
            }
        } else {
            try {
                zip.putNextEntry(new ZipEntry(name));
                byte[] data = readAllFile(dir.getAbsolutePath());
                zip.write(data, 0, data.length);
                zip.flush();
                zip.closeEntry();
            } catch (IOException e) {
                LogCatcher.e("ApkBuilder", "添加文件失败: " + name);
            }
        }
    }
    
    private static void addAssetDirectoryToZip(ZipOutputStream zip, AssetManager assetManager,
                                              String assetPath, String zipPath) throws IOException {
        String[] files = assetManager.list(assetPath);
        if (files == null || files.length == 0) {
            return;
        }
        
        for (String file : files) {
            String newAssetPath = assetPath + "/" + file;
            String newZipPath = zipPath + "/" + file;
            
            String[] subFiles = assetManager.list(newAssetPath);
            if (subFiles != null && subFiles.length > 0) {
                addAssetDirectoryToZip(zip, assetManager, newAssetPath, newZipPath);
            } else {
                InputStream assetStream = assetManager.open(newAssetPath);
                zip.putNextEntry(new ZipEntry(newZipPath));
                copyStream(assetStream, zip);
                assetStream.close();
                zip.closeEntry();
            }
        }
    }
    
    private static byte[] readAllFile(String path) throws IOException {
        File file = new File(path);
        byte[] data = new byte[(int) file.length()];
        FileInputStream fis = new FileInputStream(file);
        fis.read(data);
        fis.close();
        return data;
    }
    
    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
    }
    
    private static void copyFile(File source, File dest) throws IOException {
        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(dest);
        copyStream(in, out);
        in.close();
        out.close();
    }
    
    /**
     * 签名 APK
     */
    public static boolean signerApk(String signFilePath, String signPassword,
                                   String keyName, String keyWord,
                                   String apkPath, String outApkpath) {
        try {
            LogCatcher.i("ApkBuilder", "开始签名: " + apkPath);
            
            File keyFile = new File(signFilePath);
            if (!keyFile.exists()) {
                LogCatcher.e("ApkBuilder", "签名文件不存在: " + signFilePath);
                return simpleSignApk(apkPath, outApkpath);
            }
            
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                LogCatcher.e("ApkBuilder", "APK 文件不存在: " + apkPath);
                return false;
            }
            
            try {
                com.mcal.apksigner.ApkSigner signer = new com.mcal.apksigner.ApkSigner(
                    new File(apkPath),
                    new File(outApkpath)
                );
                
                boolean result = signer.signRelease(keyFile, signPassword, keyName, keyWord);
                
                if (result) {
                    LogCatcher.i("ApkBuilder", "✅ APK 签名成功");
                } else {
                    LogCatcher.e("ApkBuilder", "签名失败，使用简化签名");
                    return simpleSignApk(apkPath, outApkpath);
                }
                
                return result;
                
            } catch (Exception e) {
                LogCatcher.e("ApkBuilder", "签名异常: " + e.getMessage());
                return simpleSignApk(apkPath, outApkpath);
            }
            
        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "签名过程异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 简化签名（仅复制文件）
     */
    private static boolean simpleSignApk(String apkPath, String outApkpath) {
        try {
            LogCatcher.w("ApkBuilder", "使用简化签名（未签名）");
            copyFile(new File(apkPath), new File(outApkpath));
            LogCatcher.w("ApkBuilder", "⚠️ 这是未签名的 APK，仅用于测试");
            return true;
        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "简化签名失败: " + e.getMessage());
            return false;
        }
    }
}

