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

package com.android.apksig.internal.zip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ZIP End of Central Directory record.
 */
public class EocdRecord {
    private static final int CD_RECORD_COUNT_ON_DISK_OFFSET = 8;
    private static final int CD_RECORD_COUNT_TOTAL_OFFSET = 10;
    private static final int CD_SIZE_OFFSET = 12;
    private static final int CD_OFFSET_OFFSET = 16;

    public static ByteBuffer createWithModifiedCentralDirectoryInfo(
            ByteBuffer original,
            int centralDirectoryRecordCount,
            long centralDirectorySizeBytes,
            long centralDirectoryOffset) {
        ByteBuffer result = ByteBuffer.allocate(original.remaining());
        result.order(ByteOrder.LITTLE_ENDIAN);
        result.put(original.slice());
        result.flip();
        ZipUtils.setUnsignedInt16(
                result, CD_RECORD_COUNT_ON_DISK_OFFSET, centralDirectoryRecordCount);
        ZipUtils.setUnsignedInt16(
                result, CD_RECORD_COUNT_TOTAL_OFFSET, centralDirectoryRecordCount);
        ZipUtils.setUnsignedInt32(result, CD_SIZE_OFFSET, centralDirectorySizeBytes);
        ZipUtils.setUnsignedInt32(result, CD_OFFSET_OFFSET, centralDirectoryOffset);
        return result;
    }
}
