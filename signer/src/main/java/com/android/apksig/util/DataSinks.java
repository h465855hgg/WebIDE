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

package com.android.apksig.util;

import com.android.apksig.internal.util.ByteArrayDataSink;
import com.android.apksig.internal.util.MessageDigestSink;
import com.android.apksig.internal.util.OutputStreamDataSink;
import com.android.apksig.internal.util.RandomAccessFileDataSink;

import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;

/**
 * Utility methods for working with {@link DataSink} abstraction.
 */
public abstract class DataSinks {
    private DataSinks() {
    }

    /**
     * Returns a {@link DataSink} which outputs received data into the provided
     * {@link OutputStream}.
     */
    public static DataSink asDataSink(OutputStream out) {
        return new OutputStreamDataSink(out);
    }

    /**
     * Returns a {@link DataSink} which outputs received data into the provided file, sequentially,
     * starting at the beginning of the file.
     */
    public static DataSink asDataSink(RandomAccessFile file) {
        return new RandomAccessFileDataSink(file);
    }

    /**
     * Returns a {@link DataSink} which forwards data into the provided {@link MessageDigest}
     * instances via their {@code update} method. Each {@code MessageDigest} instance receives the
     * same data.
     */
    public static DataSink asDataSink(MessageDigest... digests) {
        return new MessageDigestSink(digests);
    }

    /**
     * Returns a new in-memory {@link DataSink} which exposes all data consumed so far via the
     * {@link DataSource} interface.
     */
    public static ReadableDataSink newInMemoryDataSink() {
        return new ByteArrayDataSink();
    }

    /**
     * Returns a new in-memory {@link DataSink} which exposes all data consumed so far via the
     * {@link DataSource} interface.
     *
     * @param initialCapacity initial capacity in bytes
     */
    public static ReadableDataSink newInMemoryDataSink(int initialCapacity) {
        return new ByteArrayDataSink(initialCapacity);
    }
}
