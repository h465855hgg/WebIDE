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
package com.android.apksig;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Hints {
    /**
     * Name of hint pattern asset file in APK.
     */
    public static final String PIN_HINT_ASSET_ZIP_ENTRY_NAME = "assets/com.android.hints.pins.txt";

    /**
     * Name of hint byte range data file in APK.  Keep in sync with PinnerService.java.
     */
    public static final String PIN_BYTE_RANGE_ZIP_ENTRY_NAME = "pinlist.meta";

    private static int clampToInt(long value) {
        return (int) Math.max(0, Math.min(value, Integer.MAX_VALUE));
    }

    /**
     * Create a blob of bytes that PinnerService understands as a
     * sequence of byte ranges to pin.
     */
    public static byte[] encodeByteRangeList(List<ByteRange> pinByteRanges) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(pinByteRanges.size() * 8);
        DataOutputStream out = new DataOutputStream(bos);
        try {
            for (ByteRange pinByteRange : pinByteRanges) {
                out.writeInt(clampToInt(pinByteRange.start));
                out.writeInt(clampToInt(pinByteRange.end - pinByteRange.start));
            }
        } catch (IOException ex) {
            throw new AssertionError("impossible", ex);
        }
        return bos.toByteArray();
    }

    public static ArrayList<PatternWithRange> parsePinPatterns(byte[] patternBlob) {
        ArrayList<PatternWithRange> pinPatterns = new ArrayList<>();
        try {
            for (String rawLine : new String(patternBlob, "UTF-8").split("\n")) {
                String line = rawLine.replaceFirst("#.*", "");  // # starts a comment
                String[] fields = line.split(" ");
                if (fields.length == 1) {
                    pinPatterns.add(new PatternWithRange(fields[0]));
                } else if (fields.length == 3) {
                    long start = Long.parseLong(fields[1]);
                    long end = Long.parseLong(fields[2]);
                    pinPatterns.add(new PatternWithRange(fields[0], start, end - start));
                } else {
                    throw new AssertionError("bad pin pattern line " + line);
                }
            }
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException("UTF-8 must be supported", ex);
        }
        return pinPatterns;
    }

    public static final class ByteRange {
        final long start;
        final long end;

        public ByteRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public static final class PatternWithRange {
        final Pattern pattern;
        final long offset;
        final long size;

        public PatternWithRange(String pattern) {
            this.pattern = Pattern.compile(pattern);
            this.offset = 0;
            this.size = Long.MAX_VALUE;
        }

        public PatternWithRange(String pattern, long offset, long size) {
            this.pattern = Pattern.compile(pattern);
            this.offset = offset;
            this.size = size;
        }

        public Matcher matcher(CharSequence input) {
            return this.pattern.matcher(input);
        }

        public ByteRange ClampToAbsoluteByteRange(ByteRange rangeIn) {
            if (rangeIn.end - rangeIn.start < this.offset) {
                return null;
            }
            long rangeOutStart = rangeIn.start + this.offset;
            long rangeOutSize = Math.min(rangeIn.end - rangeOutStart,
                    this.size);
            return new ByteRange(rangeOutStart,
                    rangeOutStart + rangeOutSize);
        }
    }
}
