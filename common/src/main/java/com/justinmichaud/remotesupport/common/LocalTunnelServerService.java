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
                while(running) {
                    int b = in.read();
                    if (b < 0) throw new IOException("End of Stream");
                    out.write(b);
                }
            } catch (IOException e) {
                e.printStackTrace();
                running = false;
            }
        }
    }

    private class AcceptThread implements Runnable {

        private final int localPort, remotePort;
        private final ServerSocket localServer;

        public AcceptThread(int localPort, int remotePort) throws IOException {
            this.localPort = localPort;
            this.remotePort = remotePort;
            localServer = new ServerSocket(localPort);
        }

        @Override
        public void run() {
            while (isOpen()) {
                Socket localSocket;
                try {
                    localSocket = localServer.accept();
                } catch (IOException e) { continue; }

                try {
                    serviceManager.getControlService().requestPeerOpenPort(LocalTunnelServerService.this, remotePort);
                    connect(localSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }

                System.out.println("Accepted new tunneled connection on port " + localPort + " (id: " + id + ")");

                while (inputThread.isAlive() && outputThread.isAlive()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                }

                try {
                    serviceManager.getControlService().requestPeerCloseService(LocalTunnelServerService.this.id);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println("Closed tunneled connection (id: " + id + ")");
            }
            System.out.println("Server Tunnel accept thread closed (id: " + id + ")");
        }
    }

    public LocalTunnelServerService(int id, ServiceManager serviceManager, int localPort, int remotePort)
            throws IOException {
        super(id, serviceManager);

        connectThread = new Thread(new AcceptThread(localPort, remotePort));
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
