package com.justinmichaud.remotesupport.client;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

// Temporary test client to see what is causing ssh not to work over the tunnel
// Port 5001 -> 22
public class SshTest {

    public static void main(String... args) throws IOException {
        System.out.println("Act as client or server [c/s]?");
        boolean server = new Scanner(System.in).nextLine().equalsIgnoreCase("s");

        if (server) server();
        else client();
    }

    public static void server() throws IOException {
        ServerSocket peerServerSocket = new ServerSocket(5000, 1);
        Socket peerSocket = peerServerSocket.accept();
        System.out.println("Accepted peer connection");

        Socket sshSocket = new Socket("localhost", 22);
        System.out.println("Connected to ssh");

        //ssh -> peer
        new Thread(() -> {
            while (!sshSocket.isClosed() && sshSocket.isConnected()
                    && !peerSocket.isClosed() && peerSocket.isConnected()) {
                try {
                    peerSocket.getOutputStream().write(sshSocket.getInputStream().read());
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!sshSocket.isClosed()) try {
                        sshSocket.close();
                    } catch (IOException e1) {}
                }
            }
        }).start();

        //peer -> ssh
        new Thread(() -> {
            while (!sshSocket.isClosed() && sshSocket.isConnected()
                    && !peerSocket.isClosed() && peerSocket.isConnected()) {
                try {
                    sshSocket.getOutputStream().write(peerSocket.getInputStream().read());
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!sshSocket.isClosed()) try {
                        sshSocket.close();
                    } catch (IOException e1) {}
                }
            }
        }).start();

        while (!sshSocket.isClosed() && sshSocket.isConnected()
                && !peerSocket.isClosed() && peerSocket.isConnected()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        System.out.println("Connection closed.");

        if (!peerSocket.isClosed()) peerSocket.close();
        if (!sshSocket.isClosed()) sshSocket.close();
        if (!peerServerSocket.isClosed()) peerServerSocket.close();
    }

    public static void client() throws IOException {
        Socket peerSocket = new Socket("localhost", 5000);
        System.out.println("Connected peer connection");

        ServerSocket sshServerSocket = new ServerSocket(5001, 1);
        Socket sshSocket = sshServerSocket.accept();
        System.out.println("Accepted from port 5001");

        //ssh -> peer
        new Thread(() -> {
            while (!sshSocket.isClosed() && sshSocket.isConnected()
                    && !peerSocket.isClosed() && peerSocket.isConnected()) {
                try {
                    peerSocket.getOutputStream().write(sshSocket.getInputStream().read());
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!sshSocket.isClosed()) try {
                        sshSocket.close();
                    } catch (IOException e1) {}
                }
            }
        }).start();

        //peer -> ssh
        new Thread(() -> {
            while (!sshSocket.isClosed() && sshSocket.isConnected()
                    && !peerSocket.isClosed() && peerSocket.isConnected()) {
                try {
                    sshSocket.getOutputStream().write(peerSocket.getInputStream().read());
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!sshSocket.isClosed()) try {
                        sshSocket.close();
                    } catch (IOException e1) {}
                }
            }
        }).start();

        while (!sshSocket.isClosed() && sshSocket.isConnected()
                && !peerSocket.isClosed() && peerSocket.isConnected()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }

        System.out.println("Connection closed.");

        if (!peerSocket.isClosed()) peerSocket.close();
        if (!sshSocket.isClosed()) sshSocket.close();
        if (!sshServerSocket.isClosed()) sshServerSocket.close();
    }

}
