/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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

package com.android.apksig.internal.apk;

import com.android.apksig.internal.util.AndroidSdkVersion;
import com.android.apksig.internal.util.Pair;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

/**
 * APK Signing Block signature algorithm.
 */
public enum SignatureAlgorithm {
    // TODO reserve the 0x0000 ID to mean null
    /**
     * RSASSA-PSS with SHA2-256 digest, SHA2-256 MGF1, 32 bytes of salt, trailer: 0xbc, content
     * digested using SHA2-256 in 1 MB chunks.
     */
    RSA_PSS_WITH_SHA256(
            0x0101,
            ContentDigestAlgorithm.CHUNKED_SHA256,
            "RSA",
            Pair.of("SHA256withRSA/PSS",
                    new PSSParameterSpec(
                            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 256 / 8, 1)),
            AndroidSdkVersion.N),

    /**
     * RSASSA-PSS with SHA2-512 digest, SHA2-512 MGF1, 64 bytes of salt, trailer: 0xbc, content
     * digested using SHA2-512 in 1 MB chunks.
     */
    RSA_PSS_WITH_SHA512(
            0x0102,
            ContentDigestAlgorithm.CHUNKED_SHA512,
            "RSA",
            Pair.of(
                    "SHA512withRSA/PSS",
                    new PSSParameterSpec(
                            "SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 512 / 8, 1)),
            AndroidSdkVersion.N),

    /**
     * RSASSA-PKCS1-v1_5 with SHA2-256 digest, content digested using SHA2-256 in 1 MB chunks.
     */
    RSA_PKCS1_V1_5_WITH_SHA256(
            0x0103,
            ContentDigestAlgorithm.CHUNKED_SHA256,
            "RSA",
            Pair.of("SHA256withRSA", null),
            AndroidSdkVersion.N),

    /**
     * RSASSA-PKCS1-v1_5 with SHA2-512 digest, content digested using SHA2-512 in 1 MB chunks.
     */
    RSA_PKCS1_V1_5_WITH_SHA512(
            0x0104,
            ContentDigestAlgorithm.CHUNKED_SHA512,
            "RSA",
            Pair.of("SHA512withRSA", null),
            AndroidSdkVersion.N),

    /**
     * ECDSA with SHA2-256 digest, content digested using SHA2-256 in 1 MB chunks.
     */
    ECDSA_WITH_SHA256(
            0x0201,
            ContentDigestAlgorithm.CHUNKED_SHA256,
            "EC",
            Pair.of("SHA256withECDSA", null),
            AndroidSdkVersion.N),

    /**
     * ECDSA with SHA2-512 digest, content digested using SHA2-512 in 1 MB chunks.
     */
    ECDSA_WITH_SHA512(
            0x0202,
            ContentDigestAlgorithm.CHUNKED_SHA512,
            "EC",
            Pair.of("SHA512withECDSA", null),
            AndroidSdkVersion.N),

    /**
     * DSA with SHA2-256 digest, content digested using SHA2-256 in 1 MB chunks.
     */
    DSA_WITH_SHA256(
            0x0301,
            ContentDigestAlgorithm.CHUNKED_SHA256,
            "DSA",
            Pair.of("SHA256withDSA", null),
            AndroidSdkVersion.N),

    /**
     * RSASSA-PKCS1-v1_5 with SHA2-256 digest, content digested using SHA2-256 in 4 KB chunks, in
     * the same way fsverity operates. This digest and the content length (before digestion, 8 bytes
     * in little endian) construct the final digest.
     */
    VERITY_RSA_PKCS1_V1_5_WITH_SHA256(
            0x0421,
            ContentDigestAlgorithm.VERITY_CHUNKED_SHA256,
            "RSA",
            Pair.of("SHA256withRSA", null),
            AndroidSdkVersion.P),

    /**
     * ECDSA with SHA2-256 digest, content digested using SHA2-256 in 4 KB chunks, in the same way
     * fsverity operates. This digest and the content length (before digestion, 8 bytes in little
     * endian) construct the final digest.
     */
    VERITY_ECDSA_WITH_SHA256(
            0x0423,
            ContentDigestAlgorithm.VERITY_CHUNKED_SHA256,
            "EC",
            Pair.of("SHA256withECDSA", null),
            AndroidSdkVersion.P),

    /**
     * DSA with SHA2-256 digest, content digested using SHA2-256 in 4 KB chunks, in the same way
     * fsverity operates. This digest and the content length (before digestion, 8 bytes in little
     * endian) construct the final digest.
     */
    VERITY_DSA_WITH_SHA256(
            0x0425,
            ContentDigestAlgorithm.VERITY_CHUNKED_SHA256,
            "DSA",
            Pair.of("SHA256withDSA", null),
            AndroidSdkVersion.P);

    private final int mId;
    private final String mJcaKeyAlgorithm;
    private final ContentDigestAlgorithm mContentDigestAlgorithm;
    private final Pair<String, ? extends AlgorithmParameterSpec> mJcaSignatureAlgAndParams;
    private final int mMinSdkVersion;

    SignatureAlgorithm(int id,
                       ContentDigestAlgorithm contentDigestAlgorithm,
                       String jcaKeyAlgorithm,
                       Pair<String, ? extends AlgorithmParameterSpec> jcaSignatureAlgAndParams,
                       int minSdkVersion) {
        mId = id;
        mContentDigestAlgorithm = contentDigestAlgorithm;
        mJcaKeyAlgorithm = jcaKeyAlgorithm;
        mJcaSignatureAlgAndParams = jcaSignatureAlgAndParams;
        mMinSdkVersion = minSdkVersion;
    }

    public static SignatureAlgorithm findById(int id) {
        for (SignatureAlgorithm alg : SignatureAlgorithm.values()) {
            if (alg.getId() == id) {
                return alg;
            }
        }

        return null;
    }

    /**
     * Returns the ID of this signature algorithm as used in APK Signature Scheme v2 wire format.
     */
    public int getId() {
        return mId;
    }

    /**
     * Returns the content digest algorithm associated with this signature algorithm.
     */
    public ContentDigestAlgorithm getContentDigestAlgorithm() {
        return mContentDigestAlgorithm;
    }

    /**
     * Returns the JCA {@link java.security.Key} algorithm used by this signature scheme.
     */
    public String getJcaKeyAlgorithm() {
        return mJcaKeyAlgorithm;
    }

    /**
     * Returns the {@link java.security.Signature} algorithm and the {@link AlgorithmParameterSpec}
     * (or null if not needed) to parameterize the {@code Signature}.
     */
    public Pair<String, ? extends AlgorithmParameterSpec> getJcaSignatureAlgorithmAndParams() {
        return mJcaSignatureAlgAndParams;
    }

    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }
}
