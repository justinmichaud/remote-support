package com.justinmichaud.remotesupport.client.services;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public abstract class Service {

    private ChannelPipeline pipeline;

    protected final ServiceManager serviceManager;
    protected final EventLoopGroup serviceGroup;
    protected final ArrayList<EventLoopGroup> logicGroups = new ArrayList<>();

    protected ServiceHandler handler;

    public final String name;
    public final int id;

    public Service(String name, int id, ServiceManager serviceManager) {
        if (id <=0 || id >255) throw new IllegalArgumentException("Invalid Service ID");

        this.name = name;
        this.id = id;
        this.serviceManager = serviceManager;
        serviceGroup = new NioEventLoopGroup(1);
    }

    protected void setHandler(ServiceHandler handler) {
        this.handler = handler;
    }

    public void addToPipeline(ChannelPipeline pipeline) {
        if (handler == null) throw new IllegalStateException("Must set handler");

        serviceManager.eh.serviceOpen(this);

        this.pipeline = pipeline;
        pipeline.addLast(serviceGroup, handler);
        serviceGroup.schedule(() -> handler.channelActive(pipeline.context(handler)), 0, TimeUnit.SECONDS);
    }

    public void removeFromPipeline() {
        if (handler == null) throw new IllegalStateException("Must set handler");

        System.out.println("Service " + name + ":" + id + ": Removing from pipeline");
        serviceGroup.schedule(() -> {
            handler.channelInactive(pipeline.context(handler));
            pipeline.remove(handler);
        }, 0, TimeUnit.SECONDS);

        serviceManager.services.remove(this);
        serviceManager.eh.serviceClosed(this);
    }

    EventLoopGroup makeEventLoopGroup() {
        EventLoopGroup g = new NioEventLoopGroup();
        logicGroups.add(g);
        return g;
    }

    public void onHandlerActive() {
        serviceManager.eh.debug("Service handler active: " + name + ":" + id);
    }

    public void onHandlerInactive() {
        serviceManager.eh.debug("On Service handler inactive: " + name + ":" + id);
        serviceGroup.shutdownGracefully();
        logicGroups.forEach(EventLoopGroup::shutdownGracefully);
    }
}
