


package com.android.apksig.internal.asn1;

import java.nio.ByteBuffer;


public class Asn1OpaqueObject {
    private final ByteBuffer mEncoded;

    public Asn1OpaqueObject(ByteBuffer encoded) {
        mEncoded = encoded.slice();
    }

    public Asn1OpaqueObject(byte[] encoded) {
        mEncoded = ByteBuffer.wrap(encoded);
    }

    public ByteBuffer getEncoded() {
        return mEncoded.slice();
    }
}
