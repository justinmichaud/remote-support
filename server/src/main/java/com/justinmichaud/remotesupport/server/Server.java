package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.PeerConnection;
import com.justinmichaud.remotesupport.common.Service;
import com.justinmichaud.remotesupport.common.WorkerThreadManager;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import java.util.Set;

public class Server {

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException, InterruptedException {
        System.out.println("Server");

        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");

        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        PeerConnection conn = new PeerConnection("server", "client", baseConnection, true);

        // Gracefully close our connection if the program is killed
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                conn.stop();
            }
        });

        System.out.println("Connected to client!");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (conn.isRunning()) {
            if (!in.ready()) {
                Thread.sleep(100);
                continue;
            }
            String line = in.readLine();
            String[] split = line.split(" ");
            if (split.length < 1) continue;

            if (split[0].equalsIgnoreCase("open") && split.length == 3) {
                conn.openServerPort(conn.serviceManager.getNextId(),
                        Integer.parseInt(split[1]), Integer.parseInt(split[2]));
            }
            else if (split[0].equalsIgnoreCase("remote-open") && split.length == 3) {
                conn.openRemoteServerPort(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
            }
            else if (split[0].equalsIgnoreCase("close") && split.length == 2) {
                conn.serviceManager.getService(Integer.parseInt(split[1])).stop();
            }
            else if (split[0].equalsIgnoreCase("stop")) {
                conn.stop();
            }
            else {
                continue;
            }

            System.out.println("Services:");
            for (Service s : conn.serviceManager.getServices()) {
                System.out.println(s.id + ": " + s.toString());
            }

            System.out.println("Threads:");
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread t : threadSet) {
                if (!t.isDaemon()) System.out.println("Running thread: "  +t.getName());
            }
        }

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (!t.isDaemon()) System.out.println("Running thread: "  +t.getName());
        }
        System.out.println("Connection closed.");
    }

}
