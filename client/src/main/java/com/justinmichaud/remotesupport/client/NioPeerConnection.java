package com.justinmichaud.remotesupport.client;

import com.justinmichaud.remotesupport.client.services.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import org.bouncycastle.operator.OperatorCreationException;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import java.util.concurrent.ThreadFactory;

/*
 * Testing for NIO implementation
 */
public class NioPeerConnection {

    public static void runPeerConnection(boolean server, InetSocketAddress us,
                                         InetSocketAddress peer, TunnelEventHandler eh) {
        eh.log("Start: " + (server? "server":"client"));

        final SSLEngine engine;
        try {
             engine = getSSLEngine(server, eh);
        } catch (GeneralSecurityException e) {
            eh.error(e.getMessage(), e);
            return;
        } catch (OperatorCreationException|IOException e) {
            eh.error("Error loading SSL", e);
            return;
        }

        final ThreadFactory connectFactory = new DefaultThreadFactory("rendezvous");
        final EventLoopGroup rendezvousGroup = new NioEventLoopGroup(1, connectFactory, NioUdtProvider.MESSAGE_PROVIDER);
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
                        if (future.isSuccess()) {
                            eh.debug("SSL handshake done");

                            final ServiceManager serviceManager = new ServiceManager(eh, pipeline);

                            if (server) serviceManager.addService(new PortForwardServerService(1, serviceManager, 22));
                            else serviceManager.addService(new PortForwardClientService(1, serviceManager, 4999));
                        }
                        else {
                            eh.error("Error during SSL handshake", future.cause());
                            ch.close();
                        }
                    });
                }
            });

            ChannelFuture f = b.connect(peer, us);
            eh.start(f);
            try {
                f.sync();
            } catch (Exception e) {}

            if (f.isSuccess()) eh.log("Connected to peer - Authenticating");
            else {
                eh.error("Could not connect to peer", f.cause());
                f.channel().close();
                return;
            }

            f.channel().closeFuture().sync();

            eh.debug("Channel closed");
        } catch (InterruptedException e) {
            eh.error("Interrupted while waiting for connection to close", e);
        } finally {
            rendezvousGroup.shutdownGracefully().syncUninterruptibly();
        }

        eh.connectionClosed();
    }

    private static SSLEngine getSSLEngine(boolean server, TunnelEventHandler eh) throws GeneralSecurityException, IOException, OperatorCreationException {
        String alias = server?"server":"client";
        String partnerAlias = server?"client":"server";

        SSLEngine engine = TlsManager.getSSLContext(partnerAlias,
                new File(alias.replaceAll("\\W+", "") + "_private.jks"),
                new File(alias.replaceAll("\\W+", "") + "_trusted.jks"),
                eh).createSSLEngine();
        engine.setUseClientMode(server);
        engine.setNeedClientAuth(true);

        return engine;
    }

    public static void main(String... args) {
        System.out.println("Would you like to start a server [s] or client [c]?");
        boolean server = new Scanner(System.in).nextLine().equalsIgnoreCase("s");

        TunnelEventHandler eh = new TunnelEventHandler();

        if (server) runPeerConnection(true, new InetSocketAddress("localhost", 5000), new InetSocketAddress("localhost", 8000), eh);
        else runPeerConnection(false, new InetSocketAddress("localhost", 8000), new InetSocketAddress("localhost", 5000), eh);

        System.out.println("Main Closed.");
    }
}
