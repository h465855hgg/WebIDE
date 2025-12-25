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

package com.android.apksig.internal.jar;

import java.io.IOException;
import java.io.OutputStream;
import java.util.SortedMap;
import java.util.jar.Attributes;

/**
 * Producer of JAR signature file ({@code *.SF}).
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#JAR_Manifest">JAR Manifest format</a>
 */
public abstract class SignatureFileWriter {
    private SignatureFileWriter() {
    }

    public static void writeMainSection(OutputStream out, Attributes attributes)
            throws IOException {

        // Main section must start with the Signature-Version attribute.
        // See https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File.
        String signatureVersion = attributes.getValue(Attributes.Name.SIGNATURE_VERSION);
        if (signatureVersion == null) {
            throw new IllegalArgumentException(
                    "Mandatory " + Attributes.Name.SIGNATURE_VERSION + " attribute missing");
        }
        ManifestWriter.writeAttribute(out, Attributes.Name.SIGNATURE_VERSION, signatureVersion);

        if (attributes.size() > 1) {
            SortedMap<String, String> namedAttributes =
                    ManifestWriter.getAttributesSortedByName(attributes);
            namedAttributes.remove(Attributes.Name.SIGNATURE_VERSION.toString());
            ManifestWriter.writeAttributes(out, namedAttributes);
        }
        writeSectionDelimiter(out);
    }

    public static void writeIndividualSection(OutputStream out, String name, Attributes attributes)
            throws IOException {
        ManifestWriter.writeIndividualSection(out, name, attributes);
    }

    public static void writeSectionDelimiter(OutputStream out) throws IOException {
        ManifestWriter.writeSectionDelimiter(out);
    }
}
