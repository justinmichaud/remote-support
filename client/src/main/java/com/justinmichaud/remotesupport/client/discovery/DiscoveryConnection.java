package com.justinmichaud.remotesupport.client.discovery;

import com.justinmichaud.remotesupport.client.ConnectionEventHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;

public class DiscoveryConnection {

    public static void runDiscoveryConnection(InetSocketAddress discoveryServer, String username,
                                              ConnectionEventHandler eh) {
        final ThreadFactory connectFactory = new DefaultThreadFactory("discovery");
        final EventLoopGroup group = new NioEventLoopGroup(1, connectFactory, NioUdtProvider.MESSAGE_PROVIDER);
        try {
            Bootstrap b = new Bootstrap();
            b.option(ChannelOption.SO_REUSEADDR, true);
            b.group(group);
            b.channelFactory(NioUdtProvider.BYTE_CONNECTOR);
            b.handler(new ChannelInitializer<UdtChannel>() {
                @Override
                protected void initChannel(UdtChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();

                    //Inbound
                    pipeline.addLast(new LengthFieldBasedFrameDecoder(255, 0, 1, 0, 1));
                    pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                    //Outbound
                    pipeline.addLast(new LengthFieldPrepender(1));
                    pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));

                    pipeline.addLast(new DiscoveryClientHandler(eh, username));
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    eh.error("Uncaught exception initializing discovery connection", cause);
                }
            });

            ChannelFuture f = b.connect(discoveryServer);
            try {
                f.sync();
            } catch (Exception e) {
                eh.error("Uncaught discovery error: ", e);
                eh.onDiscoveryConnectionClosed();
                return;
            }

            if (f.isSuccess()) eh.log("Connected to discovery server");
            else {
                eh.error("Could not connect to discovery server", f.cause());
                f.channel().close();
                return;
            }

            try {
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                eh.error("Interrupted while waiting for connection to close", e);
            }

            eh.debug("Discovery Channel closed");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
            eh.onDiscoveryConnectionClosed();
        }
    }

}
