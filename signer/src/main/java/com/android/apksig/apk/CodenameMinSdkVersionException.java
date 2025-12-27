


package com.android.apksig.apk;


public class CodenameMinSdkVersionException extends MinSdkVersionException {

    private static final long serialVersionUID = 1L;

    /**
     * Encountered codename.
     */
    private final String mCodename;

    /**
     * Constructs a new {@code MinSdkVersionCodenameException} with the provided message and
     * codename.
     */
    public CodenameMinSdkVersionException(String message, String codename) {
        super(message);
        mCodename = codename;
    }

    /**
     * Returns the codename.
     */
    public String getCodename() {
        return mCodename;
    }
}
