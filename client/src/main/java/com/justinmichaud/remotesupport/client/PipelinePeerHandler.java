package com.justinmichaud.remotesupport.client;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;

abstract class PipelinePeerHandler extends ChannelInboundHandlerAdapter {

    protected volatile Channel tunnel;
    protected final EventLoopGroup group;
    protected final TunnelEventHandler eh;

    public PipelinePeerHandler(EventLoopGroup group, TunnelEventHandler eh) {
        super();
        this.group = group;
        this.eh = eh;
    }

    protected abstract void establishTunnel(Channel peer);

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Channel peer = ctx.channel();
        peer.config().setOption(ChannelOption.AUTO_READ, false);

        establishTunnel(ctx.channel());

        // Wait until we are connected to the tunnel
        // This handler must be on a separate thread group, otherwise it will block the event thread
        while (tunnel == null && !peer.eventLoop().isShuttingDown()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                eh.debug("Interrupted waiting for tunnel");
                peer.close();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        eh.log("Connection to peer closed");
        if (tunnel != null) closeOnFlush(tunnel);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel peer = ctx.channel();

        if (tunnel == null || !tunnel.isActive()) {
            eh.debug("Attempted to read from a tunnel that is closed");
            closeOnFlush(peer);
            return;
        }

        tunnel.writeAndFlush(msg).addListener(future -> {
            if (future.isSuccess()) peer.read();
            else {
                eh.error("Error reading from peer", future.cause());
                peer.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        eh.error("Peer connection error", cause);
        closeOnFlush(ctx.channel());
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
