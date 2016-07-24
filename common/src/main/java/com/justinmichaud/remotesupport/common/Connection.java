package com.justinmichaud.remotesupport.common;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

public class Connection {

    private final static char[] keystorePassword = "1".toCharArray();

    private SSLContext sslContext;
    private KeyStore privateKey, publicKey, trustedKeys;

    private SSLSocket socket;

    public Connection(Socket baseSocket, File privateKeystoreFile, File publicKeystoreFile,
                      File trustedKeystoreFile, boolean server) throws GeneralSecurityException, IOException {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());

        loadOrCreateKeypair(privateKeystoreFile, publicKeystoreFile);
        loadSSLContext();

        socket = (SSLSocket) sslContext.getSocketFactory().createSocket(baseSocket,
                baseSocket.getLocalAddress().getHostName(), baseSocket.getLocalPort(), true);
        socket.setUseClientMode(!server);
        if (server) socket.setNeedClientAuth(true);
    }

    private void createKeypair(File privateKeystoreFile, File publicKeystoreFile)
            throws KeyStoreException, IOException {
        try (
                FileOutputStream privateOut = new FileOutputStream(privateKeystoreFile);
                FileOutputStream publicOut = new FileOutputStream(publicKeystoreFile)
        ) {
            privateKey = KeyStore.getInstance("JKS");
            privateKey.load(null, null);

            publicKey = KeyStore.getInstance("JKS");
            publicKey.load(null, null);

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

            publicKey.setCertificateEntry("cert", certs[0]);
            publicKey.store(publicOut, keystorePassword);
        } catch (Exception e) {
            if (privateKeystoreFile.exists()) privateKeystoreFile.delete();
            if (publicKeystoreFile.exists()) publicKeystoreFile.delete();
        }
    }

    private void loadKeypair(File privateKeystoreFile, File publicKeystoreFile)
            throws GeneralSecurityException, IOException {
        try (FileInputStream privateOut = new FileInputStream(privateKeystoreFile);
             FileInputStream publicOut = new FileInputStream(publicKeystoreFile)) {
            privateKey = KeyStore.getInstance("JKS");
            privateKey.load(privateOut, keystorePassword);

            publicKey = KeyStore.getInstance("JKS");
            publicKey.load(publicOut, keystorePassword);
        }
    }

    private void loadOrCreateKeypair(File privateKeystoreFile, File publicKeystoreFile)
            throws GeneralSecurityException, IOException {
        try {
            loadKeypair(privateKeystoreFile, publicKeystoreFile);
        } catch (IOException|GeneralSecurityException e) {
            createKeypair(privateKeystoreFile, publicKeystoreFile);
            loadKeypair(privateKeystoreFile, publicKeystoreFile);
        }
    }

    private void loadSSLContext() throws GeneralSecurityException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(privateKey, keystorePassword);

        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                System.out.println("checkClientTrusted");
                //TODO
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                System.out.println("checkServerTrusted");
                //TODO
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[] {};
            }
        };

        sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[] {tm}, null);
    }

    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }
}
