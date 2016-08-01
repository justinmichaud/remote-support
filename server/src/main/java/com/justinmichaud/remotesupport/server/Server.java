package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.WorkerThreadManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    public static void main(String... args) throws IOException {
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

        final ConcurrentHashMap<String, InetAddress> mapping = new ConcurrentHashMap<>();

        final WorkerThreadManager.WorkerThreadGroup controlGroup
                = threadManager.makeGroup("Server Control", threadManager::stop);

        controlGroup.addWorkerThread(new WorkerThreadManager.WorkerThreadPayload("Accept Thread") {
            @Override
            public void tick() throws Exception {
                Socket s = serverSocket.accept();
                if (s == null) return;

                System.out.println("New connection from " + s.getInetAddress());

                threadManager.makeGroup("Connection " + s.getInetAddress().toString(), () -> {
                    while (mapping.values().remove(s.getInetAddress()));
                    try {
                        s.close();
                    } catch (IOException e) {}
                }).addWorkerThread(new WorkerThreadManager.WorkerThreadPayload("Connection") {
                    final StringBuilder name = new StringBuilder();
                    boolean nameRead = false;

                    @Override
                    public void start(WorkerThreadManager.WorkerThreadGroup group) throws IOException {
                        InputStream in = s.getInputStream();
                        int length = s.getInputStream().read()&0xFF;

                        for (int i=0; i< length; i++) name.append((char) (in.read()&0xFF));

                        //TODO verify user identity
                        if (mapping.containsKey(name.toString())) {
                            send("error: There is already a connected user with this name");

                            throw new IllegalArgumentException("There is already a connected user with this name: "
                                    + name);
                        }
                        nameRead = true;

                        mapping.put(name.toString(), s.getInetAddress());
                        System.out.println("New user connected with name " + name + ": " + s.getInetAddress());
                    }

                    @Override
                    public void tick() throws Exception {
                        if (s.isClosed() || !s.isConnected()) throw new IOException("Socket closed");
                        send("keepalive");
                        Thread.sleep(1000);
                    }

                    @Override
                    public void stop() {
                        if (nameRead)  {
                            mapping.remove(name.toString());
                            System.out.println("User " + name + " Disconnected");
                        }
                        else System.out.println("User disconnected before name could be verified");
                    }

                    public void send(String msg) throws IOException {
                        byte[] bytes = msg.getBytes();
                        if (bytes.length > 255) throw new IllegalArgumentException("Message is too long");

                        s.getOutputStream().write(bytes.length);
                        s.getOutputStream().write(bytes);
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
