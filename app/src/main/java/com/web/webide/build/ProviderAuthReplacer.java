

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
package com.web.webide.build;

import com.web.webide.core.utils.LogCatcher;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProviderAuthReplacer {

    private static final int CHUNK_STRING_POOL = 0x001C0001;

    /**
     * 替换 Manifest 中的 Provider 授权
     * @param manifestFile Manifest 文件
     * @param oldPackageName 旧包名
     * @param newPackageName 新包名
     * @throws Exception 处理异常
     */
    public static void replaceProviderAuthorities(File manifestFile, String oldPackageName, String newPackageName) throws Exception {
        if (oldPackageName == null || newPackageName == null ||
                oldPackageName.equals(newPackageName)) {
            LogCatcher.d("ProviderAuthReplacer", "包名相同，无需替换");
            return;
        }

        // 构建需要替换的授权映射
        Map<String, String> authMapping = new HashMap<>();

        // 1. 基础 Provider 授权替换
        String[] baseProviders = {
                ".provider",
                ".fileprovider",
                ".androidx-startup",
                ".appsflyer-provider",
                ".firebase-provider",
                ".download-provider",
                ".cache-provider",
                ".security-provider"
        };

        for (String suffix : baseProviders) {
            String oldAuth = oldPackageName + suffix;
            String newAuth = newPackageName + suffix;
            authMapping.put(oldAuth, newAuth);
        }

        // 2. 常见的第三方库 Provider 模式
        String[] commonProviders = {
                "com.web.webapp.provider",
                "com.web.webapp.fileprovider",
                "com.web.webapp.androidx-startup",
                oldPackageName + ".provider.Provider",
                oldPackageName + ".provider.FileProvider",
                oldPackageName + ".provider.DownloadProvider"
        };

        for (String oldAuth : commonProviders) {
            // 计算新的授权名称
            String newAuth;
            if (oldAuth.startsWith("com.web.webapp.")) {
                // 处理硬编码的 com.web.webapp
                newAuth = oldAuth.replace("com.web.webapp.", newPackageName + ".");
            } else if (oldAuth.startsWith(oldPackageName + ".")) {
                // 处理包名开头的
                newAuth = oldAuth.replace(oldPackageName + ".", newPackageName + ".");
            } else {
                // 其他情况，直接追加新包名
                newAuth = newPackageName + oldAuth.substring(oldAuth.lastIndexOf('.'));
            }
            authMapping.put(oldAuth, newAuth);
        }

        // 执行批量替换
        if (!authMapping.isEmpty()) {
            LogCatcher.i("ProviderAuthReplacer", "开始替换 Provider 授权，共 " + authMapping.size() + " 个映射");
            batchReplaceStringInAXML(manifestFile, authMapping);
        }
    }

    /**
     * 扫描并获取 Manifest 中的所有 Provider 授权
     * @param manifestFile Manifest 文件
     * @return 授权列表
     */
    public static List<String> scanProviderAuthorities(File manifestFile) throws Exception {
        List<String> authorities = new ArrayList<>();
        byte[] data = readFileToBytes(manifestFile);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // 跳过文件头
        if (buffer.getInt() != 0x00080003) { // AXML 文件头
            throw new IllegalArgumentException("无效的 AXML 文件");
        }
        buffer.position(8);

        int chunkType = buffer.getInt();
        if (chunkType != CHUNK_STRING_POOL) {
            return authorities;
        }

        // 解析字符串池
        int chunkSize = buffer.getInt();
        int stringCount = buffer.getInt();
        int styleCount = buffer.getInt();
        int flags = buffer.getInt();
        int stringsOffset = buffer.getInt();
        int stylesOffset = buffer.getInt();

        boolean isUTF8 = (flags & 0x0100) != 0;
        int stringPoolStart = buffer.position() - 28;

        // 读取字符串偏移
        int[] offsets = new int[stringCount];
        for (int i = 0; i < stringCount; i++) {
            offsets[i] = buffer.getInt();
        }

        // 读取所有字符串
        List<String> strings = new ArrayList<>();
        int dataStart = stringPoolStart + stringsOffset;

        for (int i = 0; i < stringCount; i++) {
            int strPos = dataStart + offsets[i];
            buffer.position(strPos);

            String str = readString(buffer, isUTF8);
            strings.add(str);

            // 检查是否为 Provider 授权（常见的授权模式）
            if (str.contains(".provider") || str.contains(".fileprovider") ||
                    str.contains("content://") || str.contains(".startup")) {
                authorities.add(str);
            }
        }

        return authorities;
    }

    /**
     * 批量替换 AXML 中的字符串
     * @param axmlFile AXML 文件
     * @param replacementMap 替换映射
     */
    public static void batchReplaceStringInAXML(File axmlFile, Map<String, String> replacementMap) throws Exception {
        if (replacementMap.isEmpty()) {
            return;
        }

        byte[] data = readFileToBytes(axmlFile);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // 检查文件头
        if (buffer.getInt() != 0x00080003) {
            throw new IllegalArgumentException("无效的 AXML 文件");
        }
        buffer.position(8);

        int chunkType = buffer.getInt();
        if (chunkType != CHUNK_STRING_POOL) {
            LogCatcher.w("ProviderAuthReplacer", "未找到字符串池");
            return;
        }

        int chunkSize = buffer.getInt();
        int stringCount = buffer.getInt();
        int styleCount = buffer.getInt();
        int flags = buffer.getInt();
        int stringsOffset = buffer.getInt();
        int stylesOffset = buffer.getInt();

        boolean isUTF8 = (flags & 0x0100) != 0;
        int stringPoolStart = buffer.position() - 28;

        // 读取偏移
        int[] offsets = new int[stringCount];
        for (int i = 0; i < stringCount; i++) {
            offsets[i] = buffer.getInt();
        }

        // 读取所有字符串
        List<String> strings = new ArrayList<>();
        int dataStart = stringPoolStart + stringsOffset;

        for (int i = 0; i < stringCount; i++) {
            int strPos = dataStart + offsets[i];
            buffer.position(strPos);
            strings.add(readString(buffer, isUTF8));
        }

        // 替换字符串
        boolean modified = false;
        for (int i = 0; i < strings.size(); i++) {
            String current = strings.get(i);
            for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
                if (current.equals(entry.getKey())) {
                    LogCatcher.d("ProviderAuthReplacer", "替换: " + entry.getKey() + " -> " + entry.getValue());
                    strings.set(i, entry.getValue());
                    modified = true;
                    break;
                }
            }
        }

        if (!modified) {
            LogCatcher.w("ProviderAuthReplacer", "未找到匹配的字符串进行替换");
            return;
        }

        // 重新构建字符串池
        byte[] newStringData = buildStringPool(strings, isUTF8);

        // 构建新文件
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // 1. 写入文件头
        output.write(data, 0, 8);

        // 2. 写入新的字符串池头部
        ByteBuffer header = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(CHUNK_STRING_POOL);
        header.putInt(28 + (stringCount * 4) + newStringData.length); // 新的大小
        header.putInt(stringCount);
        header.putInt(styleCount);
        header.putInt(flags);
        header.putInt(28 + (stringCount * 4)); // 字符串偏移
        header.putInt(0); // 样式偏移
        output.write(header.array());

        // 3. 写入新的字符串偏移
        ByteBuffer offsetsBuffer = ByteBuffer.allocate(stringCount * 4).order(ByteOrder.LITTLE_ENDIAN);
        int currentOffset = 0;
        ByteArrayOutputStream tempStrings = new ByteArrayOutputStream();

        for (String str : strings) {
            offsetsBuffer.putInt(currentOffset);
            byte[] strBytes = isUTF8 ? str.getBytes("UTF-8") : str.getBytes("UTF-16LE");

            if (isUTF8) {
                // UTF-8 编码
                tempStrings.write(str.length() & 0xFF);
                tempStrings.write(strBytes.length & 0xFF);
                tempStrings.write(strBytes);
                tempStrings.write(0); // null terminator
                currentOffset += 2 + strBytes.length + 1;
            } else {
                // UTF-16 编码
                tempStrings.write(str.length() & 0xFF);
                tempStrings.write((str.length() >> 8) & 0xFF);
                tempStrings.write(strBytes);
                tempStrings.write(0);
                tempStrings.write(0); // null terminator
                currentOffset += 2 + strBytes.length + 2;
            }
        }

        // 填充到4字节对齐
        while (currentOffset % 4 != 0) {
            tempStrings.write(0);
            currentOffset++;
        }

        output.write(offsetsBuffer.array());
        output.write(tempStrings.toByteArray());

        // 4. 写入剩余的数据
        int remainingPos = stringPoolStart + chunkSize;
        output.write(data, remainingPos, data.length - remainingPos);

        // 5. 更新文件大小
        byte[] finalData = output.toByteArray();
        ByteBuffer finalBuffer = ByteBuffer.wrap(finalData).order(ByteOrder.LITTLE_ENDIAN);
        finalBuffer.putInt(4, finalData.length);

        // 写回文件
        try (FileOutputStream fos = new FileOutputStream(axmlFile)) {
            fos.write(finalData);
        }

        LogCatcher.i("ProviderAuthReplacer", "Provider 授权替换完成");
    }

    private static String readString(ByteBuffer buffer, boolean isUTF8) {
        try {
            if (isUTF8) {
                int len1 = buffer.get() & 0xFF;
                int len = len1;
                if ((len1 & 0x80) != 0) {
                    len = ((len1 & 0x7F) << 8) | (buffer.get() & 0xFF);
                }

                int len2 = buffer.get() & 0xFF;
                int encodedLen = len2;
                if ((len2 & 0x80) != 0) {
                    encodedLen = ((len2 & 0x7F) << 8) | (buffer.get() & 0xFF);
                }

                byte[] strBytes = new byte[encodedLen];
                buffer.get(strBytes);
                return new String(strBytes, "UTF-8");
            } else {
                int len = buffer.getShort() & 0xFFFF;
                byte[] strBytes = new byte[len * 2];
                buffer.get(strBytes);
                return new String(strBytes, "UTF-16LE");
            }
        } catch (Exception e) {
            LogCatcher.e("ProviderAuthReplacer", "读取字符串失败", e);
            return "";
        }
    }

    private static byte[] buildStringPool(List<String> strings, boolean isUTF8) throws Exception {
        ByteArrayOutputStream poolData = new ByteArrayOutputStream();

        for (String str : strings) {
            byte[] strBytes = isUTF8 ? str.getBytes("UTF-8") : str.getBytes("UTF-16LE");

            if (isUTF8) {
                // UTF-8: 写入长度和字符串
                poolData.write(str.length() & 0xFF);
                poolData.write(strBytes.length & 0xFF);
                poolData.write(strBytes);
                poolData.write(0); // null terminator
            } else {
                // UTF-16: 写入字符长度（short）和字符串
                poolData.write(str.length() & 0xFF);
                poolData.write((str.length() >> 8) & 0xFF);
                poolData.write(strBytes);
                poolData.write(0);
                poolData.write(0); // null terminator
            }
        }

        // 4字节对齐
        while (poolData.size() % 4 != 0) {
            poolData.write(0);
        }

        return poolData.toByteArray();
    }

    private static byte[] readFileToBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }

    /**
     * 快速检查并修复 Provider 冲突问题
     * @param manifestFile Manifest 文件
     * @param newPackageName 新包名
     */
    public static void fixProviderConflicts(File manifestFile, String newPackageName) {
        try {
            LogCatcher.i("ProviderAuthReplacer", "开始检查 Provider 冲突...");

            // 扫描现有的 Provider 授权
            List<String> authorities = scanProviderAuthorities(manifestFile);
            LogCatcher.i("ProviderAuthReplacer", "发现 " + authorities.size() + " 个 Provider 授权");

            // 替换所有基于 com.web.webapp 的授权
            Map<String, String> replacements = new HashMap<>();
            for (String auth : authorities) {
                if (auth.contains("com.web.webapp")) {
                    String newAuth = auth.replace("com.web.webapp", newPackageName);
                    replacements.put(auth, newAuth);
                    LogCatcher.d("ProviderAuthReplacer", "需要替换: " + auth + " -> " + newAuth);
                }
            }

            if (!replacements.isEmpty()) {
                batchReplaceStringInAXML(manifestFile, replacements);
                LogCatcher.i("ProviderAuthReplacer", "Provider 冲突修复完成");
            } else {
                LogCatcher.i("ProviderAuthReplacer", "未发现需要替换的 Provider 授权");
            }

        } catch (Exception e) {
            LogCatcher.e("ProviderAuthReplacer", "修复 Provider 冲突失败", e);
        }
    }
}