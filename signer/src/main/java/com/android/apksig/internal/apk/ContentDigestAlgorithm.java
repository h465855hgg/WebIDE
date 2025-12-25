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

/**
 * APK Signature Scheme v2 content digest algorithm.
 */
public enum ContentDigestAlgorithm {
    /**
     * SHA2-256 over 1 MB chunks.
     */
    CHUNKED_SHA256(1, "SHA-256", 256 / 8),

    /**
     * SHA2-512 over 1 MB chunks.
     */
    CHUNKED_SHA512(2, "SHA-512", 512 / 8),

    /**
     * SHA2-256 over 4 KB chunks for APK verity.
     */
    VERITY_CHUNKED_SHA256(3, "SHA-256", 256 / 8),

    /**
     * Non-chunk SHA2-256.
     */
    SHA256(4, "SHA-256", 256 / 8);

    private final int mId;
    private final String mJcaMessageDigestAlgorithm;
    private final int mChunkDigestOutputSizeBytes;

    private ContentDigestAlgorithm(
            int id, String jcaMessageDigestAlgorithm, int chunkDigestOutputSizeBytes) {
        mId = id;
        mJcaMessageDigestAlgorithm = jcaMessageDigestAlgorithm;
        mChunkDigestOutputSizeBytes = chunkDigestOutputSizeBytes;
    }

    /**
     * Returns the ID of the digest algorithm used on the APK.
     */
    public int getId() {
        return mId;
    }

    /**
     * Returns the {@link java.security.MessageDigest} algorithm used for computing digests of
     * chunks by this content digest algorithm.
     */
    String getJcaMessageDigestAlgorithm() {
        return mJcaMessageDigestAlgorithm;
    }

    /**
     * Returns the size (in bytes) of the digest of a chunk of content.
     */
    int getChunkDigestOutputSizeBytes() {
        return mChunkDigestOutputSizeBytes;
    }
}
