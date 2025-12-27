


package com.android.apksig.util;

import java.io.IOException;
import java.nio.ByteBuffer;


public interface DataSource {

    /**
     * Returns the amount of data (in bytes) contained in this data source.
     */
    long size();

    /**
     * Feeds the specified chunk from this data source into the provided sink.
     *
     * @param offset index (in bytes) at which the chunk starts inside data source
     * @param size   size (in bytes) of the chunk
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    void feed(long offset, long size, DataSink sink) throws IOException;

    /**
     * Returns a buffer holding the contents of the specified chunk of data from this data source.
     * Changes to the data source are not guaranteed to be reflected in the returned buffer.
     * Similarly, changes in the buffer are not guaranteed to be reflected in the data source.
     *
     * <p>The returned buffer's position is {@code 0}, and the buffer's limit and capacity is
     * {@code size}.
     *
     * @param offset index (in bytes) at which the chunk starts inside data source
     * @param size   size (in bytes) of the chunk
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    ByteBuffer getByteBuffer(long offset, int size) throws IOException;

    /**
     * Copies the specified chunk from this data source into the provided destination buffer,
     * advancing the destination buffer's position by {@code size}.
     *
     * @param offset index (in bytes) at which the chunk starts inside data source
     * @param size   size (in bytes) of the chunk
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    void copyTo(long offset, int size, ByteBuffer dest) throws IOException;

    /**
     * Returns a data source representing the specified region of data of this data source. Changes
     * to data represented by this data source will also be visible in the returned data source.
     *
     * @param offset index (in bytes) at which the region starts inside data source
     * @param size   size (in bytes) of the region
     * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if
     *                                   {@code offset + size} is greater than {@link #size()}.
     */
    DataSource slice(long offset, long size);
}
