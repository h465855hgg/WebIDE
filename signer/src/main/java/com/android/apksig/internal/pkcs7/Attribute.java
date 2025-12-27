


package com.android.apksig.internal.pkcs7;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1OpaqueObject;
import com.android.apksig.internal.asn1.Asn1Type;

import java.util.List;


@Asn1Class(type = Asn1Type.SEQUENCE)
public class Attribute {

    @Asn1Field(index = 0, type = Asn1Type.OBJECT_IDENTIFIER)
    public String attrType;

    @Asn1Field(index = 1, type = Asn1Type.SET_OF)
    public List<Asn1OpaqueObject> attrValues;
}
