package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.LocalTunnelClientService;
import com.justinmichaud.remotesupport.common.PeerConnection;
import com.justinmichaud.remotesupport.common.TlsConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;

public class Server {

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException, InterruptedException {
        System.out.println("Server");

        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        PeerConnection conn = new PeerConnection("server", "client", baseConnection, true);
        conn.openPort(1, 6000, 4000);

        System.out.println("Connected to client!");

        while (conn.isOpen()) {
            Thread.sleep(1000);
        }
    }

}
