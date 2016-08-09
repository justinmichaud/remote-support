package com.justinmichaud.remotesupport.client;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;

class TcpForwardTunnelHandler extends ChannelInboundHandlerAdapter {

    private volatile Channel peer;
    private final TunnelEventHandler eh;

    public TcpForwardTunnelHandler(Channel peer, TunnelEventHandler eh) {
        this.peer = peer;
        this.eh = eh;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.channel().config().setOption(ChannelOption.AUTO_READ, false);
        ctx.read();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        eh.debug("Connection to tunnel closed");
        if (peer != null) closeOnFlush(peer);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel tunnel = ctx.channel();

        if (peer == null || !peer.isActive()) {
            eh.debug("Attempted to read from a peer that is closed");
            closeOnFlush(tunnel);
            return;
        }

        peer.writeAndFlush(msg).addListener(future -> {
            if (future.isSuccess()) tunnel.read();
            else {
                eh.debugError("Error reading from tunnel", future.cause());
                tunnel.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        eh.debugError("Tunnel error", cause);
        closeOnFlush(ctx.channel());
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
