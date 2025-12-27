

package com.android.apksig.internal.util;

import com.android.apksig.util.DataSink;

import java.nio.ByteBuffer;
import java.security.MessageDigest;


public class MessageDigestSink implements DataSink {

    private final MessageDigest[] mMessageDigests;

    public MessageDigestSink(MessageDigest[] digests) {
        mMessageDigests = digests;
    }

    @Override
    public void consume(byte[] buf, int offset, int length) {
        for (MessageDigest md : mMessageDigests) {
            md.update(buf, offset, length);
        }
    }

    @Override
    public void consume(ByteBuffer buf) {
        int originalPosition = buf.position();
        for (MessageDigest md : mMessageDigests) {
            // Reset the position back to the original because the previous iteration's
            // MessageDigest.update set the buffer's position to the buffer's limit.
            buf.position(originalPosition);
            md.update(buf);
        }
    }
}
