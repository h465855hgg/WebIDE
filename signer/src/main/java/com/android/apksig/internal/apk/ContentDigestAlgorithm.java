


package com.android.apksig.internal.apk;


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
