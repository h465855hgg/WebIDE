


package com.android.apksig.util;

import java.io.IOException;
import java.nio.ByteBuffer;


public interface DataSink {

    /**
     * Consumes the provided chunk of data.
     *
     * <p>This data sink guarantees to not hold references to the provided buffer after this method
     * terminates.
     *
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} are negative, or if
     *                                   {@code offset + length} is greater than {@code buf.length}.
     */
    void consume(byte[] buf, int offset, int length) throws IOException;

    /**
     * Consumes all remaining data in the provided buffer and advances the buffer's position
     * to the buffer's limit.
     *
     * <p>This data sink guarantees to not hold references to the provided buffer after this method
     * terminates.
     */
    void consume(ByteBuffer buf) throws IOException;
}
