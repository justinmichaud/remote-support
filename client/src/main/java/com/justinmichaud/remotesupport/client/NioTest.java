package com.justinmichaud.remotesupport.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.bouncycastle.operator.OperatorCreationException;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

/*
 * Testing for NIO implementation
 */
public class NioTest {

    private static class TcpForwardPeerHandlerConnector extends ChannelInboundHandlerAdapter {

        private volatile Channel tunnel;
        private final EventLoopGroup group;

        public TcpForwardPeerHandlerConnector(EventLoopGroup group) {
            super();
            this.group = group;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Channel peer = ctx.channel();
            peer.config().setOption(ChannelOption.AUTO_READ, false);

            Bootstrap b = new Bootstrap();
            b.group(group);
            b.channel(NioSocketChannel.class);
            b.handler(new TcpForwardTunnelHandler(peer));

            ChannelFuture f = b.connect("localhost", 22);
            tunnel = f.channel();
            f.addListener(future -> {
                if (future.isSuccess()) {
                    System.out.println("Connected to tunnel");
                    peer.read();
                } else {
                    System.out.println("Error connecting to tunnel");
                    peer.close();
                }
            });

            // Wait until we are connected to the tunnel
            // This handler must be on a separate thread group, otherwise it will block the event thread
            while (tunnel == null && !peer.eventLoop().isShuttingDown()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    System.out.println("Interrupted waiting for tunnel");
                    peer.close();
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (tunnel != null) closeOnFlush(tunnel);
            System.out.println("Connection to peer closed");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Channel peer = ctx.channel();

            if (tunnel == null || !tunnel.isActive()) {
                System.out.println("Attempted to read from a tunnel that is closed");
                closeOnFlush(peer);
                return;
            }

            tunnel.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess()) peer.read();
                else {
                    System.out.println("Error reading from peer");
                    future.cause().printStackTrace();
                    peer.close();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            closeOnFlush(ctx.channel());
        }

        public static void closeOnFlush(Channel ch) {
            if (ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private static class TcpForwardPeerHandlerAcceptor extends ChannelInboundHandlerAdapter {

        private volatile Channel tunnel;
        private final EventLoopGroup group;

        public TcpForwardPeerHandlerAcceptor(EventLoopGroup group) {
            super();
            this.group = group;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Channel peer = ctx.channel();
            peer.config().setOption(ChannelOption.AUTO_READ, false);

            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_REUSEADDR, true);
            b.group(group);
            b.channel(NioServerSocketChannel.class);
            b.childHandler(new ChannelInitializer<SocketChannel>() {
               @Override
               protected void initChannel(SocketChannel ch) throws Exception {
                   if (tunnel != null) throw new IOException("The tunnel is already connected.");
                   System.out.println("Accepted connection to tunnel");
                   tunnel = ch;
                   ch.pipeline().addLast(new TcpForwardTunnelHandler(peer));
                   peer.read();
               }
            });

            b.bind(4999).addListener(future -> {
                if (future.isSuccess()) {
                    System.out.println("Listening for connection to tunnel");
                }
                else {
                    System.out.println("Error listening for connection to tunnel");
                    future.cause().printStackTrace();
                    peer.close();
                }
            });

            // Wait until we are connected to the tunnel
            // This handler must be on a separate thread group, otherwise it will block the event thread
            while (tunnel == null && !peer.eventLoop().isShuttingDown()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    System.out.println("Interrupted waiting for tunnel");
                    peer.close();
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (tunnel != null) closeOnFlush(tunnel);
            System.out.println("Connection to peer closed");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Channel peer = ctx.channel();

            if (tunnel == null || !tunnel.isActive()) {
                System.out.println("Attempted to read from a tunnel that is closed");
                closeOnFlush(peer);
                return;
            }

            tunnel.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess()) peer.read();
                else {
                    System.out.println("Error reading from peer");
                    future.cause().printStackTrace();
                    peer.close();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            closeOnFlush(ctx.channel());
        }

        public static void closeOnFlush(Channel ch) {
            if (ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private static class TcpForwardTunnelHandler extends ChannelInboundHandlerAdapter {

        private volatile Channel peer;

        public TcpForwardTunnelHandler(Channel peer) {
            this.peer = peer;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.channel().config().setOption(ChannelOption.AUTO_READ, false);
            ctx.read();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("Connection to tunnel closed");
            if (peer != null) closeOnFlush(peer);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Channel tunnel = ctx.channel();

            if (peer == null || !peer.isActive()) {
                System.out.println("Attempted to read from a peer that is closed");
                closeOnFlush(tunnel);
                return;
            }

            peer.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess()) tunnel.read();
                else {
                    System.out.println("Error reading from tunnel");
                    future.cause().printStackTrace();
                    tunnel.close();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            closeOnFlush(ctx.channel());
        }

        public static void closeOnFlush(Channel ch) {
            if (ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    public static void rv(boolean server, InetSocketAddress us, InetSocketAddress peer)
            throws InterruptedException, GeneralSecurityException, IOException, OperatorCreationException {
        System.out.println("Start " + (server? "server":"client"));

        final SSLEngine engine = getSSLEngine(server);

        final ThreadFactory connectFactory = new DefaultThreadFactory("rendezvous");
        final EventLoopGroup rendezvousGroup = new NioEventLoopGroup(1, connectFactory, NioUdtProvider.MESSAGE_PROVIDER);
        final EventLoopGroup peerGroup = new NioEventLoopGroup();
        final EventLoopGroup tunnelGroup = new NioEventLoopGroup();
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
                            System.out.println("SSL Handshake complete");

                            if (server) pipeline.addLast(peerGroup, new TcpForwardPeerHandlerAcceptor(tunnelGroup));
                            else pipeline.addLast(peerGroup, new TcpForwardPeerHandlerConnector(tunnelGroup));

                            pipeline.fireChannelActive();
                        }
                        else {
                            System.out.println("Error during SSL handshake");
                            future.cause().printStackTrace();
                            ch.close();
                        }
                    });
                }
            });

            ChannelFuture f = b.connect(peer, us).sync();
            System.out.println("Connected to peer - Authenticating");

            //Shut down gracefully when user presses ctrl+c
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    f.channel().close().sync();
                } catch (InterruptedException|RejectedExecutionException e) {}
            }));

            f.channel().closeFuture().sync();

            System.out.println("Done.");
        } finally {
            rendezvousGroup.shutdownGracefully();
            peerGroup.shutdownGracefully();
            tunnelGroup.shutdownGracefully();
        }

        System.out.println("Closed");
    }

    private static SSLEngine getSSLEngine(boolean server) throws GeneralSecurityException, IOException, OperatorCreationException {
        String alias = server?"server":"client";
        String partnerAlias = server?"client":"server";

        SSLEngine engine = NioTestTlsManager.getSSLContext(partnerAlias,
                new File(alias.replaceAll("\\W+", "") + "_private.jks"),
                new File(alias.replaceAll("\\W+", "") + "_trusted.jks"),
                prompt -> {
                    System.out.println(prompt);
                    return new Scanner(System.in).nextLine();
                }).createSSLEngine();
        engine.setUseClientMode(server);
        engine.setNeedClientAuth(true);

        return engine;
    }

    public static void main(String... args) throws InterruptedException, GeneralSecurityException, IOException, OperatorCreationException {
        System.out.println("Would you like to start a server [s] or client [c]?");
        boolean server = new Scanner(System.in).nextLine().equalsIgnoreCase("s");

        if (server) rv(true, new InetSocketAddress("localhost", 5000), new InetSocketAddress("localhost", 8000));
        else rv(false, new InetSocketAddress("localhost", 8000), new InetSocketAddress("localhost", 5000));
    }

}
