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

package com.android.apksig.internal.pkcs7;

/**
 * Indicates that an error was encountered while decoding a PKCS #7 structure.
 */
public class Pkcs7DecodingException extends Exception {
    private static final long serialVersionUID = 1L;

    public Pkcs7DecodingException(String message) {
        super(message);
    }

    public Pkcs7DecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
