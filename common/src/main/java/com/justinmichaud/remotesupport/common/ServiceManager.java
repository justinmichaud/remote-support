package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class ServiceManager {
    // Magic values for control packets for out datastreams
    public final static int MAGIC_DATA = 0;

    private final PeerConnection peerConnection;
    private final Socket baseSocket;

    private final HashMap<Integer, Service> services = new HashMap<>();

    private final Thread readThread, writeThread;

    // Thread to read data from the tunnel and send it to services
    private class ReadThread implements Runnable {

        private InputStream in;

        public ReadThread(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            while (!baseSocket.isClosed()) {
                try {
                    int magic = in.read();
                    if (magic == MAGIC_DATA) {
                        int id = in.read();
                        int length = (in.read()&0xFF << 8) | (in.read()&0xFF);

                        getService(id).readDataFromTunnel(length, in);
                    }
                    else {
                        throw new IOException("Unknown magic byte " + magic);
                    }
                } catch (IOException e) {
                    System.out.println("Error attempting to write service control data - closing connection");
                    e.printStackTrace();
                    try {
                        baseSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    // Thread to read data from services and write it to the tunnel
    private class WriteThread implements Runnable {

        private OutputStream out;
        private ArrayList<Integer> servicesToRemove = new ArrayList<Integer>();

        public WriteThread(OutputStream out) {
            this.out = out;
        }

        @Override
        public void run() {
            while (!baseSocket.isClosed()) {
                try {
                    synchronized (services) {
                        for (Service s : services.values()) {
                            if (!s.isOpen()) servicesToRemove.add(s.id);
                            else s.writeDataToTunnel(out);
                        }

                        for (int id : servicesToRemove) {
                            System.out.println("Service " + id + " was closed - removing");
                            peerConnection.closeService(id);
                        }
                        servicesToRemove.clear();
                    }
                } catch (IOException e) {
                    System.out.println("Error attempting to read service control data - closing connection");
                    e.printStackTrace();
                    try {
                        baseSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    public ServiceManager(PeerConnection peerConnection, Socket baseSocket) throws IOException {
        this.peerConnection = peerConnection;
        this.baseSocket = baseSocket;

        readThread = new Thread(new ReadThread(baseSocket.getInputStream()));
        readThread.setDaemon(true);
        readThread.start();

        writeThread = new Thread(new WriteThread(baseSocket.getOutputStream()));
        writeThread.setDaemon(true);
        writeThread.start();
    }

    public Iterable<Service> getServices() {
        return services.values();
    }

    public Service getService(int id) {
        return services.get(id);
    }

    public Service addService(Service s) throws IOException {
        System.out.println("Adding local service " + s.id);
        synchronized (services) {
            if (services.containsKey(s.id)) {
                System.out.println("Warning: overwriting existing service!");
                removeService(s.id);
            }
            services.put(s.id, s);
            return s;
        }
    }

    public void removeService(int id) throws IOException {
        System.out.println("Removing local service " + id);
        synchronized (services) {
            getService(id).close();
            services.remove(id);
        }
    }
}
