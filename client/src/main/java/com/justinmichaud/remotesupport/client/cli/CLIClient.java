package com.justinmichaud.remotesupport.client.cli;

import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.client.PublicConnection;
import com.justinmichaud.remotesupport.client.tunnel.PeerConnection;
import com.justinmichaud.remotesupport.common.WorkerThreadManager;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import java.util.Set;

/*
 * Simple Command line client to test and debug connection issues
 */
public class CLIClient {

    private final WorkerThreadManager workerThreadManager;
    private final PublicConnection publicConnection;

    public CLIClient() throws ExceptionUDT {
        workerThreadManager = new WorkerThreadManager(null);

        publicConnection = new PublicConnection(
                new InetSocketAddress("172.16.1.216"/*"63.135.27.26"*/, 40000),
                input("What is your username?"), this::connected, this::connectToPartner);
        workerThreadManager.makeGroup("PublicConnection", null).addWorkerThread(publicConnection);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                workerThreadManager.stop();
            }
        });
    }

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Client");

        CLIClient client = new CLIClient();

        while (client.workerThreadManager.isRunning()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        client.workerThreadManager.stop();
        System.out.println("Client closed.");
    }

    private void connected() {
        System.out.println("Connected to public server!");
        String partner = input("Who would you like to connect to?");
        if (!partner.isEmpty()) {
            try {
                publicConnection.connect(partner);
            } catch (IOException e) {
                System.out.println("Error connecting to partner");
                workerThreadManager.stop();
            }
        }
    }

    private void connectToPartner(PublicConnection.Connection c) {
        if (c.isServer && !input("Would you like to grant " + c.ip + ":" + c.port
                + " remote access to your computer?[y/N]").equalsIgnoreCase("y")) return;
        System.out.println("Connecting to " + c.ip + ":" + c.port);

        try {
            runCLI(c.connect());

        } catch (Exception e) {
            e.printStackTrace();
            workerThreadManager.stop();
        }
    }

    public static String input(String prompt) {
        System.out.println(prompt);
        return new Scanner(System.in).nextLine();
    }

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

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (!t.isDaemon()) System.out.println("Running thread: "  +t.getName());
        }
        System.out.println("Connection closed.");
    }

}
