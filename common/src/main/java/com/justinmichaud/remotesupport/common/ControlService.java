package com.justinmichaud.remotesupport.common;

import java.io.IOException;

public class ControlService extends Service {

    public static final int MAGIC_OPEN_CLIENT_PORT = 0;
    public static final int MAGIC_OPEN_SERVER_PORT = 1;
    public static final int MAGIC_CLOSE_SERVICE = 2;

    private class ControlPayload extends WorkerThreadManager.WorkerThreadPayload {

        public ControlPayload() {
            super("Control Service Read Thread");
        }

        @Override
        public void tick() throws Exception {
            int magic = getInputStream().read();
            if (magic == MAGIC_OPEN_CLIENT_PORT) {
                int id = getInputStream().read();
                int port = ((getInputStream().read()&0xFF) << 8) | (getInputStream().read()&0xFF);
                logger.debug("Peer requested that we open port {} on service {}", port, id);
                serviceManager.addService(new LocalTunnelClientService(id, serviceManager, port));
            }
            else if (magic == MAGIC_OPEN_SERVER_PORT) {
                int ourPort = ((getInputStream().read()&0xFF) << 8) | (getInputStream().read()&0xFF);
                int theirPort = ((getInputStream().read()&0xFF) << 8) | (getInputStream().read()&0xFF);

                logger.debug("Peer requested that we open server port (our) {} -> (their) {}", ourPort, theirPort);
                serviceManager.addService(new LocalTunnelServerService(serviceManager.getNextId(),
                        serviceManager, ourPort, theirPort));
            }
            else if (magic == MAGIC_CLOSE_SERVICE) {
                int id = getInputStream().read();
                logger.debug("Peer requested that we stop service {}", id);
                if (serviceManager.getService(id) == null) {
                    logger.debug("Can't remove service " + id + " as it does not exist");
                }
                else serviceManager.getService(id).stop();
            }
            else {
                throw new IOException("Control Service - Unknown magic number " + magic);
            }
        }
    }

    public ControlService(ServiceManager serviceManager) {
        super(0, serviceManager);
        workerThreadGroup.addWorkerThread(new ControlPayload());
    }

    public void requestPeerOpenClientPort(Service localService, int remotePort) throws IOException {
        logger.info("Requesting that peer open client port {} on service {}", remotePort, localService.id);
        getOutputStream().write(MAGIC_OPEN_CLIENT_PORT);
        getOutputStream().write(localService.id);
        getOutputStream().write((remotePort >> 8) & 0xFF);
        getOutputStream().write(remotePort & 0xFF);
    }

    public void requestPeerOpenServerPort(int theirPort, int ourPort) throws IOException {
        logger.info("Requesting that peer open server port (their) {} -> (our) {}", theirPort, ourPort);
        getOutputStream().write(MAGIC_OPEN_SERVER_PORT);
        getOutputStream().write((theirPort >> 8) & 0xFF);
        getOutputStream().write(theirPort & 0xFF);
        getOutputStream().write((ourPort >> 8) & 0xFF);
        getOutputStream().write(ourPort & 0xFF);
    }

    public void requestPeerCloseService(int id) throws IOException {
        logger.info("Requesting that peer close service {}", id);
        getOutputStream().write(MAGIC_CLOSE_SERVICE);
        getOutputStream().write(id);
    }

    @Override
    public void stop() {
        super.stop();
        serviceManager.stop();
    }
}
