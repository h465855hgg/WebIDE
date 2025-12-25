/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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

package com.android.apksig.internal.util;

import com.android.apksig.util.DataSink;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * {@link DataSink} which outputs received data into the associated file, sequentially.
 */
public class RandomAccessFileDataSink implements DataSink {

    private final RandomAccessFile mFile;
    private final FileChannel mFileChannel;
    private long mPosition;

    /**
     * Constructs a new {@code RandomAccessFileDataSink} which stores output starting from the
     * beginning of the provided file.
     */
    public RandomAccessFileDataSink(RandomAccessFile file) {
        this(file, 0);
    }

    /**
     * Constructs a new {@code RandomAccessFileDataSink} which stores output starting from the
     * specified position of the provided file.
     */
    public RandomAccessFileDataSink(RandomAccessFile file, long startPosition) {
        if (file == null) {
            throw new NullPointerException("file == null");
        }
        if (startPosition < 0) {
            throw new IllegalArgumentException("startPosition: " + startPosition);
        }
        mFile = file;
        mFileChannel = file.getChannel();
        mPosition = startPosition;
    }

    /**
     * Returns the underlying {@link RandomAccessFile}.
     */
    public RandomAccessFile getFile() {
        return mFile;
    }

    @Override
    public void consume(byte[] buf, int offset, int length) throws IOException {
        if (offset < 0) {
            // Must perform this check here because RandomAccessFile.write doesn't throw when offset
            // is negative but length is 0
            throw new IndexOutOfBoundsException("offset: " + offset);
        }
        if (offset > buf.length) {
            // Must perform this check here because RandomAccessFile.write doesn't throw when offset
            // is too large but length is 0
            throw new IndexOutOfBoundsException(
                    "offset: " + offset + ", buf.length: " + buf.length);
        }
        if (length == 0) {
            return;
        }

        synchronized (mFile) {
            mFile.seek(mPosition);
            mFile.write(buf, offset, length);
            mPosition += length;
        }
    }

    @Override
    public void consume(ByteBuffer buf) throws IOException {
        int length = buf.remaining();
        if (length == 0) {
            return;
        }

        synchronized (mFile) {
            mFile.seek(mPosition);
            while (buf.hasRemaining()) {
                mFileChannel.write(buf);
            }
            mPosition += length;
        }
    }
}
