package com.justinmichaud.remotesupport.client;

import com.barchart.udt.net.NetSocketUDT;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Client {

    public static void main(String... args) throws IOException {
        System.out.println("Client");

        Socket socket = new NetSocketUDT();
        socket.connect(new InetSocketAddress("localhost", 5000));

        System.out.println("Connected");
        send(socket, "MyUsername");

        while (!socket.isClosed() && socket.isConnected()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        if (!socket.isClosed()) socket.close();
    }

    public static void send(Socket s, String msg) throws IOException {
        byte[] bytes = msg.getBytes();
        if (bytes.length > 255) throw new IllegalArgumentException("Message is too long");

        s.getOutputStream().write(bytes.length);
        s.getOutputStream().write(bytes);
    }

}
