/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.android.apksig.internal.pkcs7;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1OpaqueObject;
import com.android.apksig.internal.asn1.Asn1Tagging;
import com.android.apksig.internal.asn1.Asn1Type;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * PKCS #7 {@code SignedData} as specified in RFC 5652.
 */
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
