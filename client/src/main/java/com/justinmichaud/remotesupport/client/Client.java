package com.justinmichaud.remotesupport.client;

import com.barchart.udt.ErrorUDT;
import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.net.NetSocketUDT;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    public static void main(String... args) throws IOException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Client");

        Socket socket = new NetSocketUDT();
        socket.connect(new InetSocketAddress("localhost", 5000));

        BufferedInputStream in = new BufferedInputStream(socket.getInputStream());

        System.out.println("Connected");
        send(socket.getOutputStream(), input("What is your username?"));
        System.out.println(read(in));

        String partner = input("Who would you like to connect to?");
        if (!partner.isEmpty()) send(socket.getOutputStream(), "connect: " + partner);

        while (!socket.isClosed() && socket.isConnected()) {
            if (in.available() > 0) {
                System.out.println("Tick");
                String value = read(in);
                System.out.println(value);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        if (!socket.isClosed()) socket.close();
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
