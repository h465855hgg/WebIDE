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

package com.android.apksig.internal.util;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * {@link X509Certificate} whose {@link #getEncoded()} returns the data provided at construction
 * time.
 */
public class GuaranteedEncodedFormX509Certificate extends DelegatingX509Certificate {
    private static final long serialVersionUID = 1L;

    private final byte[] mEncodedForm;
    private int mHash = -1;

    public GuaranteedEncodedFormX509Certificate(X509Certificate wrapped, byte[] encodedForm) {
        super(wrapped);
        this.mEncodedForm = (encodedForm != null) ? encodedForm.clone() : null;
    }

    @Override
    public byte[] getEncoded() throws CertificateEncodingException {
        return (mEncodedForm != null) ? mEncodedForm.clone() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof X509Certificate)) return false;

        try {
            byte[] a = this.getEncoded();
            byte[] b = ((X509Certificate) o).getEncoded();
            return Arrays.equals(a, b);
        } catch (CertificateEncodingException e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        if (mHash == -1) {
            try {
                mHash = Arrays.hashCode(this.getEncoded());
            } catch (CertificateEncodingException e) {
                mHash = 0;
            }
        }
        return mHash;
    }
}
