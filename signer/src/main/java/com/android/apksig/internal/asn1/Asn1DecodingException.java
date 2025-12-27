


package com.android.apksig.internal.asn1;


public class Asn1DecodingException extends Exception {
    private static final long serialVersionUID = 1L;

    public Asn1DecodingException(String message) {
        super(message);
    }

    public Asn1DecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
