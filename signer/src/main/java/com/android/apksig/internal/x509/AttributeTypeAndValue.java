


package com.android.apksig.internal.x509;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1OpaqueObject;
import com.android.apksig.internal.asn1.Asn1Type;


@Asn1Class(type = Asn1Type.SEQUENCE)
public class AttributeTypeAndValue {

    @Asn1Field(index = 0, type = Asn1Type.OBJECT_IDENTIFIER)
    public String attrType;

    @Asn1Field(index = 1, type = Asn1Type.ANY)
    public Asn1OpaqueObject attrValue;
}