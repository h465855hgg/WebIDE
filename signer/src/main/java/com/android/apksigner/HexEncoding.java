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

package com.android.apksigner;

import java.nio.ByteBuffer;

/**
 * Hexadecimal encoding where each byte is represented by two hexadecimal digits.
 */
class HexEncoding {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Hidden constructor to prevent instantiation.
     */
    private HexEncoding() {
    }

    /**
     * Encodes the provided data as a hexadecimal string.
     */
    public static String encode(byte[] data, int offset, int length) {
        StringBuilder result = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            byte b = data[offset + i];
            result.append(HEX_DIGITS[(b >>> 4) & 0x0f]);
            result.append(HEX_DIGITS[b & 0x0f]);
        }
        return result.toString();
    }

    /**
     * Encodes the provided data as a hexadecimal string.
     */
    public static String encode(byte[] data) {
        return encode(data, 0, data.length);
    }

    /**
     * Encodes the remaining bytes of the provided {@link ByteBuffer} as a hexadecimal string.
     */
    public static String encodeRemaining(ByteBuffer data) {
        return encode(data.array(), data.arrayOffset() + data.position(), data.remaining());
    }
}
