


package com.android.apksig.internal.asn1.ber;


public interface BerDataValueReader {

    /**
     * Returns the next data value or {@code null} if end of input has been reached.
     *
     * @throws BerDataValueFormatException if the value being read is malformed.
     */
    BerDataValue readDataValue() throws BerDataValueFormatException;
}
