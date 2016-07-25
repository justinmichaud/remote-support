package com.justinmichaud.remotesupport.client;

import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.common.Connection;
import org.bouncycastle.operator.OperatorCreationException;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;

public class Client {

    public static void main(String... args) throws GeneralSecurityException, IOException, OperatorCreationException {
        System.out.println("Client");

        Socket baseSocket = new NetSocketUDT();
        baseSocket.setKeepAlive(true);
        baseSocket.connect(new InetSocketAddress("localhost", 5000));
        Connection conn = new Connection("client", "server", baseSocket, new File("client_private.jks"),
                new File("client_trusted.jks"), false);

        conn.getOutputStream().write("You are connected to the client!\n".getBytes());

        InputStream inputstream = System.in;
        InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
        BufferedReader bufferedreader = new BufferedReader(inputstreamreader);

        OutputStream outputstream = conn.getOutputStream();
        OutputStreamWriter outputstreamwriter = new OutputStreamWriter(outputstream);
        BufferedWriter bufferedwriter = new BufferedWriter(outputstreamwriter);

        String string;
        while ((string = bufferedreader.readLine()) != null) {
            bufferedwriter.write(string + '\n');
            bufferedwriter.flush();
        }
    }
}
