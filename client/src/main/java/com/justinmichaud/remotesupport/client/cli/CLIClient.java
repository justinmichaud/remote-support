package com.justinmichaud.remotesupport.client.cli;

import com.justinmichaud.remotesupport.client.SimpleClient;
import com.justinmichaud.remotesupport.client.tunnel.PeerConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import java.util.Set;

/*
 * Simple Command line client to test and debug connection issues
 */
public class CLIClient {

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Client");

        SimpleClient client = new SimpleClient(CLIClient::input, CLIClient::runCLI);

        while (client.isRunning()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        System.out.println("Client closed.");
    }

    public static String input(String prompt) {
        System.out.println(prompt);
        return new Scanner(System.in).nextLine();
    }

    public static void runCLI(PeerConnection conn) {
        System.out.println("Connected");

        try {
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
                } else if (split[0].equalsIgnoreCase("remote-open") && split.length == 3) {
                    conn.openRemoteServerPort(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                } else if (split[0].equalsIgnoreCase("close") && split.length == 2) {
                    conn.serviceManager.getService(Integer.parseInt(split[1])).stop();
                } else if (split[0].equalsIgnoreCase("stop")) {
                    conn.stop();
                } else {
                    continue;
                }

//            System.out.println("Services:");
//            for (Service s : conn.serviceManager.getServices()) {
//                System.out.println(s.id + ": " + s);
//            }
//
//            System.out.println("Threads:");
//            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
//            for (Thread t : threadSet) {
//                if (!t.isDaemon()) System.out.println("Running thread: "  +t.getName());
//            }
            }
        } catch (Exception e) {
            e.printStackTrace();
            conn.stop();
        }

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (!t.isDaemon()) System.out.println("Running thread: "  +t.getName());
        }
        System.out.println("Connection closed.");
    }

}
