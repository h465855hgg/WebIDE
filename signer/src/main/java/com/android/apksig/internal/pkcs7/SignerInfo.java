


package com.android.apksig.internal.pkcs7;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1OpaqueObject;
import com.android.apksig.internal.asn1.Asn1Tagging;
import com.android.apksig.internal.asn1.Asn1Type;

import java.nio.ByteBuffer;
import java.util.List;


@Asn1Class(type = Asn1Type.SEQUENCE)
public class SignerInfo {

    @Asn1Field(index = 0, type = Asn1Type.INTEGER)
    public int version;

    @Asn1Field(index = 1, type = Asn1Type.CHOICE)
    public SignerIdentifier sid;

    @Asn1Field(index = 2, type = Asn1Type.SEQUENCE)
    public AlgorithmIdentifier digestAlgorithm;

    @Asn1Field(
            index = 3,
            type = Asn1Type.SET_OF,
            tagging = Asn1Tagging.IMPLICIT, tagNumber = 0,
            optional = true)
    public Asn1OpaqueObject signedAttrs;

    @Asn1Field(index = 4, type = Asn1Type.SEQUENCE)
    public AlgorithmIdentifier signatureAlgorithm;

    @Asn1Field(index = 5, type = Asn1Type.OCTET_STRING)
    public ByteBuffer signature;

    @Asn1Field(
            index = 6,
            type = Asn1Type.SET_OF,
            tagging = Asn1Tagging.IMPLICIT, tagNumber = 1,
            optional = true)
    public List<Attribute> unsignedAttrs;
}
