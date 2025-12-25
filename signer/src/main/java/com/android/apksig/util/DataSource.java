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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Abstract representation of a source of data.
 *
 * <p>This abstraction serves three purposes:
 * <ul>
 * <li>Transparent handling of different types of sources, such as {@code byte[]},
 *     {@link java.nio.ByteBuffer}, {@link java.io.RandomAccessFile}, memory-mapped file.</li>
 * <li>Support sources larger than 2 GB. If all sources were smaller than 2 GB, {@code ByteBuffer}
 *     may have worked as the unifying abstraction.</li>
 * <li>Support sources which do not fit into logical memory as a contiguous region.</li>
 * </ul>
 *
 * <p>There are following ways to obtain a chunk of data from the data source:
 * <ul>
 * <li>Stream the chunk's data into a {@link DataSink} using
 *     {@link #feed(long, long, DataSink) feed}. This is best suited for scenarios where there is no
 *     need to have the chunk's data accessible at the same time, for example, when computing the
 *     digest of the chunk. If you need to keep the chunk's data around after {@code feed}
 *     completes, you must create a copy during {@code feed}. However, in that case the following
 *     methods of obtaining the chunk's data may be more appropriate.</li>
 * <li>Obtain a {@link ByteBuffer} containing the chunk's data using
 *     {@link #getByteBuffer(long, int) getByteBuffer}. Depending on the data source, the chunk's
 *     data may or may not be copied by this operation. This is best suited for scenarios where
 *     you need to access the chunk's data in arbitrary order, but don't need to modify the data and
 *     thus don't require a copy of the data.</li>
 * <li>Copy the chunk's data to a {@link ByteBuffer} using
 *     {@link #copyTo(long, int, ByteBuffer) copyTo}. This is best suited for scenarios where
 *     you require a copy of the chunk's data, such as to when you need to modify the data.
 *     </li>
 * </ul>
 */
public interface DataSource {

    /**
     * Returns the amount of data (in bytes) contained in this data source.
     */
    long size();

    /**
     * Feeds the specified chunk from this data source into the provided sink.
     *
     * @param offset index (in bytes) at which the chunk starts inside data source
     * @param size   size (in bytes) of the chunk
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    void feed(long offset, long size, DataSink sink) throws IOException;

    /**
     * Returns a buffer holding the contents of the specified chunk of data from this data source.
     * Changes to the data source are not guaranteed to be reflected in the returned buffer.
     * Similarly, changes in the buffer are not guaranteed to be reflected in the data source.
     *
     * <p>The returned buffer's position is {@code 0}, and the buffer's limit and capacity is
     * {@code size}.
     *
     * @param offset index (in bytes) at which the chunk starts inside data source
     * @param size   size (in bytes) of the chunk
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    ByteBuffer getByteBuffer(long offset, int size) throws IOException;

    /**
     * Copies the specified chunk from this data source into the provided destination buffer,
     * advancing the destination buffer's position by {@code size}.
     *
     * @param offset index (in bytes) at which the chunk starts inside data source
     * @param size   size (in bytes) of the chunk
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    void copyTo(long offset, int size, ByteBuffer dest) throws IOException;

    /**
     * Returns a data source representing the specified region of data of this data source. Changes
     * to data represented by this data source will also be visible in the returned data source.
     *
     * @param offset index (in bytes) at which the region starts inside data source
     * @param size   size (in bytes) of the region
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    DataSource slice(long offset, long size);
}
