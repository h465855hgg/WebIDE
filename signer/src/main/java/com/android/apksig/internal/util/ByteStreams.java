


package com.android.apksig.internal.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


public final class ByteStreams {
    private ByteStreams() {
    }

    /**
     * Returns the data remaining in the provided input stream as a byte array
     */
    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            result.write(buf, 0, chunkSize);
        }
        return result.toByteArray();
    }
}
