


package com.android.apksig.internal.zip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


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
