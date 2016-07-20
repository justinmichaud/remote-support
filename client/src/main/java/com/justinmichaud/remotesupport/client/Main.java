package com.justinmichaud.remotesupport.client;

import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;

import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String... args) throws ExceptionUDT {
        System.out.println("Hello Client!");

        // Create a reliable udp connection
        final SocketUDT socket = new SocketUDT(TypeUDT.STREAM);
        socket.connect(new InetSocketAddress("localhost", 5000));

        System.out.println("Connected");
        socket.setBlocking(false);

        final BlockingQueue<String> userInput = new LinkedBlockingQueue<>();
        final Thread userInputThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Scanner in = new Scanner(System.in);
                String line;

                while (!(line = in.nextLine()).isEmpty() && socket.isOpen()) {
                    try {
                        userInput.put(line);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }

                try {
                    socket.close();
                } catch (ExceptionUDT exceptionUDT) {
                    exceptionUDT.printStackTrace();
                }

                in.close();
            }
        });
        userInputThread.start();

        while (socket.isOpen()) {
            if (userInput.peek() != null) {
                socket.send(userInput.remove().getBytes());
            }

            byte[] data = new byte[5];
            int read = 0;
            if ((read = socket.receive(data)) > 0) {
                System.out.print("From server: ");
                do {
                    for (int i=0; i<read; i++)
                        System.out.print((char) data[i]);
                } while ((read = socket.receive(data)) > 0);
                System.out.println();
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {}
        }

        System.out.println("Closing. isOpen:" + socket.isOpen());
        userInputThread.stop();
        socket.close();
    }

}
