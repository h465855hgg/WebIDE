


package com.android.apksig.internal.jar;

import java.io.IOException;
import java.io.OutputStream;
import java.util.SortedMap;
import java.util.jar.Attributes;


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
