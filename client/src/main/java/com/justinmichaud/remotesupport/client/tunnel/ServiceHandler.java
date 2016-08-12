package com.justinmichaud.remotesupport.client.tunnel;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

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
        service.debug("Service handler active");
        service.onHandlerActive();
        onChannelActive(ctx);
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) {
        service.debug("Service handler inactive");
        service.onHandlerInactive();
        onChannelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        service.error("Service handler error", cause);
        closeOnFlush(ctx.channel());
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
