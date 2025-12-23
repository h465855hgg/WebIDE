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

package com.mcal.apksigner.utils

import org.spongycastle.asn1.ASN1ObjectIdentifier
import org.spongycastle.asn1.x500.style.BCStyle
import org.spongycastle.jce.X509Principal
import java.util.Vector

/**
 * Helper class for dealing with the distinguished name RDNs.
 */
class DistinguishedNameValues : LinkedHashMap<ASN1ObjectIdentifier, String>() {
    init {
        put(BCStyle.C, "")
        put(BCStyle.ST, "")
        put(BCStyle.L, "")
        put(BCStyle.STREET, "")
        put(BCStyle.O, "")
        put(BCStyle.OU, "")
        put(BCStyle.CN, "")
    }

    override fun put(key: ASN1ObjectIdentifier, value: String): String {
        if (containsKey(key)) {
            super.put(key, value) // preserve original ordering
        } else {
            super.put(key, value)
        }
        return value
    }

    fun setCountry(country: String) {
        put(BCStyle.C, country)
    }

    fun setState(state: String) {
        put(BCStyle.ST, state)
    }

    fun setLocality(locality: String) {
        put(BCStyle.L, locality)
    }

    fun setStreet(street: String) {
        put(BCStyle.STREET, street)
    }

    fun setOrganization(organization: String) {
        put(BCStyle.O, organization)
    }

    fun setOrganizationalUnit(organizationalUnit: String) {
        put(BCStyle.OU, organizationalUnit)
    }

    fun setCommonName(commonName: String) {
        put(BCStyle.CN, commonName)
    }

    val principal: X509Principal
        get() {
            val identifiers = Vector<ASN1ObjectIdentifier>()
            val values = Vector<String>()
            for ((key, value) in entries) {
                identifiers.add(key)
                values.add(value)
            }
            return X509Principal(identifiers, values)
        }
}
