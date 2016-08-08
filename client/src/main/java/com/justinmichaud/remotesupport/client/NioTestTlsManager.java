package com.justinmichaud.remotesupport.client;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.function.Function;

public class NioTestTlsManager {

    private final static String SSL_VERSION = "TLSv1.2";
    private final static char[] keystorePassword = "1".toCharArray();

    public static SSLContext getSSLContext(String partnerName, File privateKeystoreFile,
                                           File trustedKeystoreFile, Function<String, String> prompter)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());

        KeyStore privateKey = loadOrCreatePrivateKeypair(privateKeystoreFile);
        KeyStore trustedKeys = loadOrCreateTrustStore(trustedKeystoreFile);
        TrustQuestionProvider qp = new TrustQuestionProvider(prompter, privateKey, trustedKeys,
                partnerName, trustedKeystoreFile);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(privateKey, keystorePassword);

        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                checkCertificateTrusted(chain, authType, qp);
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                checkCertificateTrusted(chain, authType, qp);
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[] {};
            }
        };

        SSLContext sslContext = SSLContext.getInstance(SSL_VERSION);
        sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[] {tm}, null);
        return sslContext;
    }

    private static KeyStore createAndSavePrivateKeypair(File privateKeystoreFile)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        try (FileOutputStream privateOut = new FileOutputStream(privateKeystoreFile)) {
            KeyStore privateKey = KeyStore.getInstance("JKS");
            privateKey.load(null, null);

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(1024);
            KeyPair key = keyGen.generateKeyPair();

            ContentSigner sigGen = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(key.getPrivate());
            SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(key.getPublic().getEncoded());

            Date startDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
            Date endDate = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000);

            X509v1CertificateBuilder certGen = new X509v1CertificateBuilder(
                    new org.bouncycastle.asn1.x500.X500Name("CN=Test"),
                    BigInteger.ONE,
                    startDate, endDate,
                    new org.bouncycastle.asn1.x500.X500Name("CN=Test"),
                    subPubKeyInfo
            );

            X509CertificateHolder certificateHolder = certGen.build(sigGen);
            X509Certificate[] certs = new X509Certificate[1];
            certs[0] = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);

            privateKey.setKeyEntry("cert", key.getPrivate(), keystorePassword, certs);
            privateKey.store(privateOut, keystorePassword);
            return privateKey;
        } catch (Exception e) {
            if (privateKeystoreFile.exists()) privateKeystoreFile.delete();
            throw e;
        }
    }

    private static KeyStore loadPrivateKeypair(File privateKeystoreFile)
            throws GeneralSecurityException, IOException {
        try (FileInputStream privateIn = new FileInputStream(privateKeystoreFile)) {
            KeyStore privateKey = KeyStore.getInstance("JKS");
            privateKey.load(privateIn, keystorePassword);
            return privateKey;
        }
    }

    private static KeyStore loadOrCreatePrivateKeypair(File privateKeystoreFile)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        try {
            return loadPrivateKeypair(privateKeystoreFile);
        } catch (IOException|GeneralSecurityException e) {
            createAndSavePrivateKeypair(privateKeystoreFile);
            return loadPrivateKeypair(privateKeystoreFile);
        }
    }

    private static KeyStore createTrustStore(File trustedKeystoreFile) throws GeneralSecurityException, IOException {
        try (FileOutputStream trustedOut = new FileOutputStream(trustedKeystoreFile)) {
            KeyStore trustedKeys = KeyStore.getInstance("JKS");
            trustedKeys.load(null, null);
            trustedKeys.store(trustedOut, keystorePassword);
            return trustedKeys;
        }
    }

    private static KeyStore loadTrustStore(File trustedKeystoreFile)
            throws GeneralSecurityException, IOException {
        try (FileInputStream trustedIn = new FileInputStream(trustedKeystoreFile)) {
            KeyStore trustedKeys = KeyStore.getInstance("JKS");
            trustedKeys.load(trustedIn, keystorePassword);
            return trustedKeys;
        }
    }

    private static KeyStore loadOrCreateTrustStore(File trustedKeystoreFile)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        try {
            return loadTrustStore(trustedKeystoreFile);
        } catch (IOException|GeneralSecurityException e) {
            createTrustStore(trustedKeystoreFile);
            return loadTrustStore(trustedKeystoreFile);
        }
    }

    private static String getCertificateFingerprint(X509Certificate cert)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();

        StringBuilder fingerprint = new StringBuilder();

        for (byte b : digest) {
            fingerprint.append(String.format("%02x:", b));
        }

        return fingerprint.substring(0, fingerprint.length());
    }

    private static void addTrustedCertificate(X509Certificate cert, KeyStore trustedKeys, String partnerAlias,
                                              File trustedKeystoreFile)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        trustedKeys.deleteEntry(partnerAlias);
        trustedKeys.setCertificateEntry(partnerAlias, cert);

        try (FileOutputStream trustedOut = new FileOutputStream(trustedKeystoreFile)) {
            trustedKeys.store(trustedOut, keystorePassword);
        }
    }

    private static class TrustQuestionProvider {
        private final Function<String, String> prompter;
        private final KeyStore trustedKeys;
        private final String partnerAlias;
        private final File trustedKeystoreFile;

        public TrustQuestionProvider(Function<String, String> prompter, KeyStore privateKey, KeyStore trustedKeys,
                                     String partnerAlias, File trustedKeystoreFile) throws KeyStoreException, CertificateEncodingException, NoSuchAlgorithmException {
            this.prompter = prompter;
            this.trustedKeys = trustedKeys;
            this.partnerAlias = partnerAlias;
            this.trustedKeystoreFile = trustedKeystoreFile;

            System.out.println(getCertificateFingerprint((X509Certificate) privateKey.getCertificate("cert")));
        }

        public void checkCertificate(X509Certificate untrusted) throws CertificateException {
            try {
                java.security.cert.Certificate storedCert = trustedKeys.getCertificate(partnerAlias);

                if (storedCert == null) {
                    if (!trustNew(untrusted)) throw new CertificateException("Certificate rejected");
                }
                else if (!storedCert.equals(untrusted)) {
                    if (!trustDifferent(untrusted)) throw new CertificateException("Certificate rejected");
                }
            } catch (KeyStoreException e) {
                e.printStackTrace();
                throw new CertificateException("KeystoreException");
            } catch (Exception e) {
                e.printStackTrace();
                throw new CertificateException("Internal error");
            }
        }

        public boolean trustNew(X509Certificate partnerCert) throws Exception {
            if (prompter.apply("This is the first time connecting to this computer. Does this fingerprint ("
                    + getCertificateFingerprint(partnerCert)
                    + ") match the one on the other computer?").equalsIgnoreCase("y")) {
                addTrustedCertificate(partnerCert, trustedKeys, partnerAlias, trustedKeystoreFile);
                return true;
            }
            return false;
        }

        public boolean trustDifferent(X509Certificate partnerCert) throws Exception {
            if (prompter.apply("This computer has a different identity since the last time it connected." +
                    "This could be an attempt to hijack your computer." +
                    "Does this fingerprint (" + getCertificateFingerprint(partnerCert) +
                    ") match the one on the other computer?").equalsIgnoreCase("y")) {
                addTrustedCertificate(partnerCert, trustedKeys, partnerAlias, trustedKeystoreFile);
                return true;
            }
            return false;
        }
    }

    private static void checkCertificateTrusted(X509Certificate[] chain, String authType, TrustQuestionProvider qp)
            throws CertificateException {
        if (chain == null || chain.length != 1)
            throw new CertificateException("Chain Length Not Correct");

        X509Certificate partnerCert = chain[0];
        qp.checkCertificate(partnerCert);
    }

}
