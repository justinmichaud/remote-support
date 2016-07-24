package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
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
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

public class Server {

    private KeyStore serverPrivateStore;
    private KeyManagerFactory keyManagerFactory;

    private TrustManagerFactory trustManagerFactory;

    private SSLContext sslContext;

    public void runServer() throws IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, InvalidKeyException, SignatureException, NoSuchProviderException, KeyStoreException, KeyManagementException, OperatorCreationException {
        load();

        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        SSLSocket conn = (SSLSocket) sslContext.getSocketFactory().createSocket(baseConnection,
                baseConnection.getLocalAddress().getHostName(), baseConnection.getLocalPort(), true);
        conn.setUseClientMode(false);

        System.out.println("Connected!");

        InputStream inputStream = conn.getInputStream();
        InputStreamReader streamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String string;
        while ((string = bufferedReader.readLine()) != null) {
            System.out.println(string);
            System.out.flush();
        }
    }

    private void load() throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, SignatureException, NoSuchProviderException, InvalidKeyException, IOException, KeyManagementException, OperatorCreationException {
        Security.addProvider(new BouncyCastleProvider());
        loadOrCreateServerKeys(new File("server_private_keystore.jks"), new File("server_public_keystore.jks"));
        loadClientCertificate();
        loadSsl();
    }

    private void loadSsl() throws NoSuchAlgorithmException, CertificateException, KeyStoreException, SignatureException, NoSuchProviderException, InvalidKeyException, IOException, UnrecoverableKeyException, KeyManagementException {
        sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
    }

    private void loadOrCreateServerKeys(File priv, File pub) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, NoSuchProviderException, InvalidKeyException, SignatureException, UnrecoverableKeyException, OperatorCreationException {
        final char[] serverPrivateStorePass = "1234".toCharArray();
        final char[] serverPublicStorePass = "1".toCharArray();

        serverPrivateStore = KeyStore.getInstance("JKS");

        if (!priv.exists() || !pub.exists()) {
            // Public stores just our certificate, private stores our private key too
            // Public store is sent to client to verify
            KeyStore serverPublicStore = KeyStore.getInstance("JKS");

            FileOutputStream priv_out = new FileOutputStream(priv);
            FileOutputStream pub_out = new FileOutputStream(pub);

            serverPrivateStore.load(null, null);
            serverPublicStore.load(null, null);

            // Generate private key and public certificate

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

            serverPrivateStore.setKeyEntry("server_cert", key.getPrivate(), serverPrivateStorePass, certs);
            serverPrivateStore.store(priv_out, serverPrivateStorePass);

            serverPublicStore.setCertificateEntry("server_cert", certs[0]);
            serverPrivateStore.store(new FileOutputStream(pub), serverPublicStorePass);

            priv_out.close();
            pub_out.close();
        }

        serverPrivateStore.load(new FileInputStream(priv), serverPrivateStorePass);

        keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(serverPrivateStore, serverPrivateStorePass);
    }

    private void loadClientCertificate() throws NoSuchAlgorithmException, KeyStoreException {
        trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
    }

    public static void main(String... args) {
        System.out.println("Server");

        Server server = new Server();
        try {
            server.runServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
