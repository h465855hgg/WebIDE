


package com.android.apksig.internal.pkcs7;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1Tagging;
import com.android.apksig.internal.asn1.Asn1Type;

import java.nio.ByteBuffer;


@Asn1Class(type = Asn1Type.SEQUENCE)
public class EncapsulatedContentInfo {

    @Asn1Field(index = 0, type = Asn1Type.OBJECT_IDENTIFIER)
    public String contentType;

    @Asn1Field(
            index = 1,
            type = Asn1Type.OCTET_STRING,
            tagging = Asn1Tagging.EXPLICIT, tagNumber = 0,
            optional = true)
    public ByteBuffer content;

    public EncapsulatedContentInfo() {
    }

    public EncapsulatedContentInfo(String contentTypeOid) {
        contentType = contentTypeOid;
    }
}
