package com.justinmichaud.remotesupport.common;

import org.bouncycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;

public class PeerConnection {

    private TlsConnection tlsConnection;
    private ServiceManager serviceManager;
    private ControlService controlService;

    public PeerConnection(String alias, String partnerAlias, Socket baseSocket, boolean server)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        tlsConnection = new TlsConnection(alias, partnerAlias, baseSocket,
                new File(alias.replaceAll("\\W+", "") + "_private.jks"),
                new File(alias.replaceAll("\\W+", "") + "_trusted.jks"), server);
        serviceManager = new ServiceManager(this, tlsConnection.getSocket());
        controlService = new ControlService(this);
        serviceManager.addService(controlService);
    }

    public void openPort(int id, int localPort, int remotePort) throws IOException {
        System.out.println("Opening Port " + localPort + "->" + remotePort);
        Service service = openLocalPort(id, localPort);
        controlService.requestPeerOpenPort(service, remotePort);
    }

    public Service openLocalPort(int id, int localPort) throws IOException {
        System.out.println("Opening local port: " + localPort + " (id: " + id + ")");
        return serviceManager.addService(new LocalTunnelServerService(id, localPort));
    }

    public void closeService(int id) throws IOException {
        System.out.println("Closing service " + id);
        closeLocalService(id);
        controlService.requestPeerCloseService(id);
    }

    public void closeLocalService(int id) throws IOException {
        System.out.println("Closing service " + id);
        serviceManager.removeService(id);
    }

    public boolean isOpen() {
        return !tlsConnection.getSocket().isClosed();
    }

}
