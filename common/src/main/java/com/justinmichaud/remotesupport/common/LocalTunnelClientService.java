package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.net.Socket;

public class LocalTunnelClientService extends Service {

    private class ConnectPayload extends WorkerThreadManager.WorkerThreadPayload {

        private final int port;
        private volatile boolean connected = false;
        private WorkerThreadManager.WorkerThreadGroup connectedGroup;

        public ConnectPayload(int port) {
            super("Local Tunnel Client Connector");
            this.port = port;
        }

        @Override
        public void tick() throws IOException {
            Socket localSocket;
            try {
                localSocket = new Socket("localhost", port);
            } catch (IOException e) { return; }

            try {
                connectedGroup =
                        serviceManager.workerThreadManager.makeGroup("Local Tunnel Client Connection", () -> connected = false);
                connected = true;
                connectedGroup.addWorkerThread(new InputOutputStreamPipePayload(localSocket.getInputStream(),
                        getOutputStream(), false));
                connectedGroup.addWorkerThread(new InputOutputStreamPipePayload(getInputStream(),
                        localSocket.getOutputStream(), false));
            } catch (IOException e) {
                logger.error("Error trying to connect to local port {}: {}", port, e);
                if (connectedGroup != null) connectedGroup.stop();
                return;
            }

            logger.info("Created new tunneled connection on port {}", port);

            while (connected) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.debug("Interrupted while waiting for connection to end");
                    if (connectedGroup != null) connectedGroup.stop();
                }
            }

            localSocket.close();
            logger.info("Closed tunneled connection on port {}", port);
        }

        @Override
        public void stop() {
            if (connectedGroup != null) connectedGroup.stop();
        }
    }

    public LocalTunnelClientService(int id, ServiceManager serviceManager, int port)
            throws IOException {
        super(id, serviceManager);
        logger.info("Allowing remote partner to send data to port " + port);
//        workerThreadGroup.addWorkerThread(new ConnectPayload(port));
        workerThreadGroup.addWorkerThread(new InputOutputStreamPipePayload(getInputStream(), getOutputStream(), false)); //TODO testing
    }
}
