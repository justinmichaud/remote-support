package com.justinmichaud.remotesupport.client.services;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;

abstract class PortForwardServiceHandler extends ServiceHandler {

    protected volatile Channel tunnel;

    private ByteBuf backlogBeforeTunnelEstablished;
    private boolean tunnelEstablished = false;

    public PortForwardServiceHandler(Service service) {
        super(service);
    }

    protected abstract void establishTunnel(Channel peer);

    protected void onTunnelEstablished() {
        tunnelEstablished = true;
        backlogBeforeTunnelEstablished.retain();
        tunnel.writeAndFlush(backlogBeforeTunnelEstablished);
    }

    @Override
    public void onChannelActive(ChannelHandlerContext ctx) {
        backlogBeforeTunnelEstablished = Unpooled.buffer();
        backlogBeforeTunnelEstablished.retain();
        establishTunnel(ctx.channel());
    }

    @Override
    public void onChannelInactive(ChannelHandlerContext ctx) {
        if (tunnel != null) closeOnFlush(tunnel);
        backlogBeforeTunnelEstablished.release();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel peer = ctx.channel();

        ServiceHeader serviceHeader = (ServiceHeader) msg;
        if (serviceHeader.id != service.id) {
            ctx.fireChannelRead(serviceHeader);
            return;
        }

        if (!tunnelEstablished) {
            backlogBeforeTunnelEstablished.writeBytes(serviceHeader.buf);
            return;
        }

        if (tunnel == null || !tunnel.isActive()) {
            throw new IllegalStateException("Attempted to read from a tunnel that is closed");
        }

        tunnel.writeAndFlush(serviceHeader.buf).addListener(future -> {
            if (!future.isSuccess()) {
                service.debugError("Error reading from peer", future.cause());
                peer.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        service.error("Peer connection error", cause);
        service.removeFromPipeline();
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
