package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class LocalTunnelServerService extends Service {

    private class ConnectPayload extends WorkerThreadManager.WorkerThreadPayload {

        private final ServerSocket localServer;
        private final int localPort, remotePort;
        private Socket localSocket;

        public ConnectPayload(int localPort, int remotePort) throws IOException {
            super("Local Tunnel Server Acceptor");

            this.localPort = localPort;
            this.remotePort = remotePort;
            localServer = new ServerSocket(localPort);
            localServer.setSoTimeout(100);
        }

        @Override
        public void start(WorkerThreadManager.WorkerThreadGroup group) throws Exception {
            System.out.println("Opening server port " + localPort + " -> " + remotePort);

            while (localSocket == null
                    && group.isRunning()) {
                try {
                    localSocket = localServer.accept();
                    localSocket.setSoTimeout(1000);
                } catch (IOException e) {}
            }

            workerThreadGroup.addWorkerThread(new InputOutputStreamPipePayload(localSocket.getInputStream(),
                    getOutputStream()));
            workerThreadGroup.addWorkerThread(new InputOutputStreamPipePayload(getInputStream(),
                    localSocket.getOutputStream()));

            logger.info("Created new tunneled server connection on port {}", localPort);
            serviceManager.controlService.requestPeerOpenClientPort(LocalTunnelServerService.this, remotePort);
        }

        @Override
        public void tick() throws Exception {
            Thread.sleep(100);
        }

        @Override
        public void stop() throws Exception {
            serviceManager.controlService.requestPeerCloseService(id);
            if (localSocket != null && !localSocket.isClosed()) localSocket.close();
            if (!localServer.isClosed()) localServer.close();

            logger.info("Closed tunneled server connection on port {}", localPort);
            System.out.println("Closing server port " + localPort + " -> " + remotePort);
        }
    }

    public LocalTunnelServerService(int id, ServiceManager serviceManager, int localPort, int remotePort)
            throws IOException {
        super(id, serviceManager);
        workerThreadGroup.addWorkerThread(new ConnectPayload(localPort, remotePort));
    }
}
