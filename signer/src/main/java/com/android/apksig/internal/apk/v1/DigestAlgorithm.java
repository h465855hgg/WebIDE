


package com.android.apksig.internal.apk.v1;

import java.util.Comparator;


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
