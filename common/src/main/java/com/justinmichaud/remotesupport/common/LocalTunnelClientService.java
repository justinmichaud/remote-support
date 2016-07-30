package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.net.*;

public class LocalTunnelClientService extends Service {

    private class ConnectPayload extends WorkerThreadManager.WorkerThreadPayload {

        private final int port;
        private Socket localSocket;

        public ConnectPayload(int port) {
            super("Local Tunnel Client Connector");
            this.port = port;
        }

        @Override
        public void start(WorkerThreadManager.WorkerThreadGroup group) throws Exception {
            logger.info("Allowing remote partner to send data to port " + port);

            while (localSocket == null
                    && group.isRunning()) {
                try {
                    localSocket = new Socket();
                    localSocket.connect(new InetSocketAddress("localhost", port), 100);
                    localSocket.setSoTimeout(100);
                } catch (IOException e) {
                    localSocket = null;
                }
            }

            workerThreadGroup.addWorkerThread(new InputOutputStreamPipePayload(localSocket.getInputStream(),
                    getOutputStream()));
            workerThreadGroup.addWorkerThread(new InputOutputStreamPipePayload(getInputStream(),
                    localSocket.getOutputStream()));

            logger.info("Created new tunneled client connection on port {}", port);
        }

        @Override
        public void tick() throws Exception {
            Thread.sleep(100);
        }

        @Override
        public void stop() throws Exception {
            serviceManager.controlService.requestPeerCloseService(id);
            if (localSocket != null && !localSocket.isClosed()) localSocket.close();

            logger.info("Closed tunneled client connection on port {}", port);
            System.out.println("Partner can no longer send data to port " + port);
        }
    }

    public LocalTunnelClientService(int id, ServiceManager serviceManager, int port)
            throws IOException {
        super(id, serviceManager);
        workerThreadGroup.addWorkerThread(new ConnectPayload(port));
    }
}
