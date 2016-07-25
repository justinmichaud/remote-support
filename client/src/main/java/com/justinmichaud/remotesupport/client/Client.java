package com.justinmichaud.remotesupport.client;

import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.common.LocalTunnel;
import com.justinmichaud.remotesupport.common.PartnerConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.*;
import java.security.*;

public class Client {

    private static class ConnectRunnable implements Runnable {

        private PartnerConnection peer;

        public ConnectRunnable(PartnerConnection peer) throws IOException {
            this.peer = peer;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    System.out.println("Waiting to create tunneled connection to port 4000");
                    Socket localSocket = new Socket("localhost", 4000);
                    System.out.println("Created new tunneled connection");
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

    public static void main(String... args) throws GeneralSecurityException, IOException, OperatorCreationException, InterruptedException {
        System.out.println("Client");

        Socket baseSocket = new NetSocketUDT();
        baseSocket.setKeepAlive(true);
        baseSocket.connect(new InetSocketAddress("localhost", 5000));
        PartnerConnection conn = new PartnerConnection("client", "server", baseSocket, new File("client_private.jks"),
                new File("client_trusted.jks"), false);

        System.out.println("Connected to server!");

        Thread connectThread = new Thread(new ConnectRunnable(conn));
        connectThread.start();
        connectThread.join();
    }
}
