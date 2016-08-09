package com.justinmichaud.remotesupport.client.tunnel;

import com.barchart.udt.ExceptionUDT;
import com.justinmichaud.remotesupport.client.tunnel.PeerConnection;
import com.justinmichaud.remotesupport.client.tunnel.PublicConnection;
import com.justinmichaud.remotesupport.common.WorkerThreadManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleClient {

    private final WorkerThreadManager workerThreadManager;
    private final PublicConnection publicConnection;
    private final Function<String, String> prompter;
    private Consumer<PeerConnection> connectedCallback;

    public SimpleClient(Function<String, String> prompter,
                        Consumer<PeerConnection> connectedCallback, InetSocketAddress addr) throws ExceptionUDT {
        this.prompter = prompter;
        this.connectedCallback = connectedCallback;
        workerThreadManager = new WorkerThreadManager(null);

        publicConnection = new PublicConnection(addr,
                prompter.apply("What is your username?"), this::connected, this::connectToPartner, prompter);
        workerThreadManager.makeGroup("PublicConnection", null).addWorkerThread(publicConnection);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                workerThreadManager.stop();
            }
        });
    }

    private void connected() {
        System.out.println("Connected to public server!");
        String partner = prompter.apply("Who would you like to connect to? Leave empty if nobody.");
        if (!partner.isEmpty()) {
            try {
                publicConnection.connect(partner);
            } catch (IOException e) {
                System.out.println("Error connecting to partner");
                workerThreadManager.stop();
            }
        }
    }

    private void connectToPartner(PublicConnection.Connection c) {
        if (c.isServer && !prompter.apply("Would you like to grant " + c.ip + ":" + c.port
                + " remote access to your computer?[y/N]").equalsIgnoreCase("y")) return;
        System.out.println("Connecting to " + c.ip + ":" + c.port);

        try {
            PeerConnection p = c.connect();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    p.stop();
                }
            });

            connectedCallback.accept(p);
            stop();
        } catch (Exception e) {
            e.printStackTrace();
            workerThreadManager.stop();
        }
    }

    public boolean isRunning() {
        return workerThreadManager.isRunning();
    }

    public void stop() {
        workerThreadManager.stop();
    }
}
