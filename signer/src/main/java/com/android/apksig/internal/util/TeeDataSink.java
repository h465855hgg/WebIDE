


package com.android.apksig.internal.util;

import com.android.apksig.util.DataSink;

import java.io.IOException;
import java.nio.ByteBuffer;


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
