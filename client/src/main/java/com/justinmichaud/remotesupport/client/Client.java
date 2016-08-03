package com.justinmichaud.remotesupport.client;

import com.barchart.udt.ErrorUDT;
import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;
import com.barchart.udt.net.NetServerSocketUDT;
import com.barchart.udt.net.NetSocketUDT;
import com.barchart.udt.nio.KindUDT;
import com.justinmichaud.remotesupport.common.CLI;
import com.justinmichaud.remotesupport.common.PeerConnection;
import com.sun.corba.se.spi.activation.Server;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Scanner;

public class Client {

    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Client");

        NetSocketUDT socket = new NetSocketUDT();
        socket.connect(new InetSocketAddress("63.135.27.26", 40000));

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
                if (partnerDetails.length != 3) {
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
                + " to have remote access to your computer?").equalsIgnoreCase("y")) return;
        System.out.println("Connect to " + ip + ":" + port + ". Server: " + isServer);

        int existingPort = existingConnection.socketUDT().getLocalInetPort();
        System.out.println("Listen on " + existingPort);
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
            CLI.runCLI(new PeerConnection(ourName, partnerName, socket, isServer));
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

}
