package com.justinmichaud.remotesupport.client.services;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class ServiceHeaderDecoder extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        ServiceHeader s = new ServiceHeader();
        s.id = (msg.readByte()&0xFF);
        s.buf = msg;
        msg.retain();
        out.add(s);
    }
}
