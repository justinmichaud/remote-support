package com.justinmichaud.remotesupport.common;

import java.io.IOException;

public class ControlService extends Service {

    public static final int OPEN_PORT = 0;
    public static final int CLOSE_PORT = 1;

    private final PeerConnection peerConnection;

    private Thread readThread;

    private class ReadThread implements Runnable {
        @Override
        public void run() {
            while (isOpen()) {
                try {
                    int magic = getInputStream().read();
                    if (magic == OPEN_PORT) {
                        int id = getInputStream().read();
                        int port = ((getInputStream().read()&0xFF) << 8) | (getInputStream().read()&0xFF);
                        peerConnection.openClientPort(id, port);
                    }
                    else if (magic == CLOSE_PORT) {
                        int id = getInputStream().read();
                        peerConnection.closeLocalService(id);
                    }
                    else {
                        throw new IOException("Unknown magic number " + magic);
                    }
                } catch (IOException e) {
                    try {
                        close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    public ControlService(PeerConnection peerConnection) {
        super(0, peerConnection.serviceManager);
        this.peerConnection = peerConnection;

        readThread = new Thread(new ReadThread());
        readThread.setDaemon(true);
        readThread.start();
    }

    public void requestPeerOpenPort(Service localService, int remotePort) throws IOException {
        getOutputStream().write(OPEN_PORT);
        getOutputStream().write(localService.id);
        getOutputStream().write((remotePort >> 8) & 0xFF);
        getOutputStream().write(remotePort & 0xFF);
    }

    public void requestPeerCloseService(int id) throws IOException {
        getOutputStream().write(CLOSE_PORT);
        getOutputStream().write(id);
    }
}
