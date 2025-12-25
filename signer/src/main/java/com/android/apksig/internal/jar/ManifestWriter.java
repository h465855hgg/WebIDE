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

package com.android.apksig.internal.jar;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.Attributes;

/**
 * Producer of {@code META-INF/MANIFEST.MF} file.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#JAR_Manifest">JAR Manifest format</a>
 */
public abstract class ManifestWriter {

    private static final byte[] CRLF = new byte[]{'\r', '\n'};
    private static final int MAX_LINE_LENGTH = 70;

    private ManifestWriter() {
    }

    public static void writeMainSection(OutputStream out, Attributes attributes)
            throws IOException {

        // Main section must start with the Manifest-Version attribute.
        // See https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File.
        String manifestVersion = attributes.getValue(Attributes.Name.MANIFEST_VERSION);
        if (manifestVersion == null) {
            throw new IllegalArgumentException(
                    "Mandatory " + Attributes.Name.MANIFEST_VERSION + " attribute missing");
        }
        writeAttribute(out, Attributes.Name.MANIFEST_VERSION, manifestVersion);

        if (attributes.size() > 1) {
            SortedMap<String, String> namedAttributes = getAttributesSortedByName(attributes);
            namedAttributes.remove(Attributes.Name.MANIFEST_VERSION.toString());
            writeAttributes(out, namedAttributes);
        }
        writeSectionDelimiter(out);
    }

    public static void writeIndividualSection(OutputStream out, String name, Attributes attributes)
            throws IOException {
        writeAttribute(out, "Name", name);

        if (!attributes.isEmpty()) {
            writeAttributes(out, getAttributesSortedByName(attributes));
        }
        writeSectionDelimiter(out);
    }

    static void writeSectionDelimiter(OutputStream out) throws IOException {
        out.write(CRLF);
    }

    static void writeAttribute(OutputStream out, Attributes.Name name, String value)
            throws IOException {
        writeAttribute(out, name.toString(), value);
    }

    private static void writeAttribute(OutputStream out, String name, String value)
            throws IOException {
        writeLine(out, name + ": " + value);
    }

    private static void writeLine(OutputStream out, String line) throws IOException {
        byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
        int offset = 0;
        int remaining = lineBytes.length;
        boolean firstLine = true;
        while (remaining > 0) {
            int chunkLength;
            if (firstLine) {
                // First line
                chunkLength = Math.min(remaining, MAX_LINE_LENGTH);
            } else {
                // Continuation line
                out.write(CRLF);
                out.write(' ');
                chunkLength = Math.min(remaining, MAX_LINE_LENGTH - 1);
            }
            out.write(lineBytes, offset, chunkLength);
            offset += chunkLength;
            remaining -= chunkLength;
            firstLine = false;
        }
        out.write(CRLF);
    }

    static SortedMap<String, String> getAttributesSortedByName(Attributes attributes) {
        Set<Map.Entry<Object, Object>> attributesEntries = attributes.entrySet();
        SortedMap<String, String> namedAttributes = new TreeMap<String, String>();
        for (Map.Entry<Object, Object> attribute : attributesEntries) {
            String attrName = attribute.getKey().toString();
            String attrValue = attribute.getValue().toString();
            namedAttributes.put(attrName, attrValue);
        }
        return namedAttributes;
    }

    static void writeAttributes(
            OutputStream out, SortedMap<String, String> attributesSortedByName) throws IOException {
        for (Map.Entry<String, String> attribute : attributesSortedByName.entrySet()) {
            String attrName = attribute.getKey();
            String attrValue = attribute.getValue();
            writeAttribute(out, attrName, attrValue);
        }
    }
}
