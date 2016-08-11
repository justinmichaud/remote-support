package com.justinmichaud.remotesupport.client.services;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;

public class ServiceManager {

    public final ArrayList<Service> services = new ArrayList<>();
    public final TunnelEventHandler eh;
    public final NioEventLoopGroup eventLoopGroup;
    private ChannelPipeline pipeline;

    public ServiceManager(TunnelEventHandler eh, ChannelPipeline pipeline, NioEventLoopGroup workerGroup) {
        this.eh = eh;
        this.pipeline = pipeline;
        this.eventLoopGroup =  workerGroup;
    }

    public void addService(Service s) {
        services.add(s);
        s.addToPipeline(pipeline);
    }

    public void removeService(Service s) {
        s.removeFromPipeline();
    }

    public void close() {
        System.out.println("Service manager closing");
        services.forEach(this::removeService);
    }
}
