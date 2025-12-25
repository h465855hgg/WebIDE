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
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * {@link DataSink} which outputs received data into the associated {@link OutputStream}.
 */
public class OutputStreamDataSink implements DataSink {

    private static final int MAX_READ_CHUNK_SIZE = 65536;

    private final OutputStream mOut;

    /**
     * Constructs a new {@code OutputStreamDataSink} which outputs received data into the provided
     * {@link OutputStream}.
     */
    public OutputStreamDataSink(OutputStream out) {
        if (out == null) {
            throw new NullPointerException("out == null");
        }
        mOut = out;
    }

    /**
     * Returns {@link OutputStream} into which this data sink outputs received data.
     */
    public OutputStream getOutputStream() {
        return mOut;
    }

    @Override
    public void consume(byte[] buf, int offset, int length) throws IOException {
        mOut.write(buf, offset, length);
    }

    @Override
    public void consume(ByteBuffer buf) throws IOException {
        if (!buf.hasRemaining()) {
            return;
        }

        if (buf.hasArray()) {
            mOut.write(
                    buf.array(),
                    buf.arrayOffset() + buf.position(),
                    buf.remaining());
            buf.position(buf.limit());
        } else {
            byte[] tmp = new byte[Math.min(buf.remaining(), MAX_READ_CHUNK_SIZE)];
            while (buf.hasRemaining()) {
                int chunkSize = Math.min(buf.remaining(), tmp.length);
                buf.get(tmp, 0, chunkSize);
                mOut.write(tmp, 0, chunkSize);
            }
        }
    }
}
