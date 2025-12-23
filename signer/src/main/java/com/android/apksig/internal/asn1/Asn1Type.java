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

package com.android.apksig.internal.asn1;

public enum Asn1Type {
    ANY,
    CHOICE,
    INTEGER,
    OBJECT_IDENTIFIER,
    OCTET_STRING,
    SEQUENCE,
    SEQUENCE_OF,
    SET_OF,
    BIT_STRING,
    UTC_TIME,
    GENERALIZED_TIME,
    BOOLEAN,
    // This type can be used to annotate classes that encapsulate ASN.1 structures that are not
    // classified as a SEQUENCE or SET.
    UNENCODED_CONTAINER
}
