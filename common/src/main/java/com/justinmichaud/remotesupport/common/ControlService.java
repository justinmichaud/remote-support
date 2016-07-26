package com.justinmichaud.remotesupport.common;

import java.io.IOException;

public class ControlService extends Service {

    public static final int OPEN_PORT = 0;
    public static final int CLOSE_PORT = 1;

    private class ControlPayload extends WorkerThreadManager.WorkerThreadPayload {
        @Override
        public void tick() throws Exception {
            int magic = getInputStream().read();
            if (magic == OPEN_PORT) {
                int id = getInputStream().read();
                int port = ((getInputStream().read()&0xFF) << 8) | (getInputStream().read()&0xFF);
                logger.info("Peer requested that we open port {} on service {}", port, id);
                serviceManager.addService(new LocalTunnelClientService(id, serviceManager, port));
            }
            else if (magic == CLOSE_PORT) {
                int id = getInputStream().read();
                logger.info("Peer requested that we stop service {}", id);
                serviceManager.stopService(id);
            }
            else {
                throw new IOException("Unknown magic number " + magic);
            }
        }
    }

    public ControlService(ServiceManager serviceManager) {
        super(0, serviceManager);
        workerThreadGroup.addWorkerThread(new ControlPayload());
    }

    public void requestPeerOpenPort(Service localService, int remotePort) throws IOException {
        logger.info("Requesting that peer open port {} on service {}", remotePort, localService.id);
        getOutputStream().write(OPEN_PORT);
        getOutputStream().write(localService.id);
        getOutputStream().write((remotePort >> 8) & 0xFF);
        getOutputStream().write(remotePort & 0xFF);
    }

    public void requestPeerCloseService(int id) throws IOException {
        logger.info("Requesting that peer close service {}", id);
        getOutputStream().write(CLOSE_PORT);
        getOutputStream().write(id);
    }
}
