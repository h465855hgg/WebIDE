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

package com.android.apksig.internal.x509;

import com.android.apksig.internal.asn1.Asn1Class;
import com.android.apksig.internal.asn1.Asn1Field;
import com.android.apksig.internal.asn1.Asn1Type;

import java.math.BigInteger;

/**
 * {@code RSAPublicKey} as specified in RFC 3279.
 */
@Asn1Class(type = Asn1Type.SEQUENCE)
public class RSAPublicKey {
    @Asn1Field(index = 0, type = Asn1Type.INTEGER)
    public BigInteger modulus;

    @Asn1Field(index = 1, type = Asn1Type.INTEGER)
    public BigInteger publicExponent;
}
