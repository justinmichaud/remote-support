package com.justinmichaud.remotesupport.server;

import com.barchart.udt.ErrorUDT;
import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.WorkerThreadManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    public static void main(String... args) throws IOException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Server");

        final ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.setSoTimeout(100);
        serverSocket.bind(new InetSocketAddress(5000), 5);

        final WorkerThreadManager threadManager = new WorkerThreadManager(() -> {
            if (!serverSocket.isClosed()) try {
                serverSocket.close();
            } catch (IOException e) {}
        });

        // Gracefully close our connection if the program is killed
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                threadManager.stop();
            }
        });

        final ConcurrentHashMap<String, Socket> mapping = new ConcurrentHashMap<>();

        final WorkerThreadManager.WorkerThreadGroup controlGroup
                = threadManager.makeGroup("Server Control", threadManager::stop);

        controlGroup.addWorkerThread(new WorkerThreadManager.WorkerThreadPayload("Accept Thread") {
            @Override
            public void tick() throws Exception {
                Socket s = serverSocket.accept();
                if (s == null) return;
                final InputStream in = s.getInputStream();

                System.out.println("New connection from " + s.getInetAddress());

                threadManager.makeGroup("Connection " + s.getInetAddress().toString(), () -> {
                    while (mapping.values().remove(s));
                    try {
                        s.close();
                    } catch (IOException e) {}
                }).addWorkerThread(new WorkerThreadManager.WorkerThreadPayload("Connection") {
                    private String name;
                    private boolean nameValid = false;

                    private long lastKeepalive = 0;

                    @Override
                    public void start(WorkerThreadManager.WorkerThreadGroup group) throws IOException {
                        name = read(in);

                        //TODO verify user identity
                        if (mapping.containsKey(name)) {
                            send(s.getOutputStream(), "error: There is already a connected user with this name");

                            throw new IllegalArgumentException("There is already a connected user with this name: "
                                    + name);
                        }
                        nameValid = true;
                        send(s.getOutputStream(), "ok");

                        mapping.put(name, s);
                        System.out.println("New user connected with name " + name + ": " + s.getInetAddress()
                                + ":" + s.getPort());
                    }

                    @Override
                    public void tick() throws Exception {
                        if (s.isClosed() || !s.isConnected()) throw new IOException("Socket closed");
                        //Barchart UDT only notifies us of broken connections when we try to send something
                        if (System.nanoTime() - lastKeepalive > 1*1E9) {
                            System.out.println("Keepalive");
                            send(s.getOutputStream(), "keepalive");
                            lastKeepalive = System.nanoTime();
                        }

                        if (in.available() > 0) {
                            String read = read(in);
                            if (read.startsWith("connect:")) {
                                String username = read.substring("connect:".length());

                                if (mapping.containsKey(username)) {
                                    // Start NAT traversal
                                    Socket partner = mapping.get(username);
                                    send(s.getOutputStream(), "ok:" + partner.getInetAddress()
                                            + ":" + partner.getPort());
                                    send(partner.getOutputStream(), "connect:" + partner.getInetAddress()
                                            + ":" + partner.getPort());
                                }
                                else {
                                    send(s.getOutputStream(), "offline");
                                }
                            }
                        }

                        Thread.sleep(100);
                    }

                    @Override
                    public void stop() {
                        if (nameValid)  {
                            mapping.remove(name.toString());
                            System.out.println("User " + name + " Disconnected");
                        }
                        else System.out.println("User disconnected before name could be verified");
                    }

                    public void send(OutputStream out, String msg) throws IOException {
                        byte[] bytes = msg.getBytes();
                        if (bytes.length > 255) throw new IllegalArgumentException("Message is too long");

                        out.write(bytes.length);
                        out.write(bytes);
                    }

                    public String read(InputStream in) throws IOException {
                        int length = blockingRead(in);

                        StringBuilder buf = new StringBuilder();
                        for (int i=0; i< length; i++) buf.append((char) (blockingRead(in)));

                        return buf.toString();
                    }

                    private int blockingRead(InputStream in) throws IOException {
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
                });
            }
        });

        System.out.println("Started Server");
        while (!serverSocket.isClosed() && threadManager.isRunning()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
        }

        System.out.println("Server closed");
    }

}
