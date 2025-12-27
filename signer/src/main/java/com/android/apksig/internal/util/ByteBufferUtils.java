


package com.android.apksig.internal.util;

import java.nio.ByteBuffer;

public final class ByteBufferUtils {
    private ByteBufferUtils() {
    }

    /**
     * Returns the remaining data of the provided buffer as a new byte array and advances the
     * position of the buffer to the buffer's limit.
     */
    public static byte[] toByteArray(ByteBuffer buf) {
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
