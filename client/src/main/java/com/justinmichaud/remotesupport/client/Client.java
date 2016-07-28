package com.justinmichaud.remotesupport.client;

import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.common.LocalTunnelClientService;
import com.justinmichaud.remotesupport.common.PeerConnection;
import com.justinmichaud.remotesupport.common.TlsConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.*;
import java.security.*;

public class Client {

    // Testing:
    // Connections to port 6000 on server are sent over port 5000 to port 4000 here

    public static void main(String... args) throws GeneralSecurityException, IOException, OperatorCreationException, InterruptedException {
        System.out.println("Client");

        Socket baseSocket = new NetSocketUDT();
        baseSocket.setKeepAlive(true);
        baseSocket.connect(new InetSocketAddress("localhost", 5000));

        PeerConnection conn = new PeerConnection("client", "server", baseSocket, false);

        System.out.println("Connected to server!");

        while (conn.isRunning()) {
            Thread.sleep(1000);
        }

        System.out.println("Connection closed.");
    }
}
