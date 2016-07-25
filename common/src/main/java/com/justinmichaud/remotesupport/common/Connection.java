package com.justinmichaud.remotesupport.common;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.util.Date;
import java.util.Scanner;

public class Connection {

    private final static String SSL_VERSION = "TLSv1.2";
    private final static char[] keystorePassword = "1".toCharArray();

    public final String alias;
    public final String partnerAlias;

    private SSLContext sslContext;
    private KeyStore privateKey, trustedKeys;
    private File trustedKeystoreFile;

    private SSLSocket socket;

    public Connection(String alias, String partnerAlias, Socket baseSocket, File privateKeystoreFile,
                      File trustedKeystoreFile, boolean server)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());

        this.alias = alias;
        this.partnerAlias = partnerAlias;
        this.trustedKeystoreFile = trustedKeystoreFile;

        loadOrCreateKeypair(privateKeystoreFile);
        loadOrCreateTrustStore(trustedKeystoreFile);
        loadSSLContext();

        socket = (SSLSocket) sslContext.getSocketFactory().createSocket(baseSocket,
                baseSocket.getLocalAddress().getHostName(), baseSocket.getLocalPort(), true);
        socket.setKeepAlive(true);
        socket.setUseClientMode(!server);
        if (server) socket.setNeedClientAuth(true);

        System.out.println("Your fingerprint is " + getFingerprint());

        socket.startHandshake();
    }

    private void createKeypair(File privateKeystoreFile)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        try (FileOutputStream privateOut = new FileOutputStream(privateKeystoreFile)) {
            privateKey = KeyStore.getInstance("JKS");
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
        } catch (Exception e) {
            if (privateKeystoreFile.exists()) privateKeystoreFile.delete();

            throw e;
        }
    }

    private void loadKeypair(File privateKeystoreFile)
            throws GeneralSecurityException, IOException {
        try (FileInputStream privateIn = new FileInputStream(privateKeystoreFile)) {
            privateKey = KeyStore.getInstance("JKS");
            privateKey.load(privateIn, keystorePassword);
        }
    }

    private void loadOrCreateKeypair(File privateKeystoreFile)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        try {
            loadKeypair(privateKeystoreFile);
        } catch (IOException|GeneralSecurityException e) {
            createKeypair(privateKeystoreFile);
            loadKeypair(privateKeystoreFile);
        }
    }

    private void createTrustStore(File trustedKeystoreFile) throws GeneralSecurityException, IOException {
        try (FileOutputStream trustedOut = new FileOutputStream(trustedKeystoreFile)) {
            trustedKeys = KeyStore.getInstance("JKS");
            trustedKeys.load(null, null);
            trustedKeys.store(trustedOut, keystorePassword);
        }
    }

    private void loadTrustStore(File trustedKeystoreFile)
            throws GeneralSecurityException, IOException {
        try (FileInputStream trustedIn = new FileInputStream(trustedKeystoreFile)) {
            trustedKeys = KeyStore.getInstance("JKS");
            trustedKeys.load(trustedIn, keystorePassword);
        }
    }

    private void loadOrCreateTrustStore(File trustedKeystoreFile)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        try {
            loadTrustStore(trustedKeystoreFile);
        } catch (IOException|GeneralSecurityException e) {
            createTrustStore(trustedKeystoreFile);
            loadTrustStore(trustedKeystoreFile);
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

    private static boolean prompt(String message) {
        Scanner in = new Scanner(System.in); //Avoid closing because main thread uses System.in
        System.out.println(message + " [y/N]");
        return in.nextLine().equalsIgnoreCase("y");
    }

    private void addTrustedCertificate(X509Certificate cert) throws KeyStoreException, IOException,
            CertificateException, NoSuchAlgorithmException {
        trustedKeys.deleteEntry(partnerAlias);
        trustedKeys.setCertificateEntry(partnerAlias, cert);

        try (FileOutputStream trustedOut = new FileOutputStream(trustedKeystoreFile)) {
            trustedKeys.store(trustedOut, keystorePassword);
        }
    }

    private void checkCertificateTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        if (chain == null || chain.length != 1)
            throw new CertificateException("Chain Length Not Correct");

        X509Certificate partnerCert = chain[0];

        try {
            java.security.cert.Certificate storedCert = trustedKeys.getCertificate(partnerAlias);

            if (storedCert == null) {
                if (prompt("This is the first time connecting to this computer. Does this fingerprint ("
                        + getCertificateFingerprint(partnerCert)
                        + ") match the one on the other computer?")) {
                    addTrustedCertificate(partnerCert);
                }
                else {
                    throw new CertificateException("Certificate rejected");
                }
            }
            else if (!storedCert.equals(partnerCert)) {
                if (prompt("This computer has a different identity since the last time it connected."
                        + "Does this fingerprint ("
                        + getCertificateFingerprint(partnerCert)
                        + ") match the one on the other computer?")) {
                    addTrustedCertificate(partnerCert);
                }
                else {
                    throw new CertificateException("Certificate rejected");
                }
            }
        } catch (KeyStoreException e) {
            e.printStackTrace();
            throw new CertificateException("KeystoreException");
        } catch (NoSuchAlgorithmException|IOException e) {
            e.printStackTrace();
            throw new CertificateException("Internal error");
        }
    }

    private void loadSSLContext() throws GeneralSecurityException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(privateKey, keystorePassword);

        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                checkCertificateTrusted(chain, authType);
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                checkCertificateTrusted(chain, authType);
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[] {};
            }
        };

        sslContext = SSLContext.getInstance(SSL_VERSION);
        sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[] {tm}, null);
    }

    public String getFingerprint() throws KeyStoreException, CertificateEncodingException, NoSuchAlgorithmException {
        return getCertificateFingerprint((X509Certificate) privateKey.getCertificate("cert"));
    }

    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }
}
