package com.justinmichaud.remotesupport.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class PipelinePeerHandlerConnector extends PipelinePeerHandler {

    public PipelinePeerHandlerConnector(EventLoopGroup group, TunnelEventHandler eh) {
        super(group, eh);
    }

    @Override
    protected void establishTunnel(Channel peer) {
        Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(NioSocketChannel.class);
        b.handler(new TcpForwardTunnelHandler(peer, eh));

        ChannelFuture f = b.connect("localhost", 22);
        tunnel = f.channel();
        f.addListener(future -> {
            if (future.isSuccess()) {
                eh.log("Connected to tunnel");
                peer.read();
            } else {
                eh.error("Error connecting to tunnel", future.cause());
                peer.close();
            }
        });
    }
}
