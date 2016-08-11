package com.justinmichaud.remotesupport.client.services;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;

public class ServiceManager {

    public final Service[] services = new Service[256];
    public final TunnelEventHandler eh;
    public final NioEventLoopGroup eventLoopGroup;
    private ChannelPipeline pipeline;

    public ServiceManager(TunnelEventHandler eh, ChannelPipeline pipeline, NioEventLoopGroup workerGroup) {
        this.eh = eh;
        this.pipeline = pipeline;
        this.eventLoopGroup =  workerGroup;
    }

    public void addService(Service s) {
        if (services[s.id] != null)
            throw new IllegalArgumentException("A service with this id already exists");
        if (s.id <= 0 || s.id > 255)
            throw new IllegalArgumentException("Invalid service ID");
        services[s.id] = s;
        s.addToPipeline(pipeline);
    }

    public void removeService(Service s) {
        s.removeFromPipeline();
    }

    public void close() {
        System.out.println("Service manager closing");
        for (Service s : services) removeService(s);
    }
}
