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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

/**
 * {@link X509Certificate} which delegates all method invocations to the provided delegate
 * {@code X509Certificate}.
 */
public class DelegatingX509Certificate extends X509Certificate {
    private static final long serialVersionUID = 1L;

    private final X509Certificate mDelegate;

    public DelegatingX509Certificate(X509Certificate delegate) {
        this.mDelegate = delegate;
    }

    @Override
    public Set<String> getCriticalExtensionOIDs() {
        return mDelegate.getCriticalExtensionOIDs();
    }

    @Override
    public byte[] getExtensionValue(String oid) {
        return mDelegate.getExtensionValue(oid);
    }

    @Override
    public Set<String> getNonCriticalExtensionOIDs() {
        return mDelegate.getNonCriticalExtensionOIDs();
    }

    @Override
    public boolean hasUnsupportedCriticalExtension() {
        return mDelegate.hasUnsupportedCriticalExtension();
    }

    @Override
    public void checkValidity()
            throws CertificateExpiredException, CertificateNotYetValidException {
        mDelegate.checkValidity();
    }

    @Override
    public void checkValidity(Date date)
            throws CertificateExpiredException, CertificateNotYetValidException {
        mDelegate.checkValidity(date);
    }

    @Override
    public int getVersion() {
        return mDelegate.getVersion();
    }

    @Override
    public BigInteger getSerialNumber() {
        return mDelegate.getSerialNumber();
    }

    @Override
    public Principal getIssuerDN() {
        return mDelegate.getIssuerDN();
    }

    @Override
    public Principal getSubjectDN() {
        return mDelegate.getSubjectDN();
    }

    @Override
    public Date getNotBefore() {
        return mDelegate.getNotBefore();
    }

    @Override
    public Date getNotAfter() {
        return mDelegate.getNotAfter();
    }

    @Override
    public byte[] getTBSCertificate() throws CertificateEncodingException {
        return mDelegate.getTBSCertificate();
    }

    @Override
    public byte[] getSignature() {
        return mDelegate.getSignature();
    }

    @Override
    public String getSigAlgName() {
        return mDelegate.getSigAlgName();
    }

    @Override
    public String getSigAlgOID() {
        return mDelegate.getSigAlgOID();
    }

    @Override
    public byte[] getSigAlgParams() {
        return mDelegate.getSigAlgParams();
    }

    @Override
    public boolean[] getIssuerUniqueID() {
        return mDelegate.getIssuerUniqueID();
    }

    @Override
    public boolean[] getSubjectUniqueID() {
        return mDelegate.getSubjectUniqueID();
    }

    @Override
    public boolean[] getKeyUsage() {
        return mDelegate.getKeyUsage();
    }

    @Override
    public int getBasicConstraints() {
        return mDelegate.getBasicConstraints();
    }

    @Override
    public byte[] getEncoded() throws CertificateEncodingException {
        return mDelegate.getEncoded();
    }

    @Override
    public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
        mDelegate.verify(key);
    }

    @Override
    public void verify(PublicKey key, String sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchProviderException, SignatureException {
        mDelegate.verify(key, sigProvider);
    }

    @Override
    public String toString() {
        return mDelegate.toString();
    }

    @Override
    public PublicKey getPublicKey() {
        return mDelegate.getPublicKey();
    }

    @Override
    public X500Principal getIssuerX500Principal() {
        return mDelegate.getIssuerX500Principal();
    }

    @Override
    public X500Principal getSubjectX500Principal() {
        return mDelegate.getSubjectX500Principal();
    }

    @Override
    public List<String> getExtendedKeyUsage() throws CertificateParsingException {
        return mDelegate.getExtendedKeyUsage();
    }

    @Override
    public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
        return mDelegate.getSubjectAlternativeNames();
    }

    @Override
    public Collection<List<?>> getIssuerAlternativeNames() throws CertificateParsingException {
        return mDelegate.getIssuerAlternativeNames();
    }

    @Override
    public void verify(PublicKey key, Provider sigProvider) throws CertificateException,
            NoSuchAlgorithmException, InvalidKeyException, SignatureException {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            mDelegate.verify(key, sigProvider);
//        } else {
//            throw new UnsupportedOperationException("Not supported before API 24");
//        }
    }
}
