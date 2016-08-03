package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.net.*;

public class LocalTunnelClientService extends Service {

    private final int port;

    private class ConnectPayload extends WorkerThreadManager.WorkerThreadPayload {

        private Socket localSocket;

        public ConnectPayload() {
            super("Local Tunnel Client Connector");
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

            serviceManager.controlService.requestPeerOpenClientPortDone(LocalTunnelClientService.this);

            logger.info("Created new tunneled client connection on port {}", port);
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
        this.port = port;
        workerThreadGroup.addWorkerThread(new ConnectPayload());
    }

    @Override
    public String toString() {
        return "Client Tunnel: " + port;
    }
}
