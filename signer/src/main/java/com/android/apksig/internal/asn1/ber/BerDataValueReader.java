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

package com.android.apksig.internal.asn1.ber;

/**
 * Reader of ASN.1 Basic Encoding Rules (BER) data values.
 *
 * <p>BER data value reader returns data values, one by one, from a source. The interpretation of
 * data values (e.g., how to obtain a numeric value from an INTEGER data value, or how to extract
 * the elements of a SEQUENCE value) is left to clients of the reader.
 */
public interface BerDataValueReader {

    /**
     * Returns the next data value or {@code null} if end of input has been reached.
     *
     * @throws BerDataValueFormatException if the value being read is malformed.
     */
    BerDataValue readDataValue() throws BerDataValueFormatException;
}
