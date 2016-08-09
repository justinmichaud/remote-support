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
public class NioTest {

    private static class TcpForwardPeerHandlerConnector extends TcpForwardPeerHandler {

        public TcpForwardPeerHandlerConnector(EventLoopGroup group, NioTestTunnelEventHandler eh) {
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

    private static class TcpForwardPeerHandlerAcceptor extends TcpForwardPeerHandler {

        public TcpForwardPeerHandlerAcceptor(EventLoopGroup group, NioTestTunnelEventHandler eh) {
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
                }
                else {
                    eh.error("Error listening for connection to tunnel", future.cause());
                    peer.close();
                }
            });
        }
    }

    private static abstract class TcpForwardPeerHandler extends ChannelInboundHandlerAdapter {

        protected volatile Channel tunnel;
        protected final EventLoopGroup group;
        protected final NioTestTunnelEventHandler eh;

        public TcpForwardPeerHandler(EventLoopGroup group, NioTestTunnelEventHandler eh) {
            super();
            this.group = group;
            this.eh = eh;
        }

        protected abstract void establishTunnel(Channel peer);

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Channel peer = ctx.channel();
            peer.config().setOption(ChannelOption.AUTO_READ, false);

            establishTunnel(ctx.channel());

            // Wait until we are connected to the tunnel
            // This handler must be on a separate thread group, otherwise it will block the event thread
            while (tunnel == null && !peer.eventLoop().isShuttingDown()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    eh.debug("Interrupted waiting for tunnel");
                    peer.close();
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            eh.log("Connection to peer closed");
            if (tunnel != null) closeOnFlush(tunnel);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Channel peer = ctx.channel();

            if (tunnel == null || !tunnel.isActive()) {
                eh.debug("Attempted to read from a tunnel that is closed");
                closeOnFlush(peer);
                return;
            }

            tunnel.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess()) peer.read();
                else {
                    eh.error("Error reading from peer", future.cause());
                    peer.close();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            eh.error("Peer connection error", cause);
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
        private final NioTestTunnelEventHandler eh;

        public TcpForwardTunnelHandler(Channel peer, NioTestTunnelEventHandler eh) {
            this.peer = peer;
            this.eh = eh;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.channel().config().setOption(ChannelOption.AUTO_READ, false);
            ctx.read();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            eh.debug("Connection to tunnel closed");
            if (peer != null) closeOnFlush(peer);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Channel tunnel = ctx.channel();

            if (peer == null || !peer.isActive()) {
                eh.debug("Attempted to read from a peer that is closed");
                closeOnFlush(tunnel);
                return;
            }

            peer.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess()) tunnel.read();
                else {
                    eh.debugError("Error reading from tunnel", future.cause());
                    tunnel.close();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            eh.debugError("Tunnel error", cause);
            closeOnFlush(ctx.channel());
        }

        public static void closeOnFlush(Channel ch) {
            if (ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    public static void establishPeerConnection(boolean server, InetSocketAddress us,
                                               InetSocketAddress peer, NioTestTunnelEventHandler eh) {
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
                            eh.debug("SSL handshake done");

                            if (server) pipeline.addLast(peerGroup, new TcpForwardPeerHandlerAcceptor(tunnelGroup, eh));
                            else pipeline.addLast(peerGroup, new TcpForwardPeerHandlerConnector(tunnelGroup, eh));

                            pipeline.fireChannelActive();
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
            peerGroup.shutdownGracefully().syncUninterruptibly();
            tunnelGroup.shutdownGracefully().syncUninterruptibly();
        }

        eh.connectionClosed();
    }

    private static SSLEngine getSSLEngine(boolean server, NioTestTunnelEventHandler eh) throws GeneralSecurityException, IOException, OperatorCreationException {
        String alias = server?"server":"client";
        String partnerAlias = server?"client":"server";

        SSLEngine engine = NioTestTlsManager.getSSLContext(partnerAlias,
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

        NioTestTunnelEventHandler eh = new NioTestTunnelEventHandler();

        if (server) establishPeerConnection(true, new InetSocketAddress("localhost", 5000), new InetSocketAddress("localhost", 8000), eh);
        else establishPeerConnection(false, new InetSocketAddress("localhost", 8000), new InetSocketAddress("localhost", 5000), eh);

        System.out.println("Main Closed.");
    }
}
