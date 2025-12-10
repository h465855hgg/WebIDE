package com.web.webide.build;

import android.content.Context;
import com.web.webide.core.utils.LogCatcher;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

// 引入你的库 (确保这些类在你的 classpath 中)
import com.Day.Studio.Function.axmleditor.decode.AXMLDoc;
import com.Day.Studio.Function.ApkXmlEditor;
import com.Day.Studio.Function.axmleditor.editor.PermissionEditor;

public class ApkBuilder {

    // 模板 APK 的原始包名
    private static final String OLD_PACKAGE_NAME = "com.web.webapp";

    // 需要替换图标的资源路径 (模板中的路径)
    private static final String ICON_RES_1 = "res/MO.webp"; // 桌面图标
    private static final String ICON_RES_2 = "res/fq.webp"; // 前景/圆图标

    private static class AppConfig {
        String appName = "WebApp";
        String appPackage = "com.example.webapp";
        String versionName = "1.0.0";
        String versionCode = "1";
        String iconPath = null; // 新增：用户图标路径
        List<String> permissions = new ArrayList<>();
    }

    public static String bin(
            Context context,
            String mRootDir,
            String projectPath,
            String aname,
            String pkg,
            String ver,
            String code,
            String amph, // 图标路径 (CodeEditScreen 传入的 absolutePath)
            String[] ps) {

        File bf = new File(projectPath, "build");
        if (!bf.exists()) bf.mkdirs();

        File templateApk = new File(context.getCacheDir(), "webapp_template.apk");
        File rawZipFile = new File(bf, "temp_raw.zip");
        File alignedZipFile = new File(bf, "temp_aligned.apk");
        File finalApkFile = new File(bf, aname + "_release.apk");

        LogCatcher.i("ApkBuilder", "========== 开始构建 WebApp ==========");

        try {
            // 0. 清理旧文件
            if (rawZipFile.exists()) rawZipFile.delete();
            if (alignedZipFile.exists()) alignedZipFile.delete();
            if (finalApkFile.exists()) finalApkFile.delete();

            // 1. 准备配置
            AppConfig config = new AppConfig();
            config.appName = aname;
            config.appPackage = pkg;
            config.versionName = ver;
            config.versionCode = code;

            // 设置图标路径 (如果不为空且文件存在)
            if (amph != null && !amph.isEmpty() && new File(amph).exists()) {
                config.iconPath = amph;
            }

            if (ps != null) {
                for (String p : ps) config.permissions.add(p);
            }

            // 2. 提取模板 APK
            if (!copyAssetFile(context, "webapp_1.0.apk", templateApk)) {
                return "error: 找不到构建模板 (assets/webapp_1.0.apk)";
            }

            // 3. 合并逻辑 (包含图标替换)
            LogCatcher.i("ApkBuilder", ">> 正在合并资源...");
            mergeApk(templateApk, rawZipFile, projectPath, config);

            if (rawZipFile.length() < 1000) {
                return "error: 构建失败，生成的包体过小";
            }

            // 4. ZipAlign
            LogCatcher.i("ApkBuilder", ">> 正在 ZipAlign...");
            try {
                ZipAligner.align(rawZipFile, alignedZipFile);
            } catch (Exception e) {
                return "error: 对齐失败 - " + e.getMessage();
            }

            // 5. 签名
            LogCatcher.i("ApkBuilder", ">> 正在签名...");
            String signaturePath = new File(mRootDir, "WebIDE.jks").getAbsolutePath();
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

            if (signResult && finalApkFile.length() > 0) {
                LogCatcher.i("ApkBuilder", "✅ 构建成功: " + finalApkFile.getAbsolutePath());
                return finalApkFile.getAbsolutePath();
            } else {
                return "error: 签名失败";
            }

        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "❌ 构建崩溃", e);
            return "error: " + e.getMessage();
        }
    }

    private static void mergeApk(File templateFile, File outputFile, String projectPath, AppConfig config) throws Exception {
        ZipFile zipFile = new ZipFile(templateFile);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile));
        zos.setLevel(5);

        try {
            // A. 优先写入 resources.arsc (保持 STORED)
            ZipEntry arscEntry = zipFile.getEntry("resources.arsc");
            if (arscEntry != null) {
                copyAsStored(zipFile, arscEntry, zos);
            }

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equals("resources.arsc")) continue;
                if (name.startsWith("META-INF/")) continue;
                if (name.startsWith("assets/")) continue;

                // --- 1. 处理 Manifest (修改包名、权限) ---
                if (name.equals("AndroidManifest.xml")) {
                    processManifest(zipFile, entry, zos, config);
                    continue;
                }

                // --- 2. 处理图标替换 ---
                if (config.iconPath != null && (name.equals(ICON_RES_1) || name.equals(ICON_RES_2))) {
                    LogCatcher.d("ApkBuilder", "正在替换图标: " + name);
                    ZipEntry iconEntry = new ZipEntry(name);
                    zos.putNextEntry(iconEntry);
                    try (FileInputStream fis = new FileInputStream(new File(config.iconPath))) {
                        copyStream(fis, zos);
                    }
                    zos.closeEntry();
                    continue;
                }

                // --- 3. 普通文件拷贝 ---
                ZipEntry newEntry = new ZipEntry(name);
                zos.putNextEntry(newEntry);
                try (InputStream is = zipFile.getInputStream(entry)) {
                    copyStream(is, zos);
                }
                zos.closeEntry();
            }

            // B. 注入用户 assets
            File userAssetsDir = new File(projectPath, "src/main/assets");
            if (userAssetsDir.exists() && userAssetsDir.isDirectory()) {
                addProjectFilesRecursively(zos, userAssetsDir, "assets");
            }

            // C. 将 webapp.json 配置文件打包到 assets 目录
            File configFile = new File(projectPath, "webapp.json");
            if (configFile.exists()) {
                LogCatcher.i("ApkBuilder", "正在打包配置文件: webapp.json");
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    ZipEntry configEntry = new ZipEntry("assets/webapp.json");
                    zos.putNextEntry(configEntry);
                    copyStream(fis, zos);
                    zos.closeEntry();
                }
            } else {
                LogCatcher.w("ApkBuilder", "未找到 webapp.json 配置文件");
            }

        } finally {
            zipFile.close();
            zos.close();
        }
    }

    /**
     * Manifest 处理逻辑：包名修改、版本修改、权限修改
     */
    /**
     * Manifest 处理逻辑：包名修改、版本修改、权限修改
     */
    private static void processManifest(ZipFile zipFile, ZipEntry entry, ZipOutputStream zos, AppConfig config) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = zipFile.getInputStream(entry)) {
            copyStream(is, bos);
        }
        byte[] originalData = bos.toByteArray();

        File tempManifest = File.createTempFile("TempManifest", ".xml");
        try (FileOutputStream fos = new FileOutputStream(tempManifest)) {
            fos.write(originalData);
        }

        try {
            // 1. 基础属性修改
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

            // 2. 权限修改 (使用 PermissionEditor)
            if (config.permissions != null && !config.permissions.isEmpty()) {
                LogCatcher.i("ApkBuilder", "正在添加权限: " + config.permissions.size() + " 个");
                for (String perm : config.permissions) {
                    setPermission(tempManifest.getAbsolutePath(), perm, false); // false = add
                }
            }

            // 3. 移除 testOnly 标志
            removeTestOnly(tempManifest);

            // 4. AXML 字符串池修正 (解决包名变更导致的 Provider/Class 问题)
            if (!config.appPackage.equals(OLD_PACKAGE_NAME)) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put(OLD_PACKAGE_NAME + ".androidx-startup", config.appPackage + ".androidx-startup");
                replacements.put(OLD_PACKAGE_NAME + ".fileprovider", config.appPackage + ".fileprovider");
                replacements.put(".MainActivity", OLD_PACKAGE_NAME + ".MainActivity");

                ManifestStringReplacer.batchReplaceStringInAXML(tempManifest, replacements);
            }

            // 5. 【新增】处理 Provider 授权冲突
            LogCatcher.i("ApkBuilder", "正在处理 Provider 授权冲突...");
            ProviderAuthReplacer.replaceProviderAuthorities(tempManifest, OLD_PACKAGE_NAME, config.appPackage);

            // 可选：快速检查修复
            ProviderAuthReplacer.fixProviderConflicts(tempManifest, config.appPackage);

            // 写入 Zip
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

    /**
     * 使用 PermissionEditor 修改权限
     * @param path Manifest 文件路径
     * @param permission 权限字符串 (如 android.permission.INTERNET)
     * @param remove true=删除, false=添加
     */
    public static void setPermission(String path, String permission, boolean remove) {
        try {
            File file = new File(path);
            AXMLDoc doc = new AXMLDoc();
            doc.parse(new FileInputStream(file));

            PermissionEditor pe = new PermissionEditor(doc);
            PermissionEditor.EditorInfo info = new PermissionEditor.EditorInfo();
            PermissionEditor.PermissionOpera op = new PermissionEditor.PermissionOpera(permission);

            // 根据你的库实现调用 add 或 remove
            info.with(remove ? op.remove() : op.add());

            pe.setEditorInfo(info);
            pe.commit();

            doc.build(new FileOutputStream(file));
            doc.release();
        } catch (Exception e) {
            LogCatcher.e("ApkBuilder", "权限修改失败: " + permission, e);
        }
    }

    // --- 以下保持原有的辅助方法 ---

    private static void addProjectFilesRecursively(ZipOutputStream zos, File file, String zipPath) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addProjectFilesRecursively(zos, child, zipPath + "/" + child.getName());
                }
            }
        } else {
            try {
                ZipEntry newEntry = new ZipEntry(zipPath);
                zos.putNextEntry(newEntry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    copyStream(fis, zos);
                }
                zos.closeEntry();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        ZipEntry newEntry = new ZipEntry("resources.arsc");
        newEntry.setMethod(ZipEntry.STORED);
        newEntry.setSize(data.length);
        newEntry.setCompressedSize(data.length);
        newEntry.setCrc(crc.getValue());
        newEntry.setExtra(null);
        zos.putNextEntry(newEntry);
        zos.write(data);
        zos.closeEntry();
    }

    private static void removeTestOnly(File manifestFile) {
        try {
            FileInputStream fis = new FileInputStream(manifestFile);
            byte[] data = new byte[(int) manifestFile.length()];
            fis.read(data);
            fis.close();
            byte[] target = new byte[]{(byte) 0x72, (byte) 0x02, (byte) 0x01, (byte) 0x01};
            boolean found = false;
            for (int i = 0; i < data.length - 3; i++) {
                if (data[i] == target[0] && data[i + 1] == target[1] && data[i + 2] == target[2] && data[i + 3] == target[3]) {
                    data[i] = 0; data[i + 1] = 0; data[i + 2] = 0; data[i + 3] = 0;
                    found = true;
                }
            }
            if (found) {
                FileOutputStream fos = new FileOutputStream(manifestFile);
                fos.write(data);
                fos.close();
            }
        } catch (Exception e) {}
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
    }

    private static boolean copyAssetFile(Context ctx, String name, File dest) {
        try (InputStream in = ctx.getAssets().open(name);
             FileOutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
            return true;
        } catch (IOException e) { return false; }
    }

    public static boolean signerApk(String keyPath, String pass, String alias, String keyPass, String inPath, String outPath) {
        try {
            com.mcal.apksigner.ApkSigner signer = new com.mcal.apksigner.ApkSigner(new File(inPath), new File(outPath));
            signer.setV1SigningEnabled(true);
            signer.setV2SigningEnabled(true);
            signer.signRelease(new File(keyPath), pass, alias, keyPass);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    // ==================================================================================
    //   AXML 字符串池批量替换器 (保持原样)
    // ==================================================================================

    private static class ManifestStringReplacer {

        private static final int CHUNK_STRING_POOL = 0x001C0001;

        public static void batchReplaceStringInAXML(File axmlFile, Map<String, String> replacementMap) throws Exception {
            byte[] data = new byte[(int) axmlFile.length()];
            try (FileInputStream fis = new FileInputStream(axmlFile)) {
                fis.read(data);
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(8);

            int chunkType = buffer.getInt();
            if (chunkType != CHUNK_STRING_POOL) return;

            int chunkSize = buffer.getInt();
            int stringCount = buffer.getInt();
            int styleCount = buffer.getInt();
            int flags = buffer.getInt();
            int stringsOffset = buffer.getInt();
            int stylesOffset = buffer.getInt();

            boolean isUTF8 = (flags & 0x0100) != 0;
            int stringPoolStart = buffer.position() - 28;

            int[] offsets = new int[stringCount];
            for (int i = 0; i < stringCount; i++) {
                offsets[i] = buffer.getInt();
            }

            List<String> strings = new ArrayList<>();
            int dataStart = stringPoolStart + stringsOffset;

            for (int i = 0; i < stringCount; i++) {
                int strPos = dataStart + offsets[i];
                buffer.position(strPos);

                if (isUTF8) {
                    int len1 = buffer.get() & 0xFF;
                    int len = len1;
                    if ((len1 & 0x80) != 0) len = ((len1 & 0x7F) << 8) | (buffer.get() & 0xFF);

                    int len2 = buffer.get() & 0xFF;
                    int encodedLen = len2;
                    if ((len2 & 0x80) != 0) encodedLen = ((len2 & 0x7F) << 8) | (buffer.get() & 0xFF);

                    byte[] strBytes = new byte[encodedLen];
                    buffer.get(strBytes);
                    strings.add(new String(strBytes, StandardCharsets.UTF_8));
                } else {
                    int len = buffer.getShort() & 0xFFFF;
                    byte[] strBytes = new byte[len * 2];
                    buffer.get(strBytes);
                    strings.add(new String(strBytes, StandardCharsets.UTF_16LE));
                }
            }

            boolean modified = false;
            for (int i = 0; i < strings.size(); i++) {
                String currentStr = strings.get(i);
                for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
                    String target = entry.getKey();
                    String replacement = entry.getValue();
                    if (currentStr.equals(target)) {
                        strings.set(i, replacement);
                        modified = true;
                        break;
                    }
                }
            }

            if (!modified) return;

            ByteArrayOutputStream poolBos = new ByteArrayOutputStream();
            List<Integer> newOffsets = new ArrayList<>();
            int currentOffset = 0;

            for (String s : strings) {
                newOffsets.add(currentOffset);
                if (isUTF8) {
                    byte[] rawBytes = s.getBytes(StandardCharsets.UTF_8);
                    poolBos.write(s.length());
                    poolBos.write(rawBytes.length);
                    poolBos.write(rawBytes);
                    poolBos.write(0);
                    currentOffset += (2 + rawBytes.length + 1);
                } else {
                    byte[] rawBytes = s.getBytes(StandardCharsets.UTF_16LE);
                    int charLen = s.length();
                    poolBos.write(charLen & 0xFF);
                    poolBos.write((charLen >> 8) & 0xFF);
                    poolBos.write(rawBytes);
                    poolBos.write(0); poolBos.write(0);
                    currentOffset += (2 + rawBytes.length + 2);
                }
            }

            while (currentOffset % 4 != 0) {
                poolBos.write(0);
                currentOffset++;
            }

            byte[] newStringData = poolBos.toByteArray();

            ByteArrayOutputStream fileBos = new ByteArrayOutputStream();
            fileBos.write(data, 0, 8); // Header

            int newChunkSize = 28 + (stringCount * 4) + (styleCount * 4) + newStringData.length;
            ByteBuffer headerBuf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
            headerBuf.putInt(CHUNK_STRING_POOL);
            headerBuf.putInt(newChunkSize);
            headerBuf.putInt(stringCount);
            headerBuf.putInt(styleCount);
            headerBuf.putInt(flags);
            headerBuf.putInt(28 + (stringCount * 4) + (styleCount * 4));
            headerBuf.putInt(0);

            fileBos.write(headerBuf.array());

            ByteBuffer offsetBuf = ByteBuffer.allocate(stringCount * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int off : newOffsets) offsetBuf.putInt(off);
            fileBos.write(offsetBuf.array());

            fileBos.write(newStringData);

            int oldChunkEnd = stringPoolStart + chunkSize;
            fileBos.write(data, oldChunkEnd, data.length - oldChunkEnd);

            byte[] finalData = fileBos.toByteArray();
            ByteBuffer finalBuf = ByteBuffer.wrap(finalData).order(ByteOrder.LITTLE_ENDIAN);
            finalBuf.putInt(4, finalData.length);

            try (FileOutputStream fos = new FileOutputStream(axmlFile)) {
                fos.write(finalData);
            }
        }
    }
}