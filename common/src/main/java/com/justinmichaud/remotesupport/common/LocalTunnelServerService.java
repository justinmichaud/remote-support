package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class LocalTunnelServerService extends Service {

    private PipeRunnable inRunnable, outRunnable;
    private Thread inputThread, outputThread, connectThread;

    private class PipeRunnable implements Runnable {

        public volatile boolean running = true;

        private InputStream in;
        private OutputStream out;

        public PipeRunnable(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                while (running) out.write(in.read());
            } catch (IOException e) {
                running = false;
            }
        }
    }

    private class AcceptThread implements Runnable {

        private final int port;
        private final ServerSocket localServer;

        public AcceptThread(int port) throws IOException {
            this.port = port;
            localServer = new ServerSocket(port);
        }

        @Override
        public void run() {
            while (isOpen()) {
                Socket localSocket;
                try {
                    localSocket = localServer.accept();
                } catch (IOException e) { continue; }

                try {
                    connect(localSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }

                System.out.println("Accepted new tunneled connection on port " + port + " (id: " + id + ")");

                while (inputThread.isAlive() && outputThread.isAlive()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                }

                System.out.println("Closed tunneled connection (id: " + id + ")");

            }
        }
    }

    public LocalTunnelServerService(int id, int port)
            throws IOException {
        super(id);

        connectThread = new Thread(new AcceptThread(port));
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void connect(Socket local) throws IOException {
        closeConnection();

        inputThread = new Thread(new PipeRunnable(local.getInputStream(), super.getOutputStream()));
        inputThread.setDaemon(true);
        outputThread = new Thread(new PipeRunnable(super.getInputStream(), local.getOutputStream()));
        outputThread.setDaemon(true);

        inputThread.start();
        outputThread.start();
    }

    public void closeConnection() {
        if (inRunnable != null) {
            while (inputThread.isAlive()) inRunnable.running = false;
            inRunnable = null;
            inputThread = null;
        }
        if (outRunnable != null) {
            while (outputThread.isAlive()) outRunnable.running = false;
            outRunnable = null;
            outputThread = null;
        }
    }

    public void close() throws IOException {
        closeConnection();
        super.close();
    }
}
