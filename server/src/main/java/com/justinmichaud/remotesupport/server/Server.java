package com.justinmichaud.remotesupport.server;

import com.barchart.udt.ErrorUDT;
import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.CircularByteBuffer;
import com.justinmichaud.remotesupport.common.InputOutputStreamPipePayload;
import com.justinmichaud.remotesupport.common.WorkerThreadManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static class AcceptThread extends WorkerThreadManager.WorkerThreadPayload {

        private final ServerSocket serverSocket;
        private WorkerThreadManager threadManager;
        private final ConcurrentHashMap<String, ConnectionThread> mapping = new ConcurrentHashMap<>();

        public AcceptThread(ServerSocket serverSocket) {
            super("Accept Thread");
            this.serverSocket = serverSocket;
        }

        @Override
        public void start(WorkerThreadManager.WorkerThreadGroup group) {
            threadManager = group.getThreadManager();
        }

        @Override
        public void tick() throws Exception {
            Socket s = serverSocket.accept();
            if (s == null) return;

            System.out.println("New connection from " + s.getInetAddress() + ":" + s.getPort());

            threadManager.makeGroup("Connection " + s.getInetAddress() + ":" + s.getPort(), null)
                    .addWorkerThread(new ConnectionThread(s, mapping));
        }
    }

    private static class ConnectionThread extends WorkerThreadManager.WorkerThreadPayload {
        private String name;
        private boolean nameValid = false;

        private final CircularByteBuffer inBuffer = new CircularByteBuffer();
        private final CircularByteBuffer outBuffer = new CircularByteBuffer();
        private final ConcurrentHashMap<String, ConnectionThread> mapping;
        private final Socket socket;

        private long lastKeepalive = 0;

        public ConnectionThread(Socket socket, ConcurrentHashMap<String, ConnectionThread> mapping) {
            super("Connection");
            this.mapping = mapping;
            this.socket = socket;
        }

        @Override
        public void start(WorkerThreadManager.WorkerThreadGroup group) throws IOException {
            group.addWorkerThread(new InputOutputStreamPipePayload(socket.getInputStream(),
                    inBuffer.getOutputStream()));
            group.addWorkerThread(new InputOutputStreamPipePayload(outBuffer.getInputStream(),
                    socket.getOutputStream()));

            name = read();

            //TODO verify user identity
            if (mapping.containsKey(name)) {
                send(socket.getOutputStream(), "name_error: There is already a connected user with this name");

                throw new IllegalArgumentException("There is already a connected user with this name: "
                        + name);
            }
            nameValid = true;
            send("ok");

            mapping.put(name, this);
            System.out.println("New user connected with name " + name + ": " + socket.getInetAddress()
                    + ":" + socket.getPort());
        }

        @Override
        public void tick() throws Exception {
            //TODO two-way keepalive
            if (System.nanoTime() - lastKeepalive > 3*1E9) {
                send("keepalive");
                lastKeepalive = System.nanoTime();
            }

            if (getInputStream().available() > 0) {
                String read = read();
                if (read.startsWith("connect:")) {
                    String username = read.substring("connect:".length());
                    System.out.println(name + " is attempting to connect to " + username);

                    if (mapping.containsKey(username)) {
                        // Start NAT traversal
                        ConnectionThread partner = mapping.get(username);
                        send("ok:" + name + ":" + username + ":" + partner.socket.getInetAddress().getHostAddress()
                                + ":" + partner.socket.getPort());
                        send(partner.getOutputStream(), "connect:" + username + ":" + name + ":" +
                                socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                    }
                    else {
                        send("offline");
                    }
                }
            }

            Thread.sleep(100);
        }

        @Override
        public void stop() throws IOException {
            if (nameValid)  {
                mapping.remove(name);
                System.out.println("User " + name + " Disconnected");
            }
            else System.out.println("User disconnected before name could be verified");

            if (!socket.isClosed()) {
                socket.close();
            }
            //Remove any possible duplicates, just in case
            while (mapping.values().remove(this));
        }

        public void send(String msg) throws IOException {
            send(getOutputStream(), msg);
        }

        public void send(OutputStream out, String msg) throws IOException {
            byte[] bytes = msg.getBytes();
            if (bytes.length > 255) throw new IllegalArgumentException("Message is too long");

            out.write(bytes.length);
            out.write(bytes);
        }

        public String read() throws IOException {
            int length = blockingRead();

            StringBuilder buf = new StringBuilder();
            for (int i=0; i< length; i++) buf.append((char) (blockingRead()));

            return buf.toString();
        }

        private int blockingRead() throws IOException {
            int value = -1;
            while (value < 0) {
                try {
                    value = getInputStream().read()&0xFF;
                } catch (ExceptionUDT e) {
                    if (e.getError() != ErrorUDT.ETIMEOUT) throw e;
                    value = -1;
                }
            }
            return value;
        }

        private InputStream getInputStream() {
            return inBuffer.getInputStream();
        }

        private OutputStream getOutputStream() {
            return outBuffer.getOutputStream();
        }
    }

    public static void main(String... args) throws IOException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        System.out.println("Server");

        final ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.setSoTimeout(100);
        serverSocket.bind(new InetSocketAddress(40000), 5);

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

        final WorkerThreadManager.WorkerThreadGroup controlGroup
                = threadManager.makeGroup("Server Control", threadManager::stop);

        controlGroup.addWorkerThread(new AcceptThread(serverSocket));

        System.out.println("Started Server");
        while (!serverSocket.isClosed() && threadManager.isRunning()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
        }

        System.out.println("Server closed");
    }

}
