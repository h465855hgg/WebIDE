


package com.android.apksig.util;

import com.android.apksig.internal.util.ByteArrayDataSink;
import com.android.apksig.internal.util.MessageDigestSink;
import com.android.apksig.internal.util.OutputStreamDataSink;
import com.android.apksig.internal.util.RandomAccessFileDataSink;

import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;


public abstract class DataSinks {
    private DataSinks() {
    }

    /**
     * Returns a {@link DataSink} which outputs received data into the provided
     * {@link OutputStream}.
     */
    public static DataSink asDataSink(OutputStream out) {
        return new OutputStreamDataSink(out);
    }

    /**
     * Returns a {@link DataSink} which outputs received data into the provided file, sequentially,
     * starting at the beginning of the file.
     */
    public static DataSink asDataSink(RandomAccessFile file) {
        return new RandomAccessFileDataSink(file);
    }

    /**
     * Returns a {@link DataSink} which forwards data into the provided {@link MessageDigest}
     * instances via their {@code update} method. Each {@code MessageDigest} instance receives the
     * same data.
     */
    public static DataSink asDataSink(MessageDigest... digests) {
        return new MessageDigestSink(digests);
    }

    /**
     * Returns a new in-memory {@link DataSink} which exposes all data consumed so far via the
     * {@link DataSource} interface.
     */
    public static ReadableDataSink newInMemoryDataSink() {
        return new ByteArrayDataSink();
    }

    /**
     * Returns a new in-memory {@link DataSink} which exposes all data consumed so far via the
     * {@link DataSource} interface.
     *
     * @param initialCapacity initial capacity in bytes
     */
    public static ReadableDataSink newInMemoryDataSink(int initialCapacity) {
        return new ByteArrayDataSink(initialCapacity);
    }
}
