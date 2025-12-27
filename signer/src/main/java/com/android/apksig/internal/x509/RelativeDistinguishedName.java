


package com.android.apksig.internal.x509;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1Type;

import java.util.List;


@Asn1Class(type = Asn1Type.UNENCODED_CONTAINER)
public class RelativeDistinguishedName {

    @Asn1Field(index = 0, type = Asn1Type.SET_OF)
    public List<AttributeTypeAndValue> attributes;
}
