package com.justinmichaud.remotesupport.client;

import com.barchart.udt.ErrorUDT;
import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;
import com.barchart.udt.net.NetServerSocketUDT;
import com.barchart.udt.net.NetSocketUDT;
import com.sun.corba.se.spi.activation.Server;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    public static void main(String... args) throws IOException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Client");

        NetSocketUDT socket = new NetSocketUDT();
        socket.connect(new InetSocketAddress("63.135.27.26", 40000));

        BufferedInputStream in = new BufferedInputStream(socket.getInputStream());

        System.out.println("Connected");
        send(socket.getOutputStream(), input("What is your username?"));
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

                socket.close(); //Make way for our new connection
                connectToPartner(partnerDetails[1], Integer.parseInt(partnerDetails[2]), socket);
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

                acceptFromPartner(partnerDetails[1], Integer.parseInt(partnerDetails[2]), socket);
            }
            else System.out.println(value);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        if (!socket.isClosed()) socket.close();
    }

    private static void acceptFromPartner(String ip, int port, NetSocketUDT existingConnection) throws IOException {
        if (!input("Would you like to grant " + ip + ":" + port
                + " to have remote access to your computer?").equalsIgnoreCase("y")) return;

        //Re-use the existing connection so it passes through the nat
        int existingPort = existingConnection.socketUDT().getLocalInetPort();
        existingConnection.close();

        System.out.println("Accept from " + ip + ":" + port + " on " + existingPort);
        ServerSocket serverSocket = new NetServerSocketUDT();

        serverSocket.bind(new InetSocketAddress(existingPort));
        Socket socket = serverSocket.accept();

        System.out.println("Accepted from " + socket.getInetAddress() + ":" + socket.getPort());
        send(socket.getOutputStream(), "Hello from acceptor!");
        while (!socket.isClosed() && socket.isConnected()) {
            System.out.print(read(socket.getInputStream()));
        }
        System.out.println("Closed.");
    }

    private static void connectToPartner(String ip, int port, NetSocketUDT existingConnection) throws IOException {
        System.out.println("Connect to " + ip + ":" + port);

        int existingPort = existingConnection.socketUDT().getLocalInetPort();
        existingConnection.close();

        NetSocketUDT socket = new NetSocketUDT();
        socket.socketUDT().bind(new InetSocketAddress(existingPort));

        for (int i=0; i<25; i++) {
            try {
                socket.connect(new InetSocketAddress(ip, port));
                break;
            } catch (IOException e) {
                if (i == 24) {
                    e.printStackTrace();
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {}
            }
        }

        System.out.println("Connected to " + socket.getInetAddress() + ":" + socket.getPort());
        send(socket.getOutputStream(), "Hello from connector!");
        while (!socket.isClosed() && socket.isConnected()) {
            System.out.print(read(socket.getInputStream()));
        }
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
