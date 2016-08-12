package com.justinmichaud.remotesupport.client.tunnel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

class PortForwardServiceTunnelHandler extends ChannelInboundHandlerAdapter {

    private volatile Channel peer;
    private final Service service;

    public PortForwardServiceTunnelHandler(Channel peer, Service service) {
        this.peer = peer;
        this.service = service;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        service.debug("Connection to tunnel closed");
        service.removeFromPipeline();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel tunnel = ctx.channel();

        if (peer == null || !peer.isActive()) {
            service.debug("Attempted to read from a peer that is closed");
            closeOnFlush(tunnel);
            return;
        }

        ServiceHeader serviceHeader = new ServiceHeader();
        serviceHeader.id = service.id;
        serviceHeader.buf = (ByteBuf) msg;

        peer.writeAndFlush(serviceHeader).addListener(future -> {
            if (!future.isSuccess()) {
                service.debugError("Error reading from tunnel", future.cause());
                tunnel.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        service.debugError("Tunnel error", cause);
        closeOnFlush(ctx.channel());
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
