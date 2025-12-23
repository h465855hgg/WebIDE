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

import com.android.apksig.util.DataSink;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * {@link DataSink} which copies provided input into each of the sinks provided to it.
 */
public class TeeDataSink implements DataSink {

    private final DataSink[] mSinks;

    public TeeDataSink(DataSink[] sinks) {
        mSinks = sinks;
    }

    @Override
    public void consume(byte[] buf, int offset, int length) throws IOException {
        for (DataSink sink : mSinks) {
            sink.consume(buf, offset, length);
        }
    }

    @Override
    public void consume(ByteBuffer buf) throws IOException {
        int originalPosition = buf.position();
        for (int i = 0; i < mSinks.length; i++) {
            if (i > 0) {
                buf.position(originalPosition);
            }
            mSinks[i].consume(buf);
        }
    }
}
