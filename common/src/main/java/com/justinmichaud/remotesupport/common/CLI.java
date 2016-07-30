package com.justinmichaud.remotesupport.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;

public class CLI {

    // Command line client for testing

    public static void runCLI(PeerConnection conn) throws IOException, InterruptedException {
        System.out.println("Connected");

        // Gracefully close our connection if the program is killed
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                conn.stop();
            }
        });

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
