package com.justinmichaud.remotesupport.client.services;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;

public class PortForwardClientService extends Service {

    public final int port;

    public class Acceptor extends PortForwardServiceHandler {

        public Acceptor() {
            super(PortForwardClientService.this);
        }

        @Override
        protected void establishTunnel(Channel peer) {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_REUSEADDR, true);
            b.group(service.serviceManager.eventLoopGroup);
            b.channel(NioServerSocketChannel.class);
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    if (tunnel != null) throw new IOException("The tunnel is already connected.");
                    log("Accepted connection to tunnel");
                    tunnel = ch;
                    ch.pipeline().addLast(new PortForwardServiceTunnelHandler(peer, service));
                    onTunnelEstablished();
                    peer.read();
                }
            });

            b.bind(port).addListener(future -> {
                if (future.isSuccess()) {
                    log("Listening for connection to tunnel");
                } else {
                    error("Error listening for connection to tunnel", future.cause());
                    service.removeFromPipeline();
                }
            });
        }
    }

    public PortForwardClientService(int id, ServiceManager serviceManager, int port) {
        super("PortForwardClientService", id, serviceManager);
        this.port = port;
        setHandler(new Acceptor());
    }

}