package com.justinmichaud.remotesupport.client.services;

import io.netty.channel.ChannelPipeline;

import java.util.ArrayList;

public class ServiceManager {

    public final ArrayList<Service> services = new ArrayList<>();
    public final TunnelEventHandler eh;
    private ChannelPipeline pipeline;

    public ServiceManager(TunnelEventHandler eh, ChannelPipeline pipeline) {
        this.eh = eh;
        this.pipeline = pipeline;
    }

    public void addService(Service s) {
        services.add(s);
        s.addToPipeline(pipeline);
    }

    public void removeService(Service s) {
        s.removeFromPipeline();
    }

    public void close() {
        services.forEach(this::removeService);
    }
}
