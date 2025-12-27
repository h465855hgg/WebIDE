

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
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.io.File;
import java.io.ByteArrayOutputStream;


public class ZipAligner {

    private static final int ALIGNMENT = 4;

    public static void align(File inputFile, File outputFile) throws IOException {
        ZipFile zipFile = null;
        ZipOutputStream zos = null;
        ByteCountingOutputStream counter = null;

        try {
            zipFile = new ZipFile(inputFile);
            // 1. 直接包装 FileOutputStream，移除 BufferedOutputStream 防止计数偏差
            counter = new ByteCountingOutputStream(new FileOutputStream(outputFile));
            zos = new ZipOutputStream(counter);
            zos.setLevel(9);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // 读取数据
                byte[] data = readEntryData(zipFile, entry);

                // 2. 创建全新的 Entry，不拷贝旧属性，防止带入未知 Extra 字段
                ZipEntry newEntry = new ZipEntry(name);

                boolean isArsc = name.equals("resources.arsc");

                if (isArsc || entry.getMethod() == ZipEntry.STORED) {
                    newEntry.setMethod(ZipEntry.STORED);
                    newEntry.setSize(data.length);
                    newEntry.setCompressedSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    newEntry.setCrc(crc.getValue());
                } else {
                    newEntry.setMethod(ZipEntry.DEFLATED);
                }

                // 3. 核心对齐计算
                if (newEntry.getMethod() == ZipEntry.STORED) {
                    // 获取当前文件写入位置 (即 LFH 开始位置)
                    long currentPos = counter.getBytesWritten();

                    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                    int nameLen = nameBytes.length;

                    // 计算标准 Header 长度:
                    // LFH固定头(30) + 文件名长度 + 额外字段头(4, 即ID+Size)
                    long headerLenWithoutPadding = 30 + nameLen + 4;

                    // 预测数据开始位置
                    long predictedDataStart = currentPos + headerLenWithoutPadding;

                    // 计算需要的填充字节数
                    int padding = (int) ((ALIGNMENT - (predictedDataStart % ALIGNMENT)) % ALIGNMENT);

                    // 【关键修复】如果 padding 为 0，签名库可能会删除这个空的 Extra 块
                    // 导致文件头长度变短，破坏结构。
                    // 我们强制加 4 字节填充（依然保持 4 对齐），确保 Extra 字段有实体内容。
                    if (padding == 0) {
                        padding = 4;
                    }


                    if (isArsc) {
                        LogCatcher.i("ZipAligner", "正在对齐 resources.arsc | StartPos: " + currentPos + " | Padding: " + padding);
                    }

                    // 构造 Extra Field (zipalign ID: 0xD935)
                    // 总长度 = 4 (Header) + padding (Data)
                    byte[] extra = new byte[4 + padding];
                    extra[0] = (byte) 0x35;
                    extra[1] = (byte) 0xD9;
                    extra[2] = (byte) padding;
                    extra[3] = (byte) 0;
                    // padding data 默认为 0

                    newEntry.setExtra(extra);
                }

                zos.putNextEntry(newEntry);
                zos.write(data);
                zos.closeEntry();
            }

            zos.finish();

        } finally {
            if (zipFile != null) try { zipFile.close(); } catch (IOException e) {}
            if (zos != null) try { zos.close(); } catch (IOException e) {}
            if (counter != null) try { counter.close(); } catch (IOException e) {}
        }
    }

    private static byte[] readEntryData(ZipFile zf, ZipEntry entry) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = zf.getInputStream(entry)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }

    // 计数流 (直连 FileOutputStream)
    private static class ByteCountingOutputStream extends FilterOutputStream {
        private long bytesWritten = 0;

        public ByteCountingOutputStream(OutputStream out) {
            super(out); // 不使用 BufferedOutputStream
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
            bytesWritten++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            super.write(b);
            bytesWritten += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            super.write(b, off, len);
            bytesWritten += len;
        }

        public long getBytesWritten() {
            return bytesWritten;
        }
    }
}