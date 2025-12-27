


package com.android.apksig.apk;


public class MinSdkVersionException extends ApkFormatException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code MinSdkVersionException} with the provided message.
     */
    public MinSdkVersionException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code MinSdkVersionException} with the provided message and cause.
     */
    public MinSdkVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
