package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class LocalTunnelServerService extends Service {

    private class ConnectPayload extends WorkerThreadManager.WorkerThreadPayload {

        private final ServerSocket localServer;
        private final int localPort, remotePort;

        private volatile boolean connected = false;
        private WorkerThreadManager.WorkerThreadGroup connectedGroup;

        public ConnectPayload(int localPort, int remotePort) throws IOException {
            super("Local Tunnel Server Acceptor");

            this.localPort = localPort;
            this.remotePort = remotePort;
            localServer = new ServerSocket(localPort);
        }

        @Override
        public void tick() throws Exception {
            Socket localSocket;
            try {
                localSocket = localServer.accept();
            } catch (IOException e) { return; }

            try {
                connectedGroup =
                        serviceManager.workerThreadManager.makeGroup("Local Tunnel Server Connection", () -> connected = false);
                connected = true;
                connectedGroup.addWorkerThread(new InputOutputStreamPipePayload(localSocket.getInputStream(),
                        getOutputStream(), false));
                connectedGroup.addWorkerThread(new InputOutputStreamPipePayload(getInputStream(),
                        localSocket.getOutputStream(), false));
            } catch (IOException e) {
                logger.error("Error trying to accept from local port {}: {}", localPort, e);
                if (connectedGroup != null) connectedGroup.stop();
                return;
            }

            logger.info("Created new tunneled server connection on port {}", localPort);
            serviceManager.controlService.requestPeerOpenPort(LocalTunnelServerService.this, remotePort);

            while (connected) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.debug("Interrupted while waiting for connection to end");
                    if (connectedGroup != null) connectedGroup.stop();
                }
            }

            localSocket.close();
            serviceManager.controlService.requestPeerCloseService(LocalTunnelServerService.this.id);
            logger.info("Closed tunneled server connection on port {}", localPort);
        }

        @Override
        public void stop() throws Exception {
            if (connectedGroup != null) connectedGroup.stop();
            localServer.close();
        }
    }

    public LocalTunnelServerService(int id, ServiceManager serviceManager, int localPort, int remotePort)
            throws IOException {
        super(id, serviceManager);
        System.out.println("Opening server port " + localPort + " -> " + remotePort);
        workerThreadGroup.addWorkerThread(new ConnectPayload(localPort, remotePort));
    }
}
