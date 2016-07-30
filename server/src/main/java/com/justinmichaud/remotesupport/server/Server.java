package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.PeerConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Set;

public class Server {

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException, InterruptedException {
        System.out.println("Server");

        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");

        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        PeerConnection conn = new PeerConnection("server", "client", baseConnection, true);
//        conn.openServerPort(1, 8000, 5900);

        // Gracefully close our connection if the program is killed
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                conn.stop();
            }
        });

        System.out.println("Connected to client!");

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
