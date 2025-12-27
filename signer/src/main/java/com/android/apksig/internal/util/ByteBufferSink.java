


package com.android.apksig.internal.util;

import com.android.apksig.util.DataSink;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;


public class ByteBufferSink implements DataSink {

    private final ByteBuffer mBuffer;

    public ByteBufferSink(ByteBuffer buffer) {
        mBuffer = buffer;
    }

    public ByteBuffer getBuffer() {
        return mBuffer;
    }

    @Override
    public void consume(byte[] buf, int offset, int length) throws IOException {
        try {
            mBuffer.put(buf, offset, length);
        } catch (BufferOverflowException e) {
            throw new IOException(
                    "Insufficient space in output buffer for " + length + " bytes", e);
        }
    }

    @Override
    public void consume(ByteBuffer buf) throws IOException {
        int length = buf.remaining();
        try {
            mBuffer.put(buf);
        } catch (BufferOverflowException e) {
            throw new IOException(
                    "Insufficient space in output buffer for " + length + " bytes", e);
        }
    }
}
