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

import com.android.apksig.internal.asn1.Asn1TagClass;
import com.android.apksig.internal.asn1.Asn1Type;

/**
 * ASN.1 Basic Encoding Rules (BER) constants and helper methods. See {@code X.690}.
 */
public abstract class BerEncoding {
    /**
     * Constructed vs primitive flag in the first identifier byte.
     */
    public static final int ID_FLAG_CONSTRUCTED_ENCODING = 1 << 5;
    /**
     * Tag class: UNIVERSAL
     */
    public static final int TAG_CLASS_UNIVERSAL = 0;
    /**
     * Tag class: APPLICATION
     */
    public static final int TAG_CLASS_APPLICATION = 1;
    /**
     * Tag class: CONTEXT SPECIFIC
     */
    public static final int TAG_CLASS_CONTEXT_SPECIFIC = 2;
    /**
     * Tag class: PRIVATE
     */
    public static final int TAG_CLASS_PRIVATE = 3;
    /**
     * Tag number: BOOLEAN
     */
    public static final int TAG_NUMBER_BOOLEAN = 0x1;
    /**
     * Tag number: INTEGER
     */
    public static final int TAG_NUMBER_INTEGER = 0x2;
    /**
     * Tag number: BIT STRING
     */
    public static final int TAG_NUMBER_BIT_STRING = 0x3;
    /**
     * Tag number: OCTET STRING
     */
    public static final int TAG_NUMBER_OCTET_STRING = 0x4;
    /**
     * Tag number: NULL
     */
    public static final int TAG_NUMBER_NULL = 0x05;
    /**
     * Tag number: OBJECT IDENTIFIER
     */
    public static final int TAG_NUMBER_OBJECT_IDENTIFIER = 0x6;
    /**
     * Tag number: SEQUENCE
     */
    public static final int TAG_NUMBER_SEQUENCE = 0x10;
    /**
     * Tag number: SET
     */
    public static final int TAG_NUMBER_SET = 0x11;
    /**
     * Tag number: UTC_TIME
     */
    public final static int TAG_NUMBER_UTC_TIME = 0x17;
    /**
     * Tag number: GENERALIZED_TIME
     */
    public final static int TAG_NUMBER_GENERALIZED_TIME = 0x18;

    private BerEncoding() {
    }

    public static int getTagNumber(Asn1Type dataType) {
        switch (dataType) {
            case INTEGER:
                return TAG_NUMBER_INTEGER;
            case OBJECT_IDENTIFIER:
                return TAG_NUMBER_OBJECT_IDENTIFIER;
            case OCTET_STRING:
                return TAG_NUMBER_OCTET_STRING;
            case BIT_STRING:
                return TAG_NUMBER_BIT_STRING;
            case SET_OF:
                return TAG_NUMBER_SET;
            case SEQUENCE:
            case SEQUENCE_OF:
                return TAG_NUMBER_SEQUENCE;
            case UTC_TIME:
                return TAG_NUMBER_UTC_TIME;
            case GENERALIZED_TIME:
                return TAG_NUMBER_GENERALIZED_TIME;
            case BOOLEAN:
                return TAG_NUMBER_BOOLEAN;
            default:
                throw new IllegalArgumentException("Unsupported data type: " + dataType);
        }
    }

    public static int getTagClass(Asn1TagClass tagClass) {
        switch (tagClass) {
            case APPLICATION:
                return TAG_CLASS_APPLICATION;
            case CONTEXT_SPECIFIC:
                return TAG_CLASS_CONTEXT_SPECIFIC;
            case PRIVATE:
                return TAG_CLASS_PRIVATE;
            case UNIVERSAL:
                return TAG_CLASS_UNIVERSAL;
            default:
                throw new IllegalArgumentException("Unsupported tag class: " + tagClass);
        }
    }

    public static String tagClassToString(int typeClass) {
        switch (typeClass) {
            case TAG_CLASS_APPLICATION:
                return "APPLICATION";
            case TAG_CLASS_CONTEXT_SPECIFIC:
                return "";
            case TAG_CLASS_PRIVATE:
                return "PRIVATE";
            case TAG_CLASS_UNIVERSAL:
                return "UNIVERSAL";
            default:
                throw new IllegalArgumentException("Unsupported type class: " + typeClass);
        }
    }

    public static String tagClassAndNumberToString(int tagClass, int tagNumber) {
        String classString = tagClassToString(tagClass);
        String numberString = tagNumberToString(tagNumber);
        return classString.isEmpty() ? numberString : classString + " " + numberString;
    }


    public static String tagNumberToString(int tagNumber) {
        switch (tagNumber) {
            case TAG_NUMBER_INTEGER:
                return "INTEGER";
            case TAG_NUMBER_OCTET_STRING:
                return "OCTET STRING";
            case TAG_NUMBER_BIT_STRING:
                return "BIT STRING";
            case TAG_NUMBER_NULL:
                return "NULL";
            case TAG_NUMBER_OBJECT_IDENTIFIER:
                return "OBJECT IDENTIFIER";
            case TAG_NUMBER_SEQUENCE:
                return "SEQUENCE";
            case TAG_NUMBER_SET:
                return "SET";
            case TAG_NUMBER_BOOLEAN:
                return "BOOLEAN";
            case TAG_NUMBER_GENERALIZED_TIME:
                return "GENERALIZED TIME";
            case TAG_NUMBER_UTC_TIME:
                return "UTC TIME";
            default:
                return "0x" + Integer.toHexString(tagNumber);
        }
    }

    /**
     * Returns {@code true} if the provided first identifier byte indicates that the data value uses
     * constructed encoding for its contents, or {@code false} if the data value uses primitive
     * encoding for its contents.
     */
    public static boolean isConstructed(byte firstIdentifierByte) {
        return (firstIdentifierByte & ID_FLAG_CONSTRUCTED_ENCODING) != 0;
    }

    /**
     * Returns the tag class encoded in the provided first identifier byte. See {@code TAG_CLASS}
     * constants.
     */
    public static int getTagClass(byte firstIdentifierByte) {
        return (firstIdentifierByte & 0xff) >> 6;
    }

    public static byte setTagClass(byte firstIdentifierByte, int tagClass) {
        return (byte) ((firstIdentifierByte & 0x3f) | (tagClass << 6));
    }

    /**
     * Returns the tag number encoded in the provided first identifier byte. See {@code TAG_NUMBER}
     * constants.
     */
    public static int getTagNumber(byte firstIdentifierByte) {
        return firstIdentifierByte & 0x1f;
    }

    public static byte setTagNumber(byte firstIdentifierByte, int tagNumber) {
        return (byte) ((firstIdentifierByte & ~0x1f) | tagNumber);
    }
}
