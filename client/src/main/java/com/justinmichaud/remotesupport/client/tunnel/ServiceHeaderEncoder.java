package com.justinmichaud.remotesupport.client.tunnel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ServiceHeaderEncoder extends MessageToByteEncoder<ServiceHeader> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ServiceHeader msg, ByteBuf out) throws Exception {
        out.writeByte(msg.id&0xFF);
        out.writeBytes(msg.buf);
    }
}
