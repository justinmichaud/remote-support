package com.justinmichaud.remotesupport.client.services;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;

public abstract class Service {

    private ChannelPipeline pipeline;

    protected final ServiceManager serviceManager;
    protected final EventLoopGroup serviceGroup;
    protected final ArrayList<EventLoopGroup> logicGroups = new ArrayList<>();

    protected ServiceHandler handler;

    public final String name;
    public final int id;

    public Service(String name, int id, ServiceManager serviceManager) {
        this.name = name;
        this.id = id;
        this.serviceManager = serviceManager;
        serviceGroup = new NioEventLoopGroup();
    }

    protected void setHandler(ServiceHandler handler) {
        this.handler = handler;
    }

    public void addToPipeline(ChannelPipeline pipeline) {
        this.pipeline = pipeline;
        pipeline.addLast(serviceGroup, handler);
        pipeline.fireChannelActive();
    }

    public void removeFromPipeline() {
        pipeline.remove(handler);
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
        serviceManager.eh.debug("Service handler inactive: " + name + ":" + id);
        serviceGroup.shutdownGracefully();
        logicGroups.forEach(EventLoopGroup::shutdownGracefully);
        serviceManager.removeService(this);
    }
}
