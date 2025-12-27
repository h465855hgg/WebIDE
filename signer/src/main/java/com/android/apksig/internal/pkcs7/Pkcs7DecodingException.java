


package com.android.apksig.internal.pkcs7;


public class Pkcs7DecodingException extends Exception {
    private static final long serialVersionUID = 1L;

    public Pkcs7DecodingException(String message) {
        super(message);
    }

    public Pkcs7DecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
