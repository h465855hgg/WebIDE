


package com.android.apksig.internal.asn1;

public enum Asn1TagClass {
    UNIVERSAL,
    APPLICATION,
    CONTEXT_SPECIFIC,
    PRIVATE,

    /**
     * Not really an actual tag class: decoder/encoder will attempt to deduce the correct tag class
     * automatically.
     */
    AUTOMATIC,
}
