package com.justinmichaud.remotesupport.client;

import com.justinmichaud.remotesupport.client.discovery.DiscoveryClientHandler;
import com.justinmichaud.remotesupport.client.tunnel.Service;
import com.justinmichaud.remotesupport.client.tunnel.ServiceManager;
import io.netty.channel.ChannelFuture;

import java.security.cert.X509Certificate;
import java.util.Scanner;
import java.util.concurrent.RejectedExecutionException;

public class ConnectionEventHandler {

    public DiscoveryClientHandler discoveryClient;
    public ServiceManager serviceManager;

    private ChannelFuture peerChannelFuture;

    public void onDiscoveryServerConnected(DiscoveryClientHandler discoveryClient) {
        System.out.println("Discovery server connected");
        this.discoveryClient = discoveryClient;
    }

    public void onDiscoveryServerNameAuthenticated() {
        System.out.println("Your name is valid");
        String partner = prompt("Who would you like to connect to? Leave empty if nobody.");
        if (!partner.isEmpty()) {
            connectToPeer(partner);
        }
    }

    public void connectToPeer(String username) {
        if (discoveryClient == null) throw new IllegalStateException("Discovery server is not connected yet");
        discoveryClient.connect(username);
    }

    public void close() {
        log("Event handler asked to close");
        if (discoveryClient != null && discoveryClient.channel != null)
            discoveryClient.channel.close();
        if (serviceManager != null)
            serviceManager.close();
        else if (peerChannelFuture != null) {
            log("Closing before peer connection established");
            try {
                peerChannelFuture.channel().close().syncUninterruptibly();
            } catch (RejectedExecutionException e) {}
        }
    }

    public void onDiscoveryConnectionClosed() {
        System.out.println("Discovery connection closed");
        discoveryClient = null;
    }

    public void onConnectingToPeer(ChannelFuture f) {
        System.out.println("Connecting to peer");
        this.peerChannelFuture = f;
    }

    public void onPeerConnected(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    public void serviceOpen(Service service) {
        System.out.println("Service " + service.name + ":" + service.id + " open.");
    }

    public void serviceClosed(Service service) {
        System.out.println("Service " + service.name + ":" + service.id + " closed.");
    }

    public void onPeerConnectionClosed() {
        System.out.println("Peer Connection closed.");
    }

    protected String prompt(String msg) {
        System.out.println("Prompt: " + msg);
        return new Scanner(System.in).nextLine();
    }

    public void onTrustStart(X509Certificate ourCert, String fingerprint) {
        System.out.println("Your fingerprint is " + fingerprint);
    }

    public boolean onTrustNew(X509Certificate partnerCert, String fingerprint) throws Exception {
        return prompt("This is the first time connecting to this computer. Does this fingerprint ("
                + fingerprint
                + ") match the one on the other computer?").equalsIgnoreCase("y");
    }

    public boolean onTrustDifferent(X509Certificate partnerCert, String fingerprint) throws Exception {
        return prompt("This computer has a different identity since the last time it onPeerConnected." +
                "This could be an attempt to hijack your computer." +
                "Does this fingerprint (" + fingerprint +
                ") match the one on the other computer?").equalsIgnoreCase("y");
    }

    public void log(String msg) {
        System.out.println("Log: " + msg );
    }

    public void debug(String msg) {
        System.out.println("Debug: " + msg );
    }

    public void error(String msg, Throwable ex) {
        System.out.println("Error: " + msg + ":");
        ex.printStackTrace();
        System.out.println("-------------------");
    }

    public void debugError(String msg, Throwable ex) {
        System.out.println("Debug Error: " + msg + ":");
        ex.printStackTrace();
        System.out.println("-------------------");
    }
}
