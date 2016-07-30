package com.justinmichaud.remotesupport.client;

import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.common.PeerConnection;
import com.justinmichaud.remotesupport.common.Service;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

        // Gracefully close our connection if the program is killed
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                conn.stop();
            }
        });

        System.out.println("Connected to server!");

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
                System.out.println(s.id + ": " + s);
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
