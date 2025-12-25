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
import com.android.apksig.util.DataSource;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * {@link DataSource} backed by a {@link ByteBuffer}.
 */
public class ByteBufferDataSource implements DataSource {

    private final ByteBuffer mBuffer;
    private final int mSize;

    /**
     * Constructs a new {@code ByteBufferDigestSource} based on the data contained in the provided
     * buffer between the buffer's position and limit.
     */
    public ByteBufferDataSource(ByteBuffer buffer) {
        this(buffer, true);
    }

    /**
     * Constructs a new {@code ByteBufferDigestSource} based on the data contained in the provided
     * buffer between the buffer's position and limit.
     */
    private ByteBufferDataSource(ByteBuffer buffer, boolean sliceRequired) {
        mBuffer = (sliceRequired) ? buffer.slice() : buffer;
        mSize = buffer.remaining();
    }

    @Override
    public long size() {
        return mSize;
    }

    @Override
    public ByteBuffer getByteBuffer(long offset, int size) {
        checkChunkValid(offset, size);

        // checkChunkValid ensures that it's OK to cast offset to int.
        int chunkPosition = (int) offset;
        int chunkLimit = chunkPosition + size;
        // Creating a slice of ByteBuffer modifies the state of the source ByteBuffer (position
        // and limit fields, to be more specific). We thus use synchronization around these
        // state-changing operations to make instances of this class thread-safe.
        synchronized (mBuffer) {
            // ByteBuffer.limit(int) and .position(int) check that that the position >= limit
            // invariant is not broken. Thus, the only way to safely change position and limit
            // without caring about their current values is to first set position to 0 or set the
            // limit to capacity.
            mBuffer.position(0);

            mBuffer.limit(chunkLimit);
            mBuffer.position(chunkPosition);
            return mBuffer.slice();
        }
    }

    @Override
    public void copyTo(long offset, int size, ByteBuffer dest) {
        dest.put(getByteBuffer(offset, size));
    }

    @Override
    public void feed(long offset, long size, DataSink sink) throws IOException {
        if ((size < 0) || (size > mSize)) {
            throw new IndexOutOfBoundsException("size: " + size + ", source size: " + mSize);
        }
        sink.consume(getByteBuffer(offset, (int) size));
    }

    @Override
    public ByteBufferDataSource slice(long offset, long size) {
        if ((offset == 0) && (size == mSize)) {
            return this;
        }
        if ((size < 0) || (size > mSize)) {
            throw new IndexOutOfBoundsException("size: " + size + ", source size: " + mSize);
        }
        return new ByteBufferDataSource(
                getByteBuffer(offset, (int) size),
                false // no need to slice -- it's already a slice
        );
    }

    private void checkChunkValid(long offset, long size) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException("offset: " + offset);
        }
        if (size < 0) {
            throw new IndexOutOfBoundsException("size: " + size);
        }
        if (offset > mSize) {
            throw new IndexOutOfBoundsException(
                    "offset (" + offset + ") > source size (" + mSize + ")");
        }
        long endOffset = offset + size;
        if (endOffset < offset) {
            throw new IndexOutOfBoundsException(
                    "offset (" + offset + ") + size (" + size + ") overflow");
        }
        if (endOffset > mSize) {
            throw new IndexOutOfBoundsException(
                    "offset (" + offset + ") + size (" + size + ") > source size (" + mSize + ")");
        }
    }
}
