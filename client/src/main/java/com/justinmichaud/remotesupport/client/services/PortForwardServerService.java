package com.justinmichaud.remotesupport.client.services;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.nio.NioSocketChannel;

public class PortForwardServerService extends Service {

    public final int port;

    public class Connector extends PortForwardServiceHandler {

        public Connector() {
            super(PortForwardServerService.this);
        }

        @Override
        protected void establishTunnel(Channel peer) {
            Bootstrap b = new Bootstrap();
            b.group(makeEventLoopGroup());
            b.channel(NioSocketChannel.class);
            b.handler(new PortForwardServiceTunnelHandler(peer, service));

            ChannelFuture f = b.connect("localhost", port);
            tunnel = f.channel();
            f.addListener(future -> {
                if (future.isSuccess()) {
                    log("Connected to tunnel");
                    peer.read();
                } else {
                    error("Error connecting to tunnel", future.cause());
                    service.removeFromPipeline();
                }
            });
        }
    }

    public PortForwardServerService(int id, ServiceManager serviceManager, int port) {
        super("PortForwardServerService", id, serviceManager);
        this.port = port;
        setHandler(new Connector());
    }

}
