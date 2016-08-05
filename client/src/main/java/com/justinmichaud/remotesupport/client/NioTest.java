package com.justinmichaud.remotesupport.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.util.Scanner;

/*
 * Testing for NIO implementation
 */
public class NioTest {

    private static class NioServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            System.out.print("From client: ");
            System.out.println(in.toString(CharsetUtil.UTF_8));
            ctx.writeAndFlush(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }

    }

    private static class NioClientHandler extends ChannelInboundHandlerAdapter {

        public final ByteBuf buffer;

        public NioClientHandler() {
            buffer = Unpooled.buffer();
            buffer.retain();
            buffer.writeBytes("Hello World!".getBytes(CharsetUtil.UTF_8));
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(buffer);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            buffer.release();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            try {
                System.out.print("From server: ");
                System.out.println(in.toString(io.netty.util.CharsetUtil.US_ASCII));
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
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
                    if (ctx != null) pipeline.addLast(ctx.newHandler(ch.alloc()));
                    pipeline.addLast(new NioServerHandler());
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
                    pipeline.addLast(new NioClientHandler());
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
