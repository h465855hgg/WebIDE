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

package com.android.apksig.internal.apk.stamp;

import static com.android.apksig.internal.apk.ApkSigningBlockUtils.encodeAsLengthPrefixedElement;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.encodeAsSequenceOfLengthPrefixedElements;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes;

import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.apk.ApkSigningBlockUtils.SignerConfig;
import com.android.apksig.internal.apk.ContentDigestAlgorithm;
import com.android.apksig.internal.util.Pair;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SourceStamp signer.
 *
 * <p>SourceStamp improves traceability of apps with respect to unauthorized distribution.
 *
 * <p>The stamp is part of the APK that is protected by the signing block.
 *
 * <p>The APK contents hash is signed using the stamp key, and is saved as part of the signing
 * block.
 *
 * <p>V1 of the source stamp allows signing the digest of at most one signature scheme only.
 */
public abstract class V1SourceStampSigner {

    public static final int V1_SOURCE_STAMP_BLOCK_ID = 0x2b09189e;

    /**
     * Hidden constructor to prevent instantiation.
     */
    private V1SourceStampSigner() {
    }

    public static Pair<byte[], Integer> generateSourceStampBlock(
            SignerConfig sourceStampSignerConfig, Map<ContentDigestAlgorithm, byte[]> digestInfo)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        if (sourceStampSignerConfig.certificates.isEmpty()) {
            throw new SignatureException("No certificates configured for signer");
        }

        List<Pair<Integer, byte[]>> digests = new ArrayList<>();
        for (Map.Entry<ContentDigestAlgorithm, byte[]> digest : digestInfo.entrySet()) {
            digests.add(Pair.of(digest.getKey().getId(), digest.getValue()));
        }
        Collections.sort(digests, (o1, o2) -> o1.getFirst().compareTo(o2.getFirst()));

        SourceStampBlock sourceStampBlock = new SourceStampBlock();

        try {
            sourceStampBlock.stampCertificate =
                    sourceStampSignerConfig.certificates.get(0).getEncoded();
        } catch (CertificateEncodingException e) {
            throw new SignatureException(
                    "Retrieving the encoded form of the stamp certificate failed", e);
        }

        byte[] digestBytes =
                encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(digests);
        sourceStampBlock.signedDigests =
                ApkSigningBlockUtils.generateSignaturesOverData(
                        sourceStampSignerConfig, digestBytes);

        // FORMAT:
        // * length-prefixed bytes: X.509 certificate (ASN.1 DER encoded)
        // * length-prefixed sequence of length-prefixed signatures:
        //   * uint32: signature algorithm ID
        //   * length-prefixed bytes: signature of signed data
        byte[] sourceStampSignerBlock =
                encodeAsSequenceOfLengthPrefixedElements(
                        new byte[][]{
                                sourceStampBlock.stampCertificate,
                                encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(
                                        sourceStampBlock.signedDigests),
                        });

        // FORMAT:
        // * length-prefixed stamp block.
        return Pair.of(
                encodeAsLengthPrefixedElement(sourceStampSignerBlock), V1_SOURCE_STAMP_BLOCK_ID);
    }

    private static final class SourceStampBlock {
        public byte[] stampCertificate;
        public List<Pair<Integer, byte[]>> signedDigests;
    }
}
