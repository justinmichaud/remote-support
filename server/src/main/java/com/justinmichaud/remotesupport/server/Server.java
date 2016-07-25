package com.justinmichaud.remotesupport.server;

import com.barchart.udt.net.NetServerSocketUDT;
import com.justinmichaud.remotesupport.common.Connection;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;

public class Server {


    public static void main(String... args) throws IOException, GeneralSecurityException, OperatorCreationException {
        System.out.println("Server");

        ServerSocket serverSocket = new NetServerSocketUDT();
        serverSocket.bind(new InetSocketAddress(5000), 1);

        Socket baseConnection = serverSocket.accept();
        Connection conn = new Connection("server", "client", baseConnection, new File("server_private.jks"),
                new File("server_trusted.jks"), true);

        InputStream inputStream = conn.getInputStream();
        InputStreamReader streamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String string;
        while ((string = bufferedReader.readLine()) != null) {
            System.out.println(string);
            System.out.flush();
        }
    }

}
