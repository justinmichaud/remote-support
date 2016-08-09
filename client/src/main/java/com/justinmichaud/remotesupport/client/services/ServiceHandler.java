package com.justinmichaud.remotesupport.client.services;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;

public abstract class ServiceHandler extends ChannelInboundHandlerAdapter {

    protected final Service service;

    public ServiceHandler(Service service) {
        super();
        this.service = service;
    }

    public void onChannelActive(ChannelHandlerContext ctx) {}
    public void onChannelInactive(ChannelHandlerContext ctx) {}

    @Override
    public final void channelActive(ChannelHandlerContext ctx) {
        debug("Service handler active");
        service.onHandlerActive();
        onChannelActive(ctx);
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) {
        debug("Service handler inactive");
        service.onHandlerInactive();
        onChannelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        error("Service handler error", cause);
        closeOnFlush(ctx.channel());
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public void error(String msg, Throwable cause) {
        service.serviceManager.eh.error("Service " + service.name + ":" + service.id
                + ":" + msg, cause);
    }

    public void debugError(String msg, Throwable cause) {
        service.serviceManager.eh.debugError("Service " + service.name + ":" + service.id
                + ":" + msg, cause);
    }

    public void log(String msg) {
        service.serviceManager.eh.log("Service " + service.name + ":" + service.id
                + ":" + msg);
    }

    public void debug(String msg) {
        service.serviceManager.eh.debug("Service " + service.name + ":" + service.id
                + ":" + msg);
    }
}
