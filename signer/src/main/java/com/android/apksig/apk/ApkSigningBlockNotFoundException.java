


package com.android.apksig.apk;


public class ApkSigningBlockNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public ApkSigningBlockNotFoundException(String message) {
        super(message);
    }

    public ApkSigningBlockNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
