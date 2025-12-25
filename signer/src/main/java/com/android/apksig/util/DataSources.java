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

package com.android.apksig.util;

import com.android.apksig.internal.util.ByteBufferDataSource;
import com.android.apksig.internal.util.FileChannelDataSource;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Utility methods for working with {@link DataSource} abstraction.
 */
public abstract class DataSources {
    private DataSources() {
    }

    /**
     * Returns a {@link DataSource} backed by the provided {@link ByteBuffer}. The data source
     * represents the data contained between the position and limit of the buffer. Changes to the
     * buffer's contents will be visible in the data source.
     */
    public static DataSource asDataSource(ByteBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException();
        }
        return new ByteBufferDataSource(buffer);
    }

    /**
     * Returns a {@link DataSource} backed by the provided {@link RandomAccessFile}. Changes to the
     * file, including changes to size of file, will be visible in the data source.
     */
    public static DataSource asDataSource(RandomAccessFile file) {
        return asDataSource(file.getChannel());
    }

    /**
     * Returns a {@link DataSource} backed by the provided region of the {@link RandomAccessFile}.
     * Changes to the file will be visible in the data source.
     */
    public static DataSource asDataSource(RandomAccessFile file, long offset, long size) {
        return asDataSource(file.getChannel(), offset, size);
    }

    /**
     * Returns a {@link DataSource} backed by the provided {@link FileChannel}. Changes to the
     * file, including changes to size of file, will be visible in the data source.
     */
    public static DataSource asDataSource(FileChannel channel) {
        if (channel == null) {
            throw new NullPointerException();
        }
        return new FileChannelDataSource(channel);
    }

    /**
     * Returns a {@link DataSource} backed by the provided region of the {@link FileChannel}.
     * Changes to the file will be visible in the data source.
     */
    public static DataSource asDataSource(FileChannel channel, long offset, long size) {
        if (channel == null) {
            throw new NullPointerException();
        }
        return new FileChannelDataSource(channel, offset, size);
    }
}
