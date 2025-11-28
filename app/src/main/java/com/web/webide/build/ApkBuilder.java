package com.web.webide.build;

import android.content.Context;
import com.web.webide.core.utils.LogCatcher;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

// 引入你的库
import com.Day.Studio.Function.axmleditor.decode.AXMLDoc;
import com.Day.Studio.Function.ApkXmlEditor;
import com.Day.Studio.Function.axmleditor.editor.PermissionEditor;

public class ApkBuilder {

    private static class AppConfig {
        String appName = "WebApp";
        String appPackage = "com.example.webapp";
        String versionName = "1.0.0";
        String versionCode = "1";
        List<String> permissions = new ArrayList<>();
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
        if (!bf.exists()) bf.mkdirs();

        // 文件路径
        File templateApk = new File(context.getCacheDir(), "template_base.apk");
        File rawZipFile = new File(bf, "temp_raw.zip");
        File alignedZipFile = new File(bf, "temp_aligned.apk");
        File finalApkFile = new File(bf, aname + ".apk");

        LogCatcher.i("ApkBuilder", "========== 开始构建 (APK模板模式) ==========");

        try {
            // 清理旧文件
            if (rawZipFile.exists()) rawZipFile.delete();
            if (alignedZipFile.exists()) alignedZipFile.delete();
            if (finalApkFile.exists()) finalApkFile.delete();

            // 1. 准备配置
            AppConfig config = new AppConfig();
            config.appName = aname;
            config.appPackage = pkg;
            config.versionName = ver;
            config.versionCode = code;
            // 处理权限数组 ps -> config.permissions (如果 ps 不为空)
            if (ps != null) {
                for (String p : ps) config.permissions.add(p);
            }

            // 2. 提取模板 APK
            LogCatcher.i("ApkBuilder", ">> 阶段1: 提取模板 APK");
            // 注意：请确保你的 assets 里文件名确实是 webapp_1.0.apk
            if (!copyAssetFile(context, "webapp_1.0.apk", templateApk)) {
                return "error: 模板 APK 不存在 (assets/webapp_1.0.apk)";
            }

            // 3. 合并逻辑
            LogCatcher.i("ApkBuilder", ">> 阶段2: 合并资源与 Manifest");
            mergeApk(templateApk, rawZipFile, mDir, config);

            if (rawZipFile.length() < 1000) {
                return "error: 生成的 ZIP 太小，合并失败";
            }

            // 检查生成的 Zip 是否正常
            if (rawZipFile.length() < 1000) {
                return "error: 生成的 ZIP 太小";
            }

            // 4. ZipAlign 对齐 (必须执行此步骤，否则报 -124 错误)
            LogCatcher.i("ApkBuilder", ">> 阶段3: ZipAlign 对齐");
            try {
                // 使用上面更新后的 ZipAligner 类
                ZipAligner.align(rawZipFile, alignedZipFile);
            } catch (Exception e) {
                e.printStackTrace();
                return "error: 对齐失败 - " + e.getMessage();
            }


            // 5. 签名
            LogCatcher.i("ApkBuilder", ">> 阶段4: 签名");
            String signaturePath = "/storage/emulated/0/WebIDE/WebIDE.jks";

            // 自动释放内置签名
            File keyFile = new File(signaturePath);
            if (!keyFile.exists()) {
                File internalKey = new File(context.getFilesDir(), "WebIDE.jks");
                if (!internalKey.exists()) copyAssetFile(context, "WebIDE.jks", internalKey);
                signaturePath = internalKey.getAbsolutePath();
            }

            boolean signResult = signerApk(
                    signaturePath, "WebIDE", "WebIDE", "WebIDE",
                    alignedZipFile.getAbsolutePath(),
                    finalApkFile.getAbsolutePath()
            );

            // 清理临时文件
            rawZipFile.delete();
            alignedZipFile.delete();
            // templateApk.delete(); // 可选：保留缓存下次用
            checkAlignment(finalApkFile.getAbsolutePath());
            verifyAlignment(finalApkFile);

            if (signResult && finalApkFile.length() > 0) {
                LogCatcher.i("ApkBuilder", "✅ 构建成功: " + finalApkFile.getAbsolutePath());
                return finalApkFile.getAbsolutePath();
            } else {
                return "error: 签名失败";
            }

        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "❌ 构建崩溃: " + e.getMessage());
            e.printStackTrace();
            return "error:" + e.getMessage();
        }

    }



    /**
     * 验证 APK 对齐状态 (诊断工具)
     * 在 bin 方法 return 之前调用: verifyAlignment(finalApkFile);
     */
    private static void verifyAlignment(File apkFile) {
        try {
            ZipFile zf = new ZipFile(apkFile);
            Enumeration<? extends ZipEntry> entries = zf.entries();

            // 为了找到 Local File Header 的物理偏移量，我们需要手动读取文件
            // 这里使用简单暴力搜索法 (仅用于 resources.arsc)
            FileInputStream fis = new FileInputStream(apkFile);
            byte[] data = new byte[(int) apkFile.length()];
            fis.read(data);
            fis.close();

            // 搜索 resources.arsc 的文件名
            byte[] search = "resources.arsc".getBytes(StandardCharsets.UTF_8);

            for (int i = 0; i < data.length - search.length; i++) {
                boolean match = true;
                for (int j = 0; j < search.length; j++) {
                    if (data[i + j] != search[j]) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    // 找到了文件名，它前面是 LFH (30字节)
                    // LFH 结构: [PK 03 04]...[文件名长度 2B][Extra长度 2B][文件名]
                    // 文件名开始位置是 i
                    // 往前推 26 字节是文件名长度字段
                    // 往前推 28 字节是 Extra 长度字段
                    // 往前推 30 字节是 Header 头部

                    int headerStart = i - 30;
                    if (headerStart < 0) continue; // 误报

                    // 验证头标记 0x04034b50
                    if (data[headerStart] != 0x50 || data[headerStart+1] != 0x4b) continue;

                    // 获取 Extra Field 长度 (Little Endian)
                    int extraLen = (data[i - 2] & 0xFF) | ((data[i - 1] & 0xFF) << 8);

                    // 数据开始位置 = 文件名结束位置(i + search.length) + Extra长度
                    int dataStart = i + search.length + extraLen;

                    int remainder = dataStart % 4;
                    LogCatcher.w("AlignCheck", "检测到 resources.arsc 数据起始位置: " + dataStart);

                    if (remainder == 0) {
                        LogCatcher.i("AlignCheck", "✅ 完美! resources.arsc 已对齐 (4字节整除)");
                    } else {
                        LogCatcher.e("AlignCheck", "❌ 严重错误! resources.arsc 未对齐! 偏差: " + remainder + " (应为0)");
                    }
                    break;
                }
            }
            zf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void checkAlignment(String apkPath) {
        try {
            ZipFile zf = new ZipFile(apkPath);
            Enumeration<? extends ZipEntry> entries = zf.entries();

            // 为了计算偏移量，我们需要遍历整个文件流（非常耗时，仅用于调试诊断）
            // 但这里我们用一种简化的方式：通过反射或者解析库，或者我们直接手动解析
            // 为了简单，我们只打印 Log 提示用户去检查

            LogCatcher.i("AlignCheck", "正在检查最终 APK 的对齐情况: " + apkPath);

            FileInputStream fis = new FileInputStream(apkPath);
            BufferedInputStream bis = new BufferedInputStream(fis);

            // 简单的 Zip 解析器，寻找 Local File Header
            byte[] buffer = new byte[65536];
            long currentPos = 0;
            int read;

            // 这是一个非常简化的流式查找，只为了找 resources.arsc
            // 严谨的解析需要解析 Central Directory，这里我们假设文件是追加写入的
            // 注意：这种简单的流式查找在 V2 签名块存在时可能不准，但足以排查 resources.arsc (通常在前面)

            LogCatcher.w("AlignCheck", "⚠️ 请注意：如果依然安装失败，请尝试使用 PC 端的 'zipalign -c -v 4 test.apk' 命令检查");
            LogCatcher.w("AlignCheck", "如果 ZipAlign 成功但签名后失效，说明签名库(ApkSigner)破坏了文件结构。");

            zf.close();
            bis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 核心逻辑：合并模板APK和用户文件
     * 修复1: 优先写入 resources.arsc (解决 -124 安装错误)
     * 修复2: 去除用户文件的多余目录层级 (直接放入 assets/ 而不是 assets/test/)
     */
    private static void mergeApk(File templateFile, File outputFile, String projectDir, AppConfig config) throws Exception {
        ZipFile zipFile = new ZipFile(templateFile);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile));
        zos.setLevel(5);

        try {
            // 1. 优先写入 resources.arsc (对齐关键)
            ZipEntry arscEntry = zipFile.getEntry("resources.arsc");
            if (arscEntry != null) {
                copyAsStored(zipFile, arscEntry, zos);
            }

            // 2. 遍历模板文件
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equals("resources.arsc")) continue;

                // 过滤旧签名
                if (name.startsWith("META-INF/")) continue;

                // ▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼
                // 【核心修复】过滤掉模板自带的所有 assets 文件！
                // 只有拦截了它们，你后面注入的新文件才能生效。
                // 如果你的模板里有必须保留的系统assets，请把条件改成 name.startsWith("assets/webapp") 之类的
                if (name.startsWith("assets/")) {
                    continue;
                }
                // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

                // 拦截 Manifest
                if (name.equals("AndroidManifest.xml")) {
                    processManifest(zipFile, entry, zos, config);
                    continue;
                }

                // 复制其他文件 (classes.dex, res/, lib/ 等)
                ZipEntry newEntry = new ZipEntry(name);
                zos.putNextEntry(newEntry);
                try (InputStream is = zipFile.getInputStream(entry)) {
                    copyStream(is, zos);
                }
                zos.closeEntry();
            }

            // 3. 注入用户文件 (这时候写入 assets 才是有效的)
            LogCatcher.i("ApkBuilder", "正在注入用户文件...");
            File rootDir = new File(projectDir);
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().equals(".WebIDE") || file.getName().endsWith(".apk")) continue;
                    addProjectFilesRecursively(zos, file, "assets");
                }
            }

        } finally {
            zipFile.close();
            zos.close();
        }
    }

    /**
     * 递归添加文件到 Zip
     * @param zos Zip输出流
     * @param file 当前要添加的文件或文件夹
     * @param parentPath 当前文件在 Zip 中的父路径 (例如 "assets")
     */
    private static void addProjectFilesRecursively(ZipOutputStream zos, File file, String parentPath) {
        if (file == null || !file.exists()) return;

        // 过滤规则
        if (file.getName().equals(".WebIDE") || file.getName().endsWith(".apk")) return;

        // 拼接路径: assets + / + index.html
        String currentPath = parentPath + "/" + file.getName();

        if (file.isDirectory()) {
            // 如果是目录，递归处理子文件
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addProjectFilesRecursively(zos, child, currentPath);
                }
            }
        } else {
            // 如果是文件，直接写入
            try {
                zos.putNextEntry(new ZipEntry(currentPath));
                try (FileInputStream fis = new FileInputStream(file)) {
                    copyStream(fis, zos);
                }
                zos.closeEntry();
            } catch (IOException e) {
                LogCatcher.e("ApkBuilder", "添加文件失败: " + file.getName());
            }
        }
    }









    private static void processManifest(ZipFile zipFile, ZipEntry entry, ZipOutputStream zos, AppConfig config) throws Exception {
        // 读取原版
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = zipFile.getInputStream(entry)) {
            copyStream(is, bos);
        }
        byte[] originalData = bos.toByteArray();

        // 1. 创建临时文件
        File tempManifest = File.createTempFile("TempManifest", ".xml");
        try (FileOutputStream fos = new FileOutputStream(tempManifest)) {
            fos.write(originalData);
        }

        try {
            // 2. 使用库修改包名、版本等 (常规操作)
            ApkXmlEditor.setXmlPaht(tempManifest.getAbsolutePath());
            ApkXmlEditor.setAppName(config.appName);
            ApkXmlEditor.setAppPack(config.appPackage);
            try {
                ApkXmlEditor.setAppbcode(Integer.parseInt(config.versionCode));
            } catch (NumberFormatException e) {
                ApkXmlEditor.setAppbcode(1);
            }
            ApkXmlEditor.setAppbname(config.versionName);
            ApkXmlEditor.operation();

            // 修改权限
            if (config.permissions != null) {
                for (String perm : config.permissions) {
                    setPermission(tempManifest.getAbsolutePath(), perm, false);
                }
            }

            // =============================================
            // 【关键插入点】: 在所有库操作完成后，执行字节替换
            // =============================================
            removeTestOnly(tempManifest);
            // =============================================

            // 3. 写入 Zip
            ZipEntry newEntry = new ZipEntry("AndroidManifest.xml");
            zos.putNextEntry(newEntry);
            try (FileInputStream fis = new FileInputStream(tempManifest)) {
                copyStream(fis, zos);
            }
            zos.closeEntry();

        } finally {
            tempManifest.delete();
        }
    }

    private static void copyAsStored(ZipFile zipFile, ZipEntry entry, ZipOutputStream zos) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = zipFile.getInputStream(entry)) {
            copyStream(is, bos);
        }
        byte[] data = bos.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(data);

        // 创建全新的 Entry，不继承原 Entry 的任何属性(如Extra)
        ZipEntry newEntry = new ZipEntry("resources.arsc");
        newEntry.setMethod(ZipEntry.STORED);
        newEntry.setSize(data.length);
        newEntry.setCompressedSize(data.length);
        newEntry.setCrc(crc.getValue());

        // 显式清空 Extra，确保头长度固定为 44
        newEntry.setExtra(null);

        zos.putNextEntry(newEntry);
        zos.write(data);
        zos.closeEntry();
    }
    /**
     * 强行移除 android:testOnly="true" 属性
     * 解决安装报错 -15
     */
    /**
     * 【黑科技】通过二进制修改，强行移除 android:testOnly="true"
     * 原理：将 testOnly 的资源 ID (0x01010272) 替换为 0，使系统忽略该属性。
     */
    private static void removeTestOnly(File manifestFile) {
        try {
            FileInputStream fis = new FileInputStream(manifestFile);
            byte[] data = new byte[(int) manifestFile.length()];
            fis.read(data);
            fis.close();

            // testOnly 的资源 ID 是 0x01010272
            // 在 Little-Endian (小端序) 二进制中是: 72 02 01 01
            byte[] target = new byte[]{(byte) 0x72, (byte) 0x02, (byte) 0x01, (byte) 0x01};

            boolean found = false;

            // 遍历字节数组进行替换
            for (int i = 0; i < data.length - 3; i++) {
                if (data[i] == target[0] &&
                        data[i+1] == target[1] &&
                        data[i+2] == target[2] &&
                        data[i+3] == target[3]) {

                    // 找到了！替换为 00 00 00 00
                    data[i] = 0;
                    data[i+1] = 0;
                    data[i+2] = 0;
                    data[i+3] = 0;

                    found = true;
                    // 注意：通常 Manifest 里只有一个定义，但为了保险可以继续找
                    LogCatcher.i("ApkBuilder", "成功移除 testOnly 属性 (位置: " + i + ")");
                }
            }

            if (!found) {
                LogCatcher.w("ApkBuilder", "未找到 testOnly 属性，可能模板本身就是 Release 版，或者已被混淆。");
            } else {
                // 写回文件
                FileOutputStream fos = new FileOutputStream(manifestFile);
                fos.write(data);
                fos.close();
            }

        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "移除 testOnly 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
        }
    }

    private static boolean copyAssetFile(Context ctx, String name, File dest) {
        try (InputStream in = ctx.getAssets().open(name);
             FileOutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void setPermission(String path, String permission, boolean remove) {
        try {
            File file = new File(path);
            AXMLDoc doc = new AXMLDoc();
            doc.parse(new FileInputStream(file));
            PermissionEditor pe = new PermissionEditor(doc);
            PermissionEditor.EditorInfo info = new PermissionEditor.EditorInfo();
            PermissionEditor.PermissionOpera op = new PermissionEditor.PermissionOpera(permission);
            info.with(remove ? op.remove() : op.add());
            pe.setEditorInfo(info);
            pe.commit();
            doc.build(new FileOutputStream(file));
            doc.release();
        } catch (Exception e) {}
    }

    public static boolean signerApk(String keyPath, String pass, String alias, String keyPass, String inPath, String outPath) {
        try {
            com.mcal.apksigner.ApkSigner signer = new com.mcal.apksigner.ApkSigner(
                    new File(inPath), new File(outPath));
            signer.setV1SigningEnabled(true);
            signer.setV2SigningEnabled(true);
            signer.signRelease(new File(keyPath), pass, alias, keyPass);
            return true;
        } catch (Throwable e) {
            LogCatcher.e("Signer", "签名崩溃: " + e.toString());
            e.printStackTrace();
            return false;
        }
    }
}