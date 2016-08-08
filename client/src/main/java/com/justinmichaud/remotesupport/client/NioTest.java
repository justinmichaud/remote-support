package com.justinmichaud.remotesupport.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.io.IOException;
import java.util.Scanner;

/*
 * Testing for NIO implementation
 */
public class NioTest {

    // Send traffic from port 4999 <> 5000 <> 5001
    private static class TcpForwardPeerHandlerConnector extends ChannelInboundHandlerAdapter {

        private volatile Channel tunnel;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Channel peer = ctx.channel();
            peer.config().setOption(ChannelOption.AUTO_READ, false);

            Bootstrap b = new Bootstrap();
            b.group(peer.eventLoop());
            b.channel(NioSocketChannel.class);
            b.handler(new TcpForwardTunnelHandler(peer));

            ChannelFuture f = b.connect("localhost", 5001);
            tunnel = f.channel();
            f.addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) throws Exception {
                    if (future.isSuccess()) {
                        System.out.println("Connected to tunnel");
                        peer.read();
                    } else {
                        System.out.println("Error connecting to tunnel - retrying");
                        f.channel().connect(f.channel().remoteAddress()).addListener(this);
                    }
                }
            });
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

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Channel peer = ctx.channel();
            peer.config().setOption(ChannelOption.AUTO_READ, false);

            ServerBootstrap b = new ServerBootstrap();
            b.group(peer.eventLoop());
            b.channel(NioServerSocketChannel.class);
            b.childHandler(new ChannelInitializer<SocketChannel>() {
               @Override
               protected void initChannel(SocketChannel ch) throws Exception {
                   if (tunnel != null) throw new IOException("The tunnel is already connected.");
                   tunnel = ch;
                   ch.pipeline().addLast(new TcpForwardTunnelHandler(peer));
                   System.out.println("Accepted connection to tunnel");
               }
            });

            b.bind(4999).addListener(future -> {
                if (future.isSuccess()) {
                    System.out.println("Listening for connection to tunnel");
                    peer.read();
                }
                else {
                    System.out.println("Error listening for connection to tunnel");
                    future.cause().printStackTrace();
                    peer.close();
                }
            });
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
            if (peer != null) closeOnFlush(peer);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Channel tunnel = ctx.channel();

            if (peer == null || !peer.isActive()) {
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

    public static void server() throws InterruptedException {
        System.out.println("Start server");

        final SslContext ctx = null;

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);
            b.option(ChannelOption.SO_BACKLOG, 1);
            b.handler(new LoggingHandler(LogLevel.INFO));
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));
                    if (ctx != null) pipeline.addLast(ctx.newHandler(ch.alloc()));
                    pipeline.addLast(new TcpForwardPeerHandlerConnector());
                }
            });

            ChannelFuture f = b.bind(5000).sync();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    f.channel().close().sync();
                } catch (InterruptedException e) {}
            }));

            while (!f.channel().closeFuture().isDone()) {
                Thread.sleep(100);
            }
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

        System.out.println("Closed");
    }

    public static void client() throws InterruptedException {
        System.out.println("Start client");

        final SslContext ctx = null;

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group);
            b.channel(NioSocketChannel.class);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    if (ctx != null) pipeline.addLast(ctx.newHandler(ch.alloc()));
                    pipeline.addLast(new TcpForwardPeerHandlerAcceptor());
                }
            });

            ChannelFuture f = b.connect("localhost", 5000).sync();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    f.channel().close().sync();
                } catch (InterruptedException e) {}
            }));

            while (!f.channel().closeFuture().isDone()) {
                Thread.sleep(100);
            }
        } finally {
            group.shutdownGracefully();
        }

        System.out.println("Closed");
    }

    public static void main(String... args) throws InterruptedException {
        System.out.println("Would you like to start a server [s] or client [c]?");
        boolean server = new Scanner(System.in).nextLine().equalsIgnoreCase("s");

        if (server) server();
        else client();
    }

}
