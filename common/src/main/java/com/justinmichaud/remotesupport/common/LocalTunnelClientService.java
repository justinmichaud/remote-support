package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class LocalTunnelClientService extends Service {

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
                while (running) {
                    int b = in.read();
                    if (b < 0) throw new IOException("End of Stream");
                    out.write(b);
                }
            } catch (IOException e) {
                running = false;
            }
        }
    }

    private class ConnectThread implements Runnable {

        private final int port;

        public ConnectThread(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            while (isOpen()) {
                Socket localSocket;
                try {
                    localSocket = new Socket("localhost", port);
                } catch (IOException e) { continue; }

                try {
                    connect(localSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }

                System.out.println("Created new tunneled connection on port " + port + " (id:" + id + ")");

                while (inputThread.isAlive() && outputThread.isAlive()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                }

                System.out.println("Closed tunneled connection (id: " + id + ")");

            }
        }
    }

    public LocalTunnelClientService(int id, ServiceManager serviceManager, int port)
            throws IOException {
        super(id ,serviceManager);

        connectThread = new Thread(new ConnectThread(port));
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
