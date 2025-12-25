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

import java.nio.ByteBuffer;
import java.security.MessageDigest;

/**
 * Data sink which feeds all received data into the associated {@link MessageDigest} instances. Each
 * {@code MessageDigest} instance receives the same data.
 */
public class MessageDigestSink implements DataSink {

    private final MessageDigest[] mMessageDigests;

    public MessageDigestSink(MessageDigest[] digests) {
        mMessageDigests = digests;
    }

    @Override
    public void consume(byte[] buf, int offset, int length) {
        for (MessageDigest md : mMessageDigests) {
            md.update(buf, offset, length);
        }
    }

    @Override
    public void consume(ByteBuffer buf) {
        int originalPosition = buf.position();
        for (MessageDigest md : mMessageDigests) {
            // Reset the position back to the original because the previous iteration's
            // MessageDigest.update set the buffer's position to the buffer's limit.
            buf.position(originalPosition);
            md.update(buf);
        }
    }
}
