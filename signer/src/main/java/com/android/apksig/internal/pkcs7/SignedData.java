


package com.android.apksig.internal.pkcs7;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1OpaqueObject;
import com.android.apksig.internal.asn1.Asn1Tagging;
import com.android.apksig.internal.asn1.Asn1Type;

import java.nio.ByteBuffer;
import java.util.List;


@Asn1Class(type = Asn1Type.SEQUENCE)
public class SignedData {

    @Asn1Field(index = 0, type = Asn1Type.INTEGER)
    public int version;

    @Asn1Field(index = 1, type = Asn1Type.SET_OF)
    public List<AlgorithmIdentifier> digestAlgorithms;

    @Asn1Field(index = 2, type = Asn1Type.SEQUENCE)
    public EncapsulatedContentInfo encapContentInfo;

    @Asn1Field(
            index = 3,
            type = Asn1Type.SET_OF,
            tagging = Asn1Tagging.IMPLICIT, tagNumber = 0,
            optional = true)
    public List<Asn1OpaqueObject> certificates;

    @Asn1Field(
            index = 4,
            type = Asn1Type.SET_OF,
            tagging = Asn1Tagging.IMPLICIT, tagNumber = 1,
            optional = true)
    public List<ByteBuffer> crls;

    @Asn1Field(index = 5, type = Asn1Type.SET_OF)
    public List<SignerInfo> signerInfos;
}
