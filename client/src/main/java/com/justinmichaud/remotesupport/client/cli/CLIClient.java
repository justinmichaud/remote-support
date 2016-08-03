package com.justinmichaud.remotesupport.client.cli;

import com.barchart.udt.ErrorUDT;
import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.client.tunnel.PeerConnection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import java.util.Set;

/*
Simple Command line client to test and debug connection issues
 */
public class CLIClient {

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Client");

        NetSocketUDT socket = new NetSocketUDT();
        socket.connect(new InetSocketAddress("172.16.1.216"/*"63.135.27.26"*/, 40000));

        BufferedInputStream in = new BufferedInputStream(socket.getInputStream());

        System.out.println("Connected");
        final String ourName = input("What is your username?");
        send(socket.getOutputStream(), ourName);
        System.out.println(read(in));

        String partner = input("Who would you like to connect to?");
        if (!partner.isEmpty()) {
            send(socket.getOutputStream(), "connect:" + partner);

            String partnerResponse = read(socket.getInputStream());
            if (partnerResponse.startsWith("ok:")) {
                String[] partnerDetails = partnerResponse.split(":");
                if (partnerDetails.length != 3) {
                    socket.close();
                    throw new RuntimeException("Invalid data from server");
                }

                connectToPartner(ourName, partner, partnerDetails[1],
                        Integer.parseInt(partnerDetails[2]), socket, false);
            }
            else System.out.println("Error: " + partnerResponse);
        }

        while (!socket.isClosed() && socket.isConnected()) {
            String value = read(in);

            if (value.startsWith("connect:")) {
                String[] partnerDetails = value.split(":");
                if (partnerDetails.length != 4) {
                    socket.close();
                    throw new RuntimeException("Invalid data from server");
                }

                connectToPartner(ourName, partnerDetails[1], partnerDetails[2],
                        Integer.parseInt(partnerDetails[3]), socket, true);
            }
            else System.out.println(value);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        if (!socket.isClosed()) socket.close();
    }

    private static void connectToPartner(String ourName, String partnerName, String ip, int port,
                                         NetSocketUDT existingConnection, boolean isServer)
            throws IOException, GeneralSecurityException, OperatorCreationException {
        if (isServer && !input("Would you like to grant " + ip + ":" + port
                + " remote access to your computer?[y/N]").equalsIgnoreCase("y")) return;
        System.out.println("Connecting to " + ip + ":" + port);

        int existingPort = existingConnection.socketUDT().getLocalInetPort();
        existingConnection.close();

        NetSocketUDT socket = new NetSocketUDT();
        socket.socketUDT().setRendezvous(true);
        socket.socketUDT().bind(new InetSocketAddress(existingPort));

        for (int i=0; i<=5; i++) {
            try {
                socket.connect(new InetSocketAddress(ip, port));
                break;
            } catch (IOException e) {
                if (i == 5) {
                    e.printStackTrace();
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {}
            }
        }

        System.out.println("Connected to " + socket.getInetAddress() + ":" + socket.getPort());
        try {
            runCLI(new PeerConnection(ourName, partnerName, socket, isServer));
        } catch (InterruptedException e) {}
        System.out.println("Closed.");
    }

    public static void send(OutputStream out, String msg) throws IOException {
        byte[] bytes = msg.getBytes();
        if (bytes.length > 255) throw new IllegalArgumentException("Message is too long");

        out.write(bytes.length);
        out.write(bytes);
    }

    public static String read(InputStream in) throws IOException {
        int length = blockingRead(in);

        StringBuilder buf = new StringBuilder();
        for (int i=0; i< length; i++) buf.append((char) (blockingRead(in)));

        return buf.toString();
    }

    private static int blockingRead(InputStream in) throws IOException {
        int value = -1;
        while (value < 0) {
            try {
                value = in.read()&0xFF;
            } catch (ExceptionUDT e) {
                if (e.getError() != ErrorUDT.ETIMEOUT) throw e;
                value = -1;
            }
        }
        return value;
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
