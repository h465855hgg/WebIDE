


package com.android.apksig.internal.apk;

import java.nio.ByteBuffer;


public class SignatureInfo {
    /**
     * Contents of APK Signature Scheme block.
     */
    public final ByteBuffer signatureBlock;

    /**
     * Position of the APK Signing Block in the file.
     */
    public final long apkSigningBlockOffset;

    /**
     * Position of the ZIP Central Directory in the file.
     */
    public final long centralDirOffset;

    /**
     * Position of the ZIP End of Central Directory (EoCD) in the file.
     */
    public final long eocdOffset;

    /**
     * Contents of ZIP End of Central Directory (EoCD) of the file.
     */
    public final ByteBuffer eocd;

    public SignatureInfo(
            ByteBuffer signatureBlock,
            long apkSigningBlockOffset,
            long centralDirOffset,
            long eocdOffset,
            ByteBuffer eocd) {
        this.signatureBlock = signatureBlock;
        this.apkSigningBlockOffset = apkSigningBlockOffset;
        this.centralDirOffset = centralDirOffset;
        this.eocdOffset = eocdOffset;
        this.eocd = eocd;
    }
}
