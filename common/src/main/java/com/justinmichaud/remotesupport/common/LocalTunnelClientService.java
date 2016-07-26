package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.net.Socket;

public class LocalTunnelClientService extends Service {

    private class ConnectPayload extends WorkerThreadManager.WorkerThreadPayload {

        private final int port;
        private volatile boolean connected = false;
        private WorkerThreadManager.WorkerThreadGroup connectedGroup;

        public ConnectPayload(int port) {
            this.port = port;
        }

        @Override
        public void tick() {
            Socket localSocket;
            try {
                localSocket = new Socket("localhost", port);
            } catch (IOException e) { return; }

            try {
                connectedGroup =
                        serviceManager.workerThreadManager.makeGroup("Local Tunnel Connection", () -> connected = false);
                connectedGroup.addWorkerThread(new InputOutputStreamPipePayload(localSocket.getInputStream(),
                        getOutputStream(), false));
                connectedGroup.addWorkerThread(new InputOutputStreamPipePayload(getInputStream(),
                        localSocket.getOutputStream(), false));
            } catch (IOException e) {
                logger.error("Error trying to connect to local port {}: {}", port, e);
                return;
            }

            connected = true;
            logger.info("Created new tunneled connection on port {}", port);

            while (connected) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {}
            }

            logger.info("Closed tunneled connection on port {}", port);
        }

        @Override
        public void stop(WorkerThreadManager.WorkerThreadGroup group) {
            logger.debug("Being asked to close tunneled connection on port {}", port);
            super.stop(group);
            if (connectedGroup != null) connectedGroup.stop();
        }
    }

    public LocalTunnelClientService(int id, ServiceManager serviceManager, int port)
            throws IOException {
        super(id, serviceManager);
        System.out.println("Allowing remote partner to send data to port " + port);
        workerThreadGroup.addWorkerThread(new ConnectPayload(port));
    }
}
