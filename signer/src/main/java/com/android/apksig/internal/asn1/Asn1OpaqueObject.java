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

package com.android.apksig.internal.asn1;

import java.nio.ByteBuffer;

/**
 * Opaque holder of encoded ASN.1 stuff.
 */
public class Asn1OpaqueObject {
    private final ByteBuffer mEncoded;

    public Asn1OpaqueObject(ByteBuffer encoded) {
        mEncoded = encoded.slice();
    }

    public Asn1OpaqueObject(byte[] encoded) {
        mEncoded = ByteBuffer.wrap(encoded);
    }

    public ByteBuffer getEncoded() {
        return mEncoded.slice();
    }
}
