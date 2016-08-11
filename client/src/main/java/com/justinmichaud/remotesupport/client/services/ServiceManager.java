package com.justinmichaud.remotesupport.client.services;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;

public class ServiceManager {

    public final Service[] services = new Service[256];
    public final TunnelEventHandler eh;
    public final NioEventLoopGroup eventLoopGroup;
    public final Channel peer;

    private ControlHandler controlHandler;

    public ServiceManager(TunnelEventHandler eh, Channel peer, NioEventLoopGroup workerGroup) {
        this.eh = eh;
        this.peer = peer;
        this.eventLoopGroup =  workerGroup;

        this.controlHandler = new ControlHandler(this);
        peer.pipeline().addLast(controlHandler);
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
        for (Service s : services) removeService(s);
    }
}
