package com.justinmichaud.remotesupport.client.services;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

abstract class PortForwardServiceHandler extends ServiceHandler {

    protected volatile Channel tunnel;

    public PortForwardServiceHandler(Service service) {
        super(service);
    }

    protected abstract void establishTunnel(Channel peer);

    @Override
    public void onChannelActive(ChannelHandlerContext ctx) {
        Channel peer = ctx.channel();
        peer.config().setOption(ChannelOption.AUTO_READ, false);

        establishTunnel(ctx.channel());

        // Wait until we are connected to the tunnel
        // This handler must be on a separate thread logicGroup, otherwise it will block the event thread
        while (tunnel == null && !peer.eventLoop().isShuttingDown()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                service.debug("Interrupted waiting for tunnel");
                peer.close();
            }
        }
    }

    @Override
    public void onChannelInactive(ChannelHandlerContext ctx) {
        if (tunnel != null) closeOnFlush(tunnel);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel peer = ctx.channel();

        ServiceHeader serviceHeader = (ServiceHeader) msg;
        if (serviceHeader.id != service.id) {
            ctx.fireChannelRead(serviceHeader);
            return;
        }

        if (tunnel == null || !tunnel.isActive()) {
            throw new IllegalStateException("Attempted to read from a tunnel that is closed");
        }

        tunnel.writeAndFlush(serviceHeader.buf).addListener(future -> {
            if (future.isSuccess()) peer.read();
            else {
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
