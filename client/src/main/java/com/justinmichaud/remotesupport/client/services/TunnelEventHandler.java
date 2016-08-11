package com.justinmichaud.remotesupport.client.services;

import io.netty.channel.ChannelFuture;

import java.security.cert.X509Certificate;
import java.util.Scanner;
import java.util.concurrent.RejectedExecutionException;

public class TunnelEventHandler {

    private String cliPrompt(String msg) {
        System.out.println("Prompt: " + msg);
        return new Scanner(System.in).nextLine();
    }

    public void trustStart(X509Certificate ourCert, String fingerprint) {
        System.out.println("Your fingerprint is " + fingerprint);
    }

    public boolean trustNew(X509Certificate partnerCert, String fingerprint) throws Exception {
        return cliPrompt("This is the first time connecting to this computer. Does this fingerprint ("
                + fingerprint
                + ") match the one on the other computer?").equalsIgnoreCase("y");
    }

    public boolean trustDifferent(X509Certificate partnerCert, String fingerprint) throws Exception {
        return cliPrompt("This computer has a different identity since the last time it connected." +
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

    public void connectionClosed() {
        System.out.println("Event Handler: Connection closed.");
    }

    public void start(ChannelFuture f) {
        System.out.println("Got channel future");

        //Shut down gracefully when user presses ctrl+c
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                f.channel().close().sync();
            } catch (InterruptedException|RejectedExecutionException e) {}
        }));
    }

    public void serviceOpen(Service service) {
        System.out.println("Event Handler: Service " + service.name + ":" + service.id + " open.");
    }

    public void serviceClosed(Service service) {
        System.out.println("Event Handler: Service " + service.name + ":" + service.id + " closed.");
    }
}
