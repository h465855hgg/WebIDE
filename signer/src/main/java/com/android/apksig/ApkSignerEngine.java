


package com.android.apksig;

import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.util.DataSink;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.RunnablesExecutor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;
import java.util.Set;


public interface ApkSignerEngine extends Closeable {

    default void setExecutor(RunnablesExecutor executor) {
        throw new UnsupportedOperationException("setExecutor method is not implemented");
    }

    /**
     * Initializes the signer engine with the data already present in the apk (if any). There
     * might already be data that can be reused if the entries has not been changed.
     *
     * @param manifestBytes
     * @param entryNames
     * @return set of entry names which were processed by the engine during the initialization, a
     * subset of entryNames
     */
    default Set<String> initWith(byte[] manifestBytes, Set<String> entryNames) {
        throw new UnsupportedOperationException("initWith method is not implemented");
    }

    /**
     * Indicates to this engine that the input APK contains the provided APK Signing Block. The
     * block may contain signatures of the input APK, such as APK Signature Scheme v2 signatures.
     *
     * @param apkSigningBlock APK signing block of the input APK. The provided data source is
     *                        guaranteed to not be used by the engine after this method terminates.
     * @throws IOException           if an I/O error occurs while reading the APK Signing Block
     * @throws ApkFormatException    if the APK Signing Block is malformed
     * @throws IllegalStateException if this engine is closed
     */
    void inputApkSigningBlock(DataSource apkSigningBlock)
            throws IOException, ApkFormatException, IllegalStateException;

    /**
     * Indicates to this engine that the specified JAR entry was encountered in the input APK.
     *
     * <p>When an input entry is updated/changed, it's OK to not invoke
     * {@link #inputJarEntryRemoved(String)} before invoking this method.
     *
     * @return instructions about how to proceed with this entry
     * @throws IllegalStateException if this engine is closed
     */
    InputJarEntryInstructions inputJarEntry(String entryName) throws IllegalStateException;

    /**
     * Indicates to this engine that the specified JAR entry was output.
     *
     * <p>It is unnecessary to invoke this method for entries added to output by this engine (e.g.,
     * requested by {@link #outputJarEntries()}) provided the entries were output with exactly the
     * data requested by the engine.
     *
     * <p>When an already output entry is updated/changed, it's OK to not invoke
     * {@link #outputJarEntryRemoved(String)} before invoking this method.
     *
     * @return request to inspect the entry or {@code null} if the engine does not need to inspect
     * the entry. The request must be fulfilled before {@link #outputJarEntries()} is
     * invoked.
     * @throws IllegalStateException if this engine is closed
     */
    InspectJarEntryRequest outputJarEntry(String entryName) throws IllegalStateException;

    /**
     * Indicates to this engine that the specified JAR entry was removed from the input. It's safe
     * to invoke this for entries for which {@link #inputJarEntry(String)} hasn't been invoked.
     *
     * @return output policy of this JAR entry. The policy indicates how this input entry affects
     * the output APK. The client of this engine should use this information to determine
     * how the removal of this input APK's JAR entry affects the output APK.
     * @throws IllegalStateException if this engine is closed
     */
    InputJarEntryInstructions.OutputPolicy inputJarEntryRemoved(String entryName)
            throws IllegalStateException;

    /**
     * Indicates to this engine that the specified JAR entry was removed from the output. It's safe
     * to invoke this for entries for which {@link #outputJarEntry(String)} hasn't been invoked.
     *
     * @throws IllegalStateException if this engine is closed
     */
    void outputJarEntryRemoved(String entryName) throws IllegalStateException;

    /**
     * Indicates to this engine that all JAR entries have been output.
     *
     * @return request to add JAR signature to the output or {@code null} if there is no need to add
     * a JAR signature. The request will contain additional JAR entries to be output. The
     * request must be fulfilled before
     * {@link #outputZipSections2(DataSource, DataSource, DataSource)} is invoked.
     * @throws ApkFormatException       if the APK is malformed in a way which is preventing this engine
     *                                  from producing a valid signature. For example, if the engine uses the provided
     *                                  {@code META-INF/MANIFEST.MF} as a template and the file is malformed.
     * @throws NoSuchAlgorithmException if a signature could not be generated because a required
     *                                  cryptographic algorithm implementation is missing
     * @throws InvalidKeyException      if a signature could not be generated because a signing key is
     *                                  not suitable for generating the signature
     * @throws SignatureException       if an error occurred while generating a signature
     * @throws IllegalStateException    if there are unfulfilled requests, such as to inspect some JAR
     *                                  entries, or if the engine is closed
     */
    OutputJarSignatureRequest outputJarEntries()
            throws ApkFormatException, NoSuchAlgorithmException, InvalidKeyException,
            SignatureException, IllegalStateException;

    /**
     * Indicates to this engine that the ZIP sections comprising the output APK have been output.
     *
     * <p>The provided data sources are guaranteed to not be used by the engine after this method
     * terminates.
     *
     * @param zipEntries          the section of ZIP archive containing Local File Header records and data of
     *                            the ZIP entries. In a well-formed archive, this section starts at the start of the
     *                            archive and extends all the way to the ZIP Central Directory.
     * @param zipCentralDirectory ZIP Central Directory section
     * @param zipEocd             ZIP End of Central Directory (EoCD) record
     * @return request to add an APK Signing Block to the output or {@code null} if the output must
     * not contain an APK Signing Block. The request must be fulfilled before
     * {@link #outputDone()} is invoked.
     * @throws IOException              if an I/O error occurs while reading the provided ZIP sections
     * @throws ApkFormatException       if the provided APK is malformed in a way which prevents this
     *                                  engine from producing a valid signature. For example, if the APK Signing Block
     *                                  provided to the engine is malformed.
     * @throws NoSuchAlgorithmException if a signature could not be generated because a required
     *                                  cryptographic algorithm implementation is missing
     * @throws InvalidKeyException      if a signature could not be generated because a signing key is
     *                                  not suitable for generating the signature
     * @throws SignatureException       if an error occurred while generating a signature
     * @throws IllegalStateException    if there are unfulfilled requests, such as to inspect some JAR
     *                                  entries or to output JAR signature, or if the engine is closed
     * @deprecated This is now superseded by {@link #outputZipSections2(DataSource, DataSource,
     * DataSource)}.
     */
    @Deprecated
    OutputApkSigningBlockRequest outputZipSections(
            DataSource zipEntries,
            DataSource zipCentralDirectory,
            DataSource zipEocd)
            throws IOException, ApkFormatException, NoSuchAlgorithmException,
            InvalidKeyException, SignatureException, IllegalStateException;

    /**
     * Indicates to this engine that the ZIP sections comprising the output APK have been output.
     *
     * <p>The provided data sources are guaranteed to not be used by the engine after this method
     * terminates.
     *
     * @param zipEntries          the section of ZIP archive containing Local File Header records and data of
     *                            the ZIP entries. In a well-formed archive, this section starts at the start of the
     *                            archive and extends all the way to the ZIP Central Directory.
     * @param zipCentralDirectory ZIP Central Directory section
     * @param zipEocd             ZIP End of Central Directory (EoCD) record
     * @return request to add an APK Signing Block to the output or {@code null} if the output must
     * not contain an APK Signing Block. The request must be fulfilled before
     * {@link #outputDone()} is invoked.
     * @throws IOException              if an I/O error occurs while reading the provided ZIP sections
     * @throws ApkFormatException       if the provided APK is malformed in a way which prevents this
     *                                  engine from producing a valid signature. For example, if the APK Signing Block
     *                                  provided to the engine is malformed.
     * @throws NoSuchAlgorithmException if a signature could not be generated because a required
     *                                  cryptographic algorithm implementation is missing
     * @throws InvalidKeyException      if a signature could not be generated because a signing key is
     *                                  not suitable for generating the signature
     * @throws SignatureException       if an error occurred while generating a signature
     * @throws IllegalStateException    if there are unfulfilled requests, such as to inspect some JAR
     *                                  entries or to output JAR signature, or if the engine is closed
     */
    OutputApkSigningBlockRequest2 outputZipSections2(
            DataSource zipEntries,
            DataSource zipCentralDirectory,
            DataSource zipEocd)
            throws IOException, ApkFormatException, NoSuchAlgorithmException,
            InvalidKeyException, SignatureException, IllegalStateException;

    /**
     * Indicates to this engine that the signed APK was output.
     *
     * <p>This does not change the output APK. The method helps the client confirm that the current
     * output is signed.
     *
     * @throws IllegalStateException if there are unfulfilled requests, such as to inspect some JAR
     *                               entries or to output signatures, or if the engine is closed
     */
    void outputDone() throws IllegalStateException;

    /**
     * Generates a V4 signature proto and write to output file.
     *
     * @param data           Input data to calculate a verity hash tree and hash root
     * @param outputFile     To store the serialized V4 Signature.
     * @param ignoreFailures Whether any failures will be silently ignored.
     * @throws InvalidKeyException      if a signature could not be generated because a signing key is
     *                                  not suitable for generating the signature
     * @throws NoSuchAlgorithmException if a signature could not be generated because a required
     *                                  cryptographic algorithm implementation is missing
     * @throws SignatureException       if an error occurred while generating a signature
     * @throws IOException              if protobuf fails to be serialized and written to file
     */
    void signV4(DataSource data, File outputFile, boolean ignoreFailures)
            throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, IOException;

    /**
     * Checks if the signing configuration provided to the engine is capable of creating a
     * SourceStamp.
     */
    default boolean isEligibleForSourceStamp() {
        return false;
    }

    /**
     * Generates the digest of the certificate used to sign the source stamp.
     */
    default byte[] generateSourceStampCertificateDigest() throws SignatureException {
        return new byte[0];
    }

    /**
     * Indicates to this engine that it will no longer be used. Invoking this on an already closed
     * engine is OK.
     *
     * <p>This does not change the output APK. For example, if the output APK is not yet fully
     * signed, it will remain so after this method terminates.
     */
    @Override
    void close();

    /**
     * Request to inspect the specified JAR entry.
     *
     * <p>The entry's uncompressed data must be provided to the data sink returned by
     * {@link #getDataSink()}. Once the entry's data has been provided to the sink, {@link #done()}
     * must be invoked.
     */
    interface InspectJarEntryRequest {

        /**
         * Returns the data sink into which the entry's uncompressed data should be sent.
         */
        DataSink getDataSink();

        /**
         * Indicates that entry's data has been provided in full.
         */
        void done();

        /**
         * Returns the name of the JAR entry.
         */
        String getEntryName();
    }

    /**
     * Request to add JAR signature (aka v1 signature) to the output APK.
     *
     * <p>Entries listed in {@link #getAdditionalJarEntries()} must be added to the output APK after
     * which {@link #done()} must be invoked.
     */
    interface OutputJarSignatureRequest {

        /**
         * Returns JAR entries that must be added to the output APK.
         */
        List<JarEntry> getAdditionalJarEntries();

        /**
         * Indicates that the JAR entries contained in this request were added to the output APK.
         */
        void done();

        /**
         * JAR entry.
         */
        public static class JarEntry {
            private final String mName;
            private final byte[] mData;

            /**
             * Constructs a new {@code JarEntry} with the provided name and data.
             *
             * @param data uncompressed data of the entry. Changes to this array will not be
             *             reflected in {@link #getData()}.
             */
            public JarEntry(String name, byte[] data) {
                mName = name;
                mData = data.clone();
            }

            /**
             * Returns the name of this ZIP entry.
             */
            public String getName() {
                return mName;
            }

            /**
             * Returns the uncompressed data of this JAR entry.
             */
            public byte[] getData() {
                return mData.clone();
            }
        }
    }

    /**
     * Request to add the specified APK Signing Block to the output APK. APK Signature Scheme v2
     * signature(s) of the APK are contained in this block.
     *
     * <p>The APK Signing Block returned by {@link #getApkSigningBlock()} must be placed into the
     * output APK such that the block is immediately before the ZIP Central Directory, the offset of
     * ZIP Central Directory in the ZIP End of Central Directory record must be adjusted
     * accordingly, and then {@link #done()} must be invoked.
     *
     * <p>If the output contains an APK Signing Block, that block must be replaced by the block
     * contained in this request.
     *
     * @deprecated This is now superseded by {@link OutputApkSigningBlockRequest2}.
     */
    @Deprecated
    interface OutputApkSigningBlockRequest {

        /**
         * Returns the APK Signing Block.
         */
        byte[] getApkSigningBlock();

        /**
         * Indicates that the APK Signing Block was output as requested.
         */
        void done();
    }

    /**
     * Request to add the specified APK Signing Block to the output APK. APK Signature Scheme v2
     * signature(s) of the APK are contained in this block.
     *
     * <p>The APK Signing Block returned by {@link #getApkSigningBlock()} must be placed into the
     * output APK such that the block is immediately before the ZIP Central Directory. Immediately
     * before the APK Signing Block must be padding consists of the number of 0x00 bytes returned by
     * {@link #getPaddingSizeBeforeApkSigningBlock()}. The offset of ZIP Central Directory in the
     * ZIP End of Central Directory record must be adjusted accordingly, and then {@link #done()}
     * must be invoked.
     *
     * <p>If the output contains an APK Signing Block, that block must be replaced by the block
     * contained in this request.
     */
    interface OutputApkSigningBlockRequest2 {
        /**
         * Returns the APK Signing Block.
         */
        byte[] getApkSigningBlock();

        /**
         * Indicates that the APK Signing Block was output as requested.
         */
        void done();

        /**
         * Returns the number of 0x00 bytes the caller must place immediately before APK Signing
         * Block.
         */
        int getPaddingSizeBeforeApkSigningBlock();
    }

    /**
     * Instructions about how to handle an input APK's JAR entry.
     *
     * <p>The instructions indicate whether to output the entry (see {@link #getOutputPolicy()}) and
     * may contain a request to inspect the entry (see {@link #getInspectJarEntryRequest()}), in
     * which case the request must be fulfilled before {@link ApkSignerEngine#outputJarEntries()} is
     * invoked.
     */
    public static class InputJarEntryInstructions {
        private final OutputPolicy mOutputPolicy;
        private final InspectJarEntryRequest mInspectJarEntryRequest;

        /**
         * Constructs a new {@code InputJarEntryInstructions} instance with the provided entry
         * output policy and without a request to inspect the entry.
         */
        public InputJarEntryInstructions(OutputPolicy outputPolicy) {
            this(outputPolicy, null);
        }

        /**
         * Constructs a new {@code InputJarEntryInstructions} instance with the provided entry
         * output mode and with the provided request to inspect the entry.
         *
         * @param inspectJarEntryRequest request to inspect the entry or {@code null} if there's no
         *                               need to inspect the entry.
         */
        public InputJarEntryInstructions(
                OutputPolicy outputPolicy,
                InspectJarEntryRequest inspectJarEntryRequest) {
            mOutputPolicy = outputPolicy;
            mInspectJarEntryRequest = inspectJarEntryRequest;
        }

        /**
         * Returns the output policy for this entry.
         */
        public OutputPolicy getOutputPolicy() {
            return mOutputPolicy;
        }

        /**
         * Returns the request to inspect the JAR entry or {@code null} if there is no need to
         * inspect the entry.
         */
        public InspectJarEntryRequest getInspectJarEntryRequest() {
            return mInspectJarEntryRequest;
        }

        /**
         * Output policy for an input APK's JAR entry.
         */
        public static enum OutputPolicy {
            /**
             * Entry must not be output.
             */
            SKIP,

            /**
             * Entry should be output.
             */
            OUTPUT,

            /**
             * Entry will be output by the engine. The client can thus ignore this input entry.
             */
            OUTPUT_BY_ENGINE,
        }
    }
}
