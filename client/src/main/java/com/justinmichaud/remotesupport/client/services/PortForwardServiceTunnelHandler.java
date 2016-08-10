package com.justinmichaud.remotesupport.client.services;

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
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.channel().config().setOption(ChannelOption.AUTO_READ, false);
        ctx.read();
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

        peer.writeAndFlush(msg).addListener(future -> {
            if (future.isSuccess()) tunnel.read();
            else {
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
