


package com.android.apksig.internal.util;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;


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
