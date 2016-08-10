package com.justinmichaud.remotesupport.client.services;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public abstract class Service {

    private ChannelPipeline pipeline;

    private final ServiceManager serviceManager;
    protected final ArrayList<EventLoopGroup> logicGroups = new ArrayList<>();

    protected ServiceHandler handler;

    public final String name;
    public final int id;

    public Service(String name, int id, ServiceManager serviceManager) {
        if (id <=0 || id >255) throw new IllegalArgumentException("Invalid Service ID");

        this.name = name;
        this.id = id;
        this.serviceManager = serviceManager;
    }

    protected void setHandler(ServiceHandler handler) {
        this.handler = handler;
    }

    public void addToPipeline(ChannelPipeline pipeline) {
        if (handler == null) throw new IllegalStateException("Must set handler");

        serviceManager.eh.serviceOpen(this);

        this.pipeline = pipeline;
        pipeline.addLast(handler);
        handler.channelActive(pipeline.context(handler));
    }

    public void removeFromPipeline() {
        if (handler == null) throw new IllegalStateException("Must set handler");

        debug("Removing service from pipeline");
        handler.channelInactive(pipeline.context(handler));
        pipeline.remove(handler);

        serviceManager.services.remove(this);
        serviceManager.eh.serviceClosed(this);
    }

    EventLoopGroup makeEventLoopGroup() {
        EventLoopGroup g = new NioEventLoopGroup();
        logicGroups.add(g);
        return g;
    }

    public void onHandlerActive() {
        debug("On Service handler active");
    }

    public void onHandlerInactive() {
        debug("On Service handler inactive");
        logicGroups.forEach(EventLoopGroup::shutdownGracefully);
    }

    public void error(String msg, Throwable cause) {
        serviceManager.eh.error(this + ": " + msg, cause);
    }

    public void debugError(String msg, Throwable cause) {
        serviceManager.eh.debugError(this + ": " + msg, cause);
    }

    public void log(String msg) {
        serviceManager.eh.log(this + ": " + msg);
    }

    public void debug(String msg) {
        serviceManager.eh.debug(this + ": " + msg);
    }

    @Override
    public String toString() {
        return name + ":" + id;
    }
}
