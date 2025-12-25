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

import java.nio.ByteBuffer;

/**
 * APK Signature Scheme block and additional information relevant to verifying the signatures
 * contained in the block against the file.
 */
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
