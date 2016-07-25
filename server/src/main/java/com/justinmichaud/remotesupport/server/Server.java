package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.LocalTunnel;
import com.justinmichaud.remotesupport.common.PartnerConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;

public class Server {

    // Testing only
    // Connections to port 6000 are sent over port 5000 to Client.java

    private static class AcceptRunnable implements Runnable {

        private PartnerConnection peer;
        private ServerSocket localServer;

        public AcceptRunnable(PartnerConnection peer) throws IOException {
            this.peer = peer;
            localServer = new ServerSocket(6000);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    System.out.println("Waiting to accept tunneled connection from port 6000");
                    Socket localSocket = localServer.accept();
                    System.out.println("Accepted new tunneled connection");
                    LocalTunnel tunnel = new LocalTunnel(localSocket.getInputStream(), localSocket.getOutputStream(),
                            peer.getInputStream(), peer.getOutputStream());
                    while (!localSocket.isClosed()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.println("Closed tunneled connection");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException, InterruptedException {
        System.out.println("Server");

        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        PartnerConnection conn = new PartnerConnection("server", "client", baseConnection, new File("server_private.jks"),
                new File("server_trusted.jks"), true);

        System.out.println("Connected to client");

        Thread acceptThread = new Thread(new AcceptRunnable(conn));
        acceptThread.start();
        acceptThread.join();
    }

}
