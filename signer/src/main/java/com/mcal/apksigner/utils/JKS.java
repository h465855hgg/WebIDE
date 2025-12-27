

package com.mcal.apksigner.utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.spec.SecretKeySpec;


public class JKS extends KeyStoreSpi {
    /**
     * Ah, Sun. So goddamned clever with those magic bytes.
     */
    private static final int MAGIC = 0xFEEDFEED;

    private static final int PRIVATE_KEY = 1;
    private static final int TRUSTED_CERT = 2;

    private final Vector<String> aliases;
    private final HashMap<String, Certificate> trustedCerts;
    private final HashMap<String, byte[]> privateKeys;
    private final HashMap<String, Certificate[]> certChains;
    private final HashMap<String, Date> dates;

    public JKS() {
        super();
        aliases = new Vector<>();
        trustedCerts = new HashMap<>();
        privateKeys = new HashMap<>();
        certChains = new HashMap<>();
        dates = new HashMap<>();

    }

    private static Certificate readCert(DataInputStream in) throws IOException, CertificateException, NoSuchAlgorithmException {
        String type = in.readUTF();
        int len = in.readInt();
        byte[] encoded = new byte[len];
        in.read(encoded);
        CertificateFactory factory = CertificateFactory.getInstance(type);
        return factory.generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static void writeCert(DataOutputStream dout, Certificate cert) throws IOException, CertificateException {
        dout.writeUTF(cert.getType());
        byte[] b = cert.getEncoded();
        dout.writeInt(b.length);
        dout.write(b);
    }

    private static byte[] decryptKey(byte[] encryptedPKI, byte[] passwd) throws UnrecoverableKeyException {
        try {
            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(encryptedPKI);
            byte[] encr = epki.getEncryptedData();
            byte[] keystream = new byte[20];
            System.arraycopy(encr, 0, keystream, 0, 20);
            byte[] check = new byte[20];
            System.arraycopy(encr, encr.length - 20, check, 0, 20);
            byte[] key = new byte[encr.length - 40];
            MessageDigest sha = MessageDigest.getInstance("SHA1");
            int count = 0;
            while (count < key.length) {
                sha.reset();
                sha.update(passwd);
                sha.update(keystream);
                sha.digest(keystream, 0, keystream.length);
                for (int i = 0; i < keystream.length && count < key.length; i++) {
                    key[count] = (byte) (keystream[i] ^ encr[count + 20]);
                    count++;
                }
            }
            sha.reset();
            sha.update(passwd);
            sha.update(key);
            if (!MessageDigest.isEqual(check, sha.digest())) {
                throw new UnrecoverableKeyException("checksum mismatch");
            }
            return key;
        } catch (Exception x) {
            throw new UnrecoverableKeyException(x.getMessage());
        }
    }

    private static byte[] encryptKey(Key key, byte[] passwd) throws KeyStoreException {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA1");
            SecureRandom rand = SecureRandom.getInstance("SHA1PRNG");
            byte[] k = key.getEncoded();
            byte[] encrypted = new byte[k.length + 40];
            byte[] keystream = rand.getSeed(20);
            System.arraycopy(keystream, 0, encrypted, 0, 20);
            int count = 0;
            while (count < k.length) {
                sha.reset();
                sha.update(passwd);
                sha.update(keystream);
                sha.digest(keystream, 0, keystream.length);
                for (int i = 0; i < keystream.length && count < k.length; i++) {
                    encrypted[count + 20] = (byte) (keystream[i] ^ k[count]);
                    count++;
                }
            }
            sha.reset();
            sha.update(passwd);
            sha.update(k);
            sha.digest(encrypted, encrypted.length - 20, 20);
            // 1.3.6.1.4.1.42.2.17.1.1 is Sun's private OID for this
            // encryption algorithm.
            return new EncryptedPrivateKeyInfo("1.3.6.1.4.1.42.2.17.1.1", encrypted).getEncoded();
        } catch (Exception x) {
            throw new KeyStoreException(x.getMessage());
        }
    }

    private static byte[] charsToBytes(char[] passwd) {
        byte[] buf = new byte[passwd.length * 2];
        for (int i = 0, j = 0; i < passwd.length; i++) {
            buf[j++] = (byte) (passwd[i] >>> 8);
            buf[j++] = (byte) passwd[i];
        }
        return buf;
    }

    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        alias = alias.toLowerCase();

        if (!privateKeys.containsKey(alias)) {
            return null;
        }
        byte[] key = decryptKey((byte[]) privateKeys.get(alias), charsToBytes(password));
        Certificate[] chain = engineGetCertificateChain(alias);
        if (chain.length > 0) {
            try {
                // Private and public keys MUST have the same algorithm.
                KeyFactory fact = KeyFactory.getInstance(chain[0].getPublicKey().getAlgorithm());
                return fact.generatePrivate(new PKCS8EncodedKeySpec(key));
            } catch (InvalidKeySpecException x) {
                throw new UnrecoverableKeyException(x.getMessage());
            }
        } else {
            return new SecretKeySpec(key, alias);
        }
    }

    public Certificate[] engineGetCertificateChain(String alias) {
        alias = alias.toLowerCase();
        return (Certificate[]) certChains.get(alias);
    }

    public Certificate engineGetCertificate(String alias) {
        alias = alias.toLowerCase();
        if (engineIsKeyEntry(alias)) {
            Certificate[] certChain = (Certificate[]) certChains.get(alias);
            if (certChain != null && certChain.length > 0) {
                return certChain[0];
            }
        }
        return (Certificate) trustedCerts.get(alias);
    }

    public Date engineGetCreationDate(String alias) {
        alias = alias.toLowerCase();
        return (Date) dates.get(alias);
    }

    public void engineSetKeyEntry(String alias, Key key, char[] passwd, Certificate[] certChain) throws KeyStoreException {
        alias = alias.toLowerCase();
        if (trustedCerts.containsKey(alias))
            throw new KeyStoreException("\"" + alias + " is a trusted certificate entry");
        privateKeys.put(alias, encryptKey(key, charsToBytes(passwd)));
        if (certChain != null) {
            certChains.put(alias, certChain);
        } else {
            certChains.put(alias, new Certificate[0]);
        }
        if (!aliases.contains(alias)) {
            dates.put(alias, new Date());
            aliases.add(alias);
        }
    }

    public void engineSetKeyEntry(String alias, byte[] encodedKey, Certificate[] certChain) throws KeyStoreException {
        alias = alias.toLowerCase();
        if (trustedCerts.containsKey(alias)) {
            throw new KeyStoreException("\"" + alias + "\" is a trusted certificate entry");
        }
        try {
            new EncryptedPrivateKeyInfo(encodedKey);
        } catch (IOException ioe) {
            throw new KeyStoreException("encoded key is not an EncryptedPrivateKeyInfo");
        }
        privateKeys.put(alias, encodedKey);
        if (certChain != null) {
            certChains.put(alias, certChain);
        } else {
            certChains.put(alias, new Certificate[0]);
        }
        if (!aliases.contains(alias)) {
            dates.put(alias, new Date());
            aliases.add(alias);
        }
    }

    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        alias = alias.toLowerCase();
        if (privateKeys.containsKey(alias)) {
            throw new KeyStoreException("\"" + alias + "\" is a private key entry");
        }
        if (cert == null) {
            throw new NullPointerException();
        }
        trustedCerts.put(alias, cert);
        if (!aliases.contains(alias)) {
            dates.put(alias, new Date());
            aliases.add(alias);
        }
    }

    public void engineDeleteEntry(String alias) throws KeyStoreException {
        alias = alias.toLowerCase();
        aliases.remove(alias);
    }

    public Enumeration<String> engineAliases() {
        return aliases.elements();
    }

    public boolean engineContainsAlias(String alias) {
        alias = alias.toLowerCase();
        return aliases.contains(alias);
    }

    public int engineSize() {
        return aliases.size();
    }

    public boolean engineIsKeyEntry(String alias) {
        alias = alias.toLowerCase();
        return privateKeys.containsKey(alias);
    }

    public boolean engineIsCertificateEntry(String alias) {
        alias = alias.toLowerCase();
        return trustedCerts.containsKey(alias);
    }

    public String engineGetCertificateAlias(Certificate cert) {
        for (String alias : trustedCerts.keySet()) {
            if (cert.equals(trustedCerts.get(alias)))
                return alias;
        }
        return null;
    }

    public void engineStore(OutputStream out, char[] passwd) throws IOException, NoSuchAlgorithmException, CertificateException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        md.update(charsToBytes(passwd));
        md.update("Mighty Aphrodite".getBytes(StandardCharsets.UTF_8));
        DataOutputStream dout = new DataOutputStream(new DigestOutputStream(out, md));
        dout.writeInt(MAGIC);
        dout.writeInt(2);
        dout.writeInt(aliases.size());
        for (Enumeration<String> e = aliases.elements(); e.hasMoreElements(); ) {
            String alias = (String) e.nextElement();
            if (trustedCerts.containsKey(alias)) {
                dout.writeInt(TRUSTED_CERT);
                dout.writeUTF(alias);
                dout.writeLong(((Date) dates.get(alias)).getTime());
                writeCert(dout, (Certificate) trustedCerts.get(alias));
            } else {
                dout.writeInt(PRIVATE_KEY);
                dout.writeUTF(alias);
                dout.writeLong(((Date) dates.get(alias)).getTime());
                byte[] key = (byte[]) privateKeys.get(alias);
                dout.writeInt(key.length);
                dout.write(key);
                Certificate[] chain = (Certificate[]) certChains.get(alias);
                dout.writeInt(chain.length);
                for (Certificate certificate : chain) {
                    writeCert(dout, certificate);
                }
            }
        }
        byte[] digest = md.digest();
        dout.write(digest);
    }

    public void engineLoad(InputStream in, char[] passwd) throws IOException, NoSuchAlgorithmException, CertificateException {
        MessageDigest md = MessageDigest.getInstance("SHA");
        if (passwd != null) {
            md.update(charsToBytes(passwd));
        }
        md.update("Mighty Aphrodite".getBytes(StandardCharsets.UTF_8));
        aliases.clear();
        trustedCerts.clear();
        privateKeys.clear();
        certChains.clear();
        dates.clear();
        if (in == null) {
            return;
        }
        DataInputStream din = new DataInputStream(new DigestInputStream(in, md));
        if (din.readInt() != MAGIC) {
            throw new IOException("not a JavaKeyStore");
        }
        din.readInt();  // version no.
        final int n = din.readInt();
        aliases.ensureCapacity(n);
        if (n < 0) {
            throw new LoadKeystoreException("Malformed key store");
        }
        for (int i = 0; i < n; i++) {
            int type = din.readInt();
            String alias = din.readUTF();
            aliases.add(alias);
            dates.put(alias, new Date(din.readLong()));
            switch (type) {
                case PRIVATE_KEY -> {
                    int len = din.readInt();
                    byte[] encoded = new byte[len];
                    din.read(encoded);
                    privateKeys.put(alias, encoded);
                    int count = din.readInt();
                    Certificate[] chain = new Certificate[count];
                    for (int j = 0; j < count; j++) {
                        chain[j] = readCert(din);
                    }
                    certChains.put(alias, chain);
                }
                case TRUSTED_CERT -> trustedCerts.put(alias, readCert(din));
                default -> throw new LoadKeystoreException("Malformed key store");
            }
        }

        if (passwd != null) {
            byte[] computedHash = md.digest();
            byte[] storedHash = new byte[20];
            din.read(storedHash);
            if (!MessageDigest.isEqual(storedHash, computedHash)) {
                throw new LoadKeystoreException("Incorrect password, or integrity check failed.");
            }
        }
    }
}
