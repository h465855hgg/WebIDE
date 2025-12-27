


package com.android.apksig.internal.pkcs7;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1OpaqueObject;
import com.android.apksig.internal.asn1.Asn1Tagging;
import com.android.apksig.internal.asn1.Asn1Type;


@Asn1Class(type = Asn1Type.SEQUENCE)
public class ContentInfo {

    @Asn1Field(index = 1, type = Asn1Type.OBJECT_IDENTIFIER)
    public String contentType;

    @Asn1Field(index = 2, type = Asn1Type.ANY, tagging = Asn1Tagging.EXPLICIT, tagNumber = 0)
    public Asn1OpaqueObject content;
}
