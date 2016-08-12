package com.justinmichaud.remotesupport.client.tunnel;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ControlHandler extends ChannelInboundHandlerAdapter {

    private static final int MAGIC_OPEN_PORT = 1,
            MAGIC_CLOSE_SERVICE=2;

    private ByteBuf buf;
    private final ServiceManager serviceManager;

    public ControlHandler(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        buf = Unpooled.buffer();
        buf.retain();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (buf != null) buf.release();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel peer = ctx.channel();

        ServiceHeader serviceHeader = (ServiceHeader) msg;
        if (serviceHeader.id != 0) {
            ctx.fireChannelRead(serviceHeader);
            return;
        }

        if (serviceHeader.buf.readableBytes() < 1) {
            serviceManager.eh.log("Error: Service manager message too small - no magic number");
            return;
        }

        int magic = serviceHeader.buf.readByte();
        if (magic == MAGIC_OPEN_PORT) {
            if (serviceHeader.buf.readableBytes() < 3) {
                serviceManager.eh.log("Error: Service manager message too small - not enough info to open port");
                return;
            }

            int newService = serviceHeader.buf.readByte()&0xFF;
            int localPort = ((serviceHeader.buf.readByte()&0xFF) << 8)
                    | (serviceHeader.buf.readByte()&0xFF);

            serviceManager.eh.log("Asked by peer to open port " + localPort + " on service " + newService);
            try {
                serviceManager.addService(new PortForwardServerService(newService, serviceManager, localPort));
            } catch (IllegalArgumentException e) {
                serviceManager.eh.error("Could not add service " + newService, e);
            }
        }
        else if (magic == MAGIC_CLOSE_SERVICE) {
            if (serviceHeader.buf.readableBytes() < 1) {
                serviceManager.eh.log("Error: Service manager message too small - no service to close");
                return;
            }
            int toClose = serviceHeader.buf.readByte();
            serviceManager.eh.log("Asked by peer to close service " + toClose);
            try {
                serviceManager.removeService(toClose);
            } catch (IllegalArgumentException e) {
                serviceManager.eh.error("Could not remove service " + toClose, e);
            }
        }
        else {
            serviceManager.eh.log("Error: Service manager unknown magic value " + magic);
        }
    }

    public void peerOpenPort(int serviceId, int remotePort) {
        buf.writeByte(MAGIC_OPEN_PORT);
        buf.writeByte(serviceId);
        buf.writeByte((remotePort >> 8) & 0xFF);
        buf.writeByte(remotePort & 0xFF);
        buf.retain();

        ServiceHeader serviceHeader = new ServiceHeader();
        serviceHeader.buf = buf;
        serviceHeader.id = 0;

        serviceManager.peer.writeAndFlush(serviceHeader);
    }

    public void peerCloseService(int serviceId) {
        buf.writeByte(MAGIC_CLOSE_SERVICE);
        buf.writeByte(serviceId);
        buf.retain();

        ServiceHeader serviceHeader = new ServiceHeader();
        serviceHeader.buf = buf;
        serviceHeader.id = 0;

        serviceManager.peer.writeAndFlush(serviceHeader);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        serviceManager.eh.error("Service Manager error", cause);
        ctx.close();
    }
}
