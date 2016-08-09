package com.justinmichaud.remotesupport.client;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;

class PipelinePeerHandlerAcceptor extends PipelinePeerHandler {

    public PipelinePeerHandlerAcceptor(EventLoopGroup group, TunnelEventHandler eh) {
        super(group, eh);
    }

    @Override
    protected void establishTunnel(Channel peer) {
        ServerBootstrap b = new ServerBootstrap();
        b.option(ChannelOption.SO_REUSEADDR, true);
        b.group(group);
        b.channel(NioServerSocketChannel.class);
        b.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                if (tunnel != null) throw new IOException("The tunnel is already connected.");
                eh.log("Accepted connection to tunnel");
                tunnel = ch;
                ch.pipeline().addLast(new TcpForwardTunnelHandler(peer, eh));
                peer.read();
            }
        });

        b.bind(4999).addListener(future -> {
            if (future.isSuccess()) {
                eh.log("Listening for connection to tunnel");
            } else {
                eh.error("Error listening for connection to tunnel", future.cause());
                peer.close();
            }
        });
    }
}
