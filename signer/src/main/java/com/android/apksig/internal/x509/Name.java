


package com.android.apksig.internal.x509;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1Type;

import java.util.List;


@Asn1Class(type = Asn1Type.CHOICE)
public class Name {

    // This field is the RDNSequence specified in RFC 5280.
    @Asn1Field(index = 0, type = Asn1Type.SEQUENCE_OF)
    public List<RelativeDistinguishedName> relativeDistinguishedNames;
}
