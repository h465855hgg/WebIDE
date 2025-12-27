


package com.android.apksig.internal.x509;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1Type;


@Asn1Class(type = Asn1Type.CHOICE)
public class Time {

    @Asn1Field(type = Asn1Type.UTC_TIME)
    public String utcTime;

    @Asn1Field(type = Asn1Type.GENERALIZED_TIME)
    public String generalizedTime;
}
