package com.justinmichaud.remotesupport.client.discovery;

import com.justinmichaud.remotesupport.client.ConnectionEventHandler;
import com.justinmichaud.remotesupport.client.tunnel.PeerConnection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;

public class DiscoveryClientHandler extends ChannelInboundHandlerAdapter {

    private final ConnectionEventHandler eh;
    private final String username;

    private boolean nameOk = false;
    public Channel channel;

    public DiscoveryClientHandler(ConnectionEventHandler eh, String username) {
        this.eh = eh;
        this.username = username;
    }

    public void connect(String username) {
        channel.writeAndFlush("connect:" + username);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        channel.writeAndFlush(username);
        eh.onDiscoveryServerConnected(this);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        eh.onDiscoveryConnectionClosed();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String serverResponse = (String) msg;

        if (serverResponse.equalsIgnoreCase("ok") && !nameOk) {
            nameOk = true;
            eh.onDiscoveryServerNameAuthenticated();
        }
        else if (serverResponse.startsWith("name_error") && !nameOk) {
            throw new RuntimeException(serverResponse.substring("name_error".length()));
        }
        else if (serverResponse.startsWith("ok:")) {
            String[] partnerDetails = serverResponse.split(":");
            if (partnerDetails.length != 5 || !partnerDetails[1].equals(username)) {
                throw new RuntimeException("Invalid data from server: " + serverResponse);
            }

            InetSocketAddress localAddress = (InetSocketAddress) channel.localAddress();
            channel.close();
            new Thread(() -> PeerConnection.runPeerConnection(false, localAddress,
                    new InetSocketAddress(partnerDetails[3], Integer.parseInt(partnerDetails[4])), eh,
                    username, partnerDetails[2])).start();
        }
        else if (serverResponse.startsWith("connect:")) {
            String[] partnerDetails = serverResponse.split(":");
            if (partnerDetails.length != 5 || !partnerDetails[1].equals(username)) {
                throw new RuntimeException("Invalid data from server: " + serverResponse);
            }

            InetSocketAddress localAddress = (InetSocketAddress) channel.localAddress();
            channel.close();
            new Thread(() -> PeerConnection.runPeerConnection(true, localAddress,
                    new InetSocketAddress(partnerDetails[3], Integer.parseInt(partnerDetails[4])), eh,
                    username, partnerDetails[2])).start();
        }
        else if (serverResponse.equalsIgnoreCase("keepalive")) {
            //TODO keepalive
        }
        else throw new RuntimeException("Invalid data from server: " + serverResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        eh.error("Discovery connection error", cause);
        ctx.close();
    }
}
