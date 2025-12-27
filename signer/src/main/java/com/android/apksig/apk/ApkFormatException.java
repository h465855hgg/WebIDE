


package com.android.apksig.apk;


public class ApkFormatException extends Exception {
    private static final long serialVersionUID = 1L;

    public ApkFormatException(String message) {
        super(message);
    }

    public ApkFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
