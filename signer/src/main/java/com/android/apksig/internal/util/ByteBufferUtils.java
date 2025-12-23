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

package com.android.apksig.internal.util;

import java.nio.ByteBuffer;

public final class ByteBufferUtils {
    private ByteBufferUtils() {
    }

    /**
     * Returns the remaining data of the provided buffer as a new byte array and advances the
     * position of the buffer to the buffer's limit.
     */
    public static byte[] toByteArray(ByteBuffer buf) {
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
