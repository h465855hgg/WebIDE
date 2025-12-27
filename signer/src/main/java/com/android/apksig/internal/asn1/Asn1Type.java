


package com.android.apksig.internal.asn1;

public enum Asn1Type {
    ANY,
    CHOICE,
    INTEGER,
    OBJECT_IDENTIFIER,
    OCTET_STRING,
    SEQUENCE,
    SEQUENCE_OF,
    SET_OF,
    BIT_STRING,
    UTC_TIME,
    GENERALIZED_TIME,
    BOOLEAN,
    // This type can be used to annotate classes that encapsulate ASN.1 structures that are not
    // classified as a SEQUENCE or SET.
    UNENCODED_CONTAINER
}
