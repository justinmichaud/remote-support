package com.justinmichaud.remotesupport.client.tunnel;

import com.justinmichaud.remotesupport.client.ConnectionEventHandler;
import com.justinmichaud.remotesupport.client.tunnel.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import org.bouncycastle.operator.OperatorCreationException;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.concurrent.ThreadFactory;

/*
 * Testing for NIO implementation
 */
public class PeerConnection {

    public static void runPeerConnection(boolean server, InetSocketAddress us, InetSocketAddress peer,
                                         ConnectionEventHandler eh, String alias, String partnerAlias) {
        eh.log("Connection as " + (server? "server":"client") + ": " + alias + " to " + partnerAlias);

        final SSLEngine engine;
        try {
             engine = getSSLEngine(server, eh, alias, partnerAlias);
        } catch (GeneralSecurityException e) {
            eh.error(e.getMessage(), e);
            return;
        } catch (OperatorCreationException|IOException e) {
            eh.error("Error loading SSL", e);
            return;
        }

        final ThreadFactory connectFactory = new DefaultThreadFactory("rendezvous");
        final EventLoopGroup rendezvousGroup = new NioEventLoopGroup(1, connectFactory, NioUdtProvider.MESSAGE_PROVIDER);
        final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.option(ChannelOption.SO_REUSEADDR, true);
            b.group(rendezvousGroup);
            b.channelFactory(NioUdtProvider.BYTE_RENDEZVOUS);
            b.handler(new ChannelInitializer<UdtChannel>() {
                @Override
                protected void initChannel(UdtChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();

                    SslHandler sslHandler = new SslHandler(engine);
                    Future<Channel> handshakeFuture = sslHandler.handshakeFuture();
                    pipeline.addLast(sslHandler);

                    handshakeFuture.addListener(future -> {
                        try {
                            if (future.isSuccess()) {
                                eh.log("Connected to peer!");

                                //Inbound
                                pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                                pipeline.addLast(new ServiceHeaderDecoder());

                                //Outbound
                                pipeline.addLast(new LengthFieldPrepender(2));
                                pipeline.addLast(new ServiceHeaderEncoder());

                                final ServiceManager serviceManager = new ServiceManager(eh, ch, workerGroup);

                                //Services are added before here by the service manager
                                //Catch-all, just in case
                                pipeline.addLast("last", new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        eh.log("Error: Message with id " + ((ServiceHeader) msg).id + " was not handled.");
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        eh.error("Uncaught exception processing tunnel", cause);
                                    }
                                });

                                eh.onPeerConnected(serviceManager);
                            } else {
                                eh.error("Error during SSL handshake", future.cause());
                                ch.close();
                            }
                        } catch (Exception e) {
                            eh.error("Uncaught exception initializing peer tunnel tunnel", e);
                        }
                    });
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    eh.error("Uncaught exception initializing peer connection", cause);
                }
            });

            ChannelFuture f = b.connect(peer, us);
            eh.onConnectingToPeer(f);
            try {
                f.sync();
            } catch (Exception e) {
                eh.error("Uncaught peer error: ", e);
            }

            if (f.isSuccess()) eh.log("Connected to peer - Authenticating");
            else {
                eh.error("Could not connect to peer", f.cause());
                f.channel().close();
                return;
            }

            try {
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                eh.error("Interrupted while waiting for connection to close", e);
            }

            eh.debug("Channel closed");
        } finally {
            rendezvousGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }

        eh.onPeerConnectionClosed();
    }

    private static SSLEngine getSSLEngine(boolean server, ConnectionEventHandler eh, String alias, String partnerAlias)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        SSLEngine engine = TlsManager.getSSLContext(partnerAlias,
                new File(alias.replaceAll("\\W+", "") + "_private.jks"),
                new File(alias.replaceAll("\\W+", "") + "_trusted.jks"),
                eh).createSSLEngine();
        engine.setUseClientMode(server);
        engine.setNeedClientAuth(true);

        return engine;
    }
}
