import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;

public class Client {

    private KeyManagerFactory keyManagerFactory;

    private TrustManagerFactory trustManagerFactory;

    private SSLContext sslContext;

    public void runClient() throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        load();

        SSLSocketFactory sslsocketfactory = sslContext.getSocketFactory();
        SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket("localhost", 9999);

        InputStream inputstream = System.in;
        InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
        BufferedReader bufferedreader = new BufferedReader(inputstreamreader);

        OutputStream outputstream = sslsocket.getOutputStream();
        OutputStreamWriter outputstreamwriter = new OutputStreamWriter(outputstream);
        BufferedWriter bufferedwriter = new BufferedWriter(outputstreamwriter);

        String string;
        while ((string = bufferedreader.readLine()) != null) {
            bufferedwriter.write(string + '\n');
            bufferedwriter.flush();
        }
    }

    private void load() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, UnrecoverableKeyException, KeyManagementException {
        loadOrCreateClientKeys();
        loadServerCertificate(new File("server_public_keystore.jks"));
        loadSsl();
    }

    private void loadSsl() throws KeyManagementException, NoSuchAlgorithmException {
        sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
    }

    private void loadOrCreateClientKeys() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(null, null);
    }

    private void loadServerCertificate(File pub) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        final char[] serverPublicStorePass = "1".toCharArray();

        KeyStore serverPublicStore = KeyStore.getInstance("JKS");
        serverPublicStore.load(new FileInputStream(pub), serverPublicStorePass);

        trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(serverPublicStore);
    }

    public static void main(String... args) {
        System.out.println("Client");

        Client client = new Client();
        try {
            client.runClient();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
