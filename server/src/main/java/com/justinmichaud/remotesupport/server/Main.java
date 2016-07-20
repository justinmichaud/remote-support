package com.justinmichaud.remotesupport.server;

import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;

import java.net.InetSocketAddress;

public class Main {

    public static void main(String... args) throws ExceptionUDT {
        System.out.println("Hello Server!");

        final SocketUDT socket = new SocketUDT(TypeUDT.STREAM);
        socket.bind(new InetSocketAddress("localhost", 5000));
        socket.listen(1);

        System.out.println("Waiting for connection");
        SocketUDT conn = socket.accept();
        conn.setBlocking(false);
        System.out.println("Connected");

        conn.send("You are connected!".getBytes());

        while (conn.isOpen()) {
            byte[] data = new byte[5];
            int read = 0;
            if ((read = conn.receive(data)) > 0) {
                System.out.print("From client: ");
                conn.send("Echo: ".getBytes());
                do {
                    System.out.println(new String(data, 0, read));
                    conn.send(data, 0, read);
                } while ((read = conn.receive(data)) > 0);
                System.out.println();
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {}
        }

        System.out.println("Closing. isOpen:" + conn.isOpen());
        socket.close();

    }

}
