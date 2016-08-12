package com.justinmichaud.remotesupport.client.tunnel;

import com.justinmichaud.remotesupport.client.ConnectionEventHandler;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.concurrent.RejectedExecutionException;

public class ServiceManager {

    public final Service[] services = new Service[256];
    public final ConnectionEventHandler eh;
    public final NioEventLoopGroup eventLoopGroup;
    public final Channel peer;

    private ControlHandler controlHandler;

    public ServiceManager(ConnectionEventHandler eh, Channel peer, NioEventLoopGroup workerGroup) {
        this.eh = eh;
        this.peer = peer;
        this.eventLoopGroup =  workerGroup;

        this.controlHandler = new ControlHandler(this);
        peer.pipeline().addLast(controlHandler);
        controlHandler.channelActive(peer.pipeline().context(controlHandler));
    }

    public void addService(Service s) {
        if (services[s.id] != null)
            throw new IllegalArgumentException("A service with this id already exists");
        if (s.id <= 0 || s.id > 255)
            throw new IllegalArgumentException("Invalid service ID");
        services[s.id] = s;
        s.addToPipeline(peer.pipeline());
    }

    public void removeService(Service s) {
        s.removeFromPipeline();
    }

    public void removeService(int id) {
        if (services[id] == null)
            throw new IllegalArgumentException("Tried to remove service that doesn't exist");
        removeService(services[id]);
    }

    public void peerOpenPort(int serviceId, int remotePort) {
        controlHandler.peerOpenPort(serviceId, remotePort);
    }

    public void peerCloseService(int serviceId) {
        controlHandler.peerCloseService(serviceId);
    }

    public void close() {
        System.out.println("Service manager closing");
        for (Service s : services) if (s!=null) s.removeFromPipeline();
        try {
            peer.close().syncUninterruptibly();
        } catch (RejectedExecutionException e) {}
    }

    public int nextId() {
        for (int i=1; i<services.length; i++) {
            if (services[i] == null) return i;
        }

        throw new IllegalStateException("No more service ids available");
    }
}
