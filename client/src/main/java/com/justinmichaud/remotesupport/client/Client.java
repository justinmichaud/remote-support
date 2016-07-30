package com.justinmichaud.remotesupport.client;

import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.common.PeerConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Set;

public class Client {

    // Testing:
    // Connections to port 6000 on server are sent over port 5000 to port 4000 here

    public static void main(String... args) throws GeneralSecurityException, IOException, OperatorCreationException, InterruptedException {
        System.out.println("Client");

        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");

        Socket baseSocket = new NetSocketUDT();
        baseSocket.setKeepAlive(true);
        baseSocket.connect(new InetSocketAddress("localhost", 5000));

        PeerConnection conn = new PeerConnection("client", "server", baseSocket, false);
        conn.openRemoteServerPort(8000, 5900);

        // Gracefully close our connection if the program is killed
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                conn.stop();
            }
        });

        System.out.println("Connected to server!");

        while (conn.isRunning()) {
            Thread.sleep(1000);
        }

        //TODO debugging only
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (!t.isDaemon()) System.out.println("Running thread: "  +t.getName());
        }
        System.out.println("Connection closed.");
    }
}
