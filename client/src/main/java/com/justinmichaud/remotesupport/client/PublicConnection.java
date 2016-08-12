package com.justinmichaud.remotesupport.client;

import com.barchart.udt.ErrorUDT;
import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.client.tunnel.TunnelEventHandler;
import com.justinmichaud.remotesupport.common.WorkerThreadManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

public class PublicConnection extends WorkerThreadManager.WorkerThreadPayload {

    private final NetSocketUDT socket;
    private final InetSocketAddress publicAddress;
    private final String username;
    private final TunnelEventHandler eh;

    private BufferedInputStream in;
    private BufferedOutputStream out;

    private boolean nameOk = false;

    public PublicConnection(InetSocketAddress publicAddress, String username, TunnelEventHandler eh)
            throws ExceptionUDT {
        super("Public connection");
        this.publicAddress = publicAddress;
        this.username = username;
        this.eh = eh;
        socket = new NetSocketUDT();
    }

    @Override
    public void start(WorkerThreadManager.WorkerThreadGroup group) throws Exception {
        socket.connect(publicAddress);
        logger.debug("Connected to public server " + publicAddress);
        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());

        send(username);
    }

    @Override
    public void tick() throws Exception {
        String serverResponse = read();

        if (serverResponse.equalsIgnoreCase("ok") && !nameOk) {
            nameOk = true;
            eh.discoveryServerConnected();
        }
        else if (serverResponse.startsWith("name_error") && !nameOk) {
            throw new RuntimeException(serverResponse.substring("name_error".length()));
        }
        else if (serverResponse.startsWith("ok:")) {
            String[] partnerDetails = serverResponse.split(":");
            if (partnerDetails.length != 5 || !partnerDetails[1].equals(username)) {
                throw new RuntimeException("Invalid data from server: " + serverResponse);
            }

            NioPeerConnection.runPeerConnection(false, new InetSocketAddress(socket.getLocalPort()),
                    new InetSocketAddress(partnerDetails[3], Integer.parseInt(partnerDetails[4])), eh,
                    username, partnerDetails[2]);
        }
        else if (serverResponse.startsWith("connect:")) {
            String[] partnerDetails = serverResponse.split(":");
            if (partnerDetails.length != 5 || !partnerDetails[1].equals(username)) {
                throw new RuntimeException("Invalid data from server: " + serverResponse);
            }

            NioPeerConnection.runPeerConnection(true, new InetSocketAddress(socket.getLocalPort()),
                    new InetSocketAddress(partnerDetails[3], Integer.parseInt(partnerDetails[4])), eh,
                    username, partnerDetails[2]);
        }
        else if (serverResponse.equalsIgnoreCase("keepalive")) {
            //TODO keepalive
        }
        else throw new RuntimeException("Invalid data from server: " + serverResponse);
    }

    @Override
    public void stop() throws Exception {
        socket.close();
    }

    public void connect(String partner) throws IOException {
        send("connect:" + partner);
    }

    private void send(String msg) throws IOException {
        byte[] bytes = msg.getBytes();
        if (bytes.length > 255) throw new IllegalArgumentException("Message is too long");

        out.write(bytes.length);
        out.write(bytes);
        out.flush();
    }

    private String read() throws IOException {
        int length = blockingRead();

        StringBuilder buf = new StringBuilder();
        for (int i=0; i< length; i++) buf.append((char) (blockingRead()));

        return buf.toString();
    }

    private int blockingRead() throws IOException {
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

}
