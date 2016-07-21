package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class Server {

    private KeyStore serverPrivateStore;
    private KeyManagerFactory keyManagerFactory;

    private TrustManagerFactory trustManagerFactory;

    private SSLContext sslContext;

    public void runServer() throws IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, InvalidKeyException, SignatureException, NoSuchProviderException, KeyStoreException, KeyManagementException {
        load();

        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        SSLSocket conn = (SSLSocket) sslContext.getSocketFactory().createSocket(baseConnection,
                baseConnection.getLocalAddress().getHostName(), baseConnection.getLocalPort(), true);
        conn.setUseClientMode(false);

        InputStream inputStream = conn.getInputStream();
        InputStreamReader streamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String string;
        while ((string = bufferedReader.readLine()) != null) {
            System.out.println(string);
            System.out.flush();
        }
    }

    private void load() throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, SignatureException, NoSuchProviderException, InvalidKeyException, IOException, KeyManagementException {
        loadOrCreateServerKeys(new File("server_private_keystore.jks"), new File("server_public_keystore.jks"));
        loadClientCertificate();
        loadSsl();
    }

    private void loadSsl() throws NoSuchAlgorithmException, CertificateException, KeyStoreException, SignatureException, NoSuchProviderException, InvalidKeyException, IOException, UnrecoverableKeyException, KeyManagementException {
        sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
    }

    private void loadOrCreateServerKeys(File priv, File pub) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, NoSuchProviderException, InvalidKeyException, SignatureException, UnrecoverableKeyException {
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

            CertAndKeyGen keyGen = new CertAndKeyGen("RSA","SHA1WithRSA",null);
            keyGen.generate(1024);
            X509Certificate[] certs = new X509Certificate[1];
            certs[0] = keyGen.getSelfCertificate(new X500Name("CN=ROOT"), (long)365*24*3600);

            serverPrivateStore.setKeyEntry("server_cert", keyGen.getPrivateKey(), serverPrivateStorePass, certs);
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
