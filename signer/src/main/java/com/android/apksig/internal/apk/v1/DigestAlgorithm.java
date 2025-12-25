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

package com.android.apksig.internal.apk.v1;

import java.util.Comparator;

/**
 * Digest algorithm used with JAR signing (aka v1 signing scheme).
 */
public enum DigestAlgorithm {
    /**
     * SHA-1
     */
    SHA1("SHA-1"),

    /**
     * SHA2-256
     */
    SHA256("SHA-256");

    public static Comparator<DigestAlgorithm> BY_STRENGTH_COMPARATOR = new StrengthComparator();
    private final String mJcaMessageDigestAlgorithm;

    private DigestAlgorithm(String jcaMessageDigestAlgoritm) {
        mJcaMessageDigestAlgorithm = jcaMessageDigestAlgoritm;
    }

    /**
     * Returns the {@link java.security.MessageDigest} algorithm represented by this digest
     * algorithm.
     */
    String getJcaMessageDigestAlgorithm() {
        return mJcaMessageDigestAlgorithm;
    }

    private static class StrengthComparator implements Comparator<DigestAlgorithm> {
        @Override
        public int compare(DigestAlgorithm a1, DigestAlgorithm a2) {
            switch (a1) {
                case SHA1:
                    switch (a2) {
                        case SHA1:
                            return 0;
                        case SHA256:
                            return -1;
                    }
                    throw new RuntimeException("Unsupported algorithm: " + a2);

                case SHA256:
                    switch (a2) {
                        case SHA1:
                            return 1;
                        case SHA256:
                            return 0;
                    }
                    throw new RuntimeException("Unsupported algorithm: " + a2);

                default:
                    throw new RuntimeException("Unsupported algorithm: " + a1);
            }
        }
    }
}
