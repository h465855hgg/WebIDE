


package com.android.apksig.internal.asn1.ber;

import java.nio.ByteBuffer;


public class BerDataValue {
    private final ByteBuffer mEncoded;
    private final ByteBuffer mEncodedContents;
    private final int mTagClass;
    private final boolean mConstructed;
    private final int mTagNumber;

    BerDataValue(
            ByteBuffer encoded,
            ByteBuffer encodedContents,
            int tagClass,
            boolean constructed,
            int tagNumber) {
        mEncoded = encoded;
        mEncodedContents = encodedContents;
        mTagClass = tagClass;
        mConstructed = constructed;
        mTagNumber = tagNumber;
    }

    /**
     * Returns the tag class of this data value. See {@link BerEncoding} {@code TAG_CLASS}
     * constants.
     */
    public int getTagClass() {
        return mTagClass;
    }

    /**
     * Returns {@code true} if the content octets of this data value are the complete BER encoding
     * of one or more data values, {@code false} if the content octets of this data value directly
     * represent the value.
     */
    public boolean isConstructed() {
        return mConstructed;
    }

    /**
     * Returns the tag number of this data value. See {@link BerEncoding} {@code TAG_NUMBER}
     * constants.
     */
    public int getTagNumber() {
        return mTagNumber;
    }

    /**
     * Returns the encoded form of this data value.
     */
    public ByteBuffer getEncoded() {
        return mEncoded.slice();
    }

    /**
     * Returns the encoded contents of this data value.
     */
    public ByteBuffer getEncodedContents() {
        return mEncodedContents.slice();
    }

    /**
     * Returns a new reader of the contents of this data value.
     */
    public BerDataValueReader contentsReader() {
        return new ByteBufferBerDataValueReader(getEncodedContents());
    }

    /**
     * Returns a new reader which returns just this data value. This may be useful for re-reading
     * this value in different contexts.
     */
    public BerDataValueReader dataValueReader() {
        return new ParsedValueReader(this);
    }

    private static final class ParsedValueReader implements BerDataValueReader {
        private final BerDataValue mValue;
        private boolean mValueOutput;

        public ParsedValueReader(BerDataValue value) {
            mValue = value;
        }

        @Override
        public BerDataValue readDataValue() throws BerDataValueFormatException {
            if (mValueOutput) {
                return null;
            }
            mValueOutput = true;
            return mValue;
        }
    }
}
