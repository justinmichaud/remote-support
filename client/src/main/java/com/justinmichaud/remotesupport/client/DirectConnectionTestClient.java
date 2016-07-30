package com.justinmichaud.remotesupport.client;

import com.barchart.udt.net.NetServerSocketUDT;
import com.barchart.udt.net.NetSocketUDT;
import com.justinmichaud.remotesupport.common.CLI;
import com.justinmichaud.remotesupport.common.PeerConnection;
import com.justinmichaud.remotesupport.common.Service;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import java.util.Set;

public class DirectConnectionTestClient {

    //Direct connection, with no nat traversal, for testing

    public static void main(String... args) throws GeneralSecurityException, IOException, OperatorCreationException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");

        System.out.println("Act as client or server [c/s]?");
        boolean server = new Scanner(System.in).nextLine().equalsIgnoreCase("s");
        if (server) CLI.runCLI(acceptPeerConnection("server", "client"));
        else CLI.runCLI(connectPeerConnection(new InetSocketAddress("localhost", 5000), "client", "server"));
    }

    public static PeerConnection acceptPeerConnection(String ourName, String partnerName)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        PeerConnection conn = new PeerConnection(ourName, partnerName, baseConnection, true); //TODO timeout
        return conn;
    }

    public static PeerConnection connectPeerConnection(InetSocketAddress addr, String ourName, String partnerName)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        Socket baseSocket = new NetSocketUDT();
        baseSocket.connect(addr);
        PeerConnection conn = new PeerConnection(ourName, partnerName, baseSocket, false); //TODO timeout
        return conn;
    }
}
