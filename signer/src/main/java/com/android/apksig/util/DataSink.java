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
 * Consumer of input data which may be provided in one go or in chunks.
 */
public interface DataSink {

    /**
     * Consumes the provided chunk of data.
     *
     * <p>This data sink guarantees to not hold references to the provided buffer after this method
     * terminates.
     *
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} are negative, or if
     *                                   {@code offset + length} is greater than {@code buf.length}.
     */
    void consume(byte[] buf, int offset, int length) throws IOException;

    /**
     * Consumes all remaining data in the provided buffer and advances the buffer's position
     * to the buffer's limit.
     *
     * <p>This data sink guarantees to not hold references to the provided buffer after this method
     * terminates.
     */
    void consume(ByteBuffer buf) throws IOException;
}
