package io.netty.example.http.websocketx.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * @Author:王洋
 * @Date:Created in 2020/6/16
 * @Desc
 */
public class WebSocketClientTestHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        System.out.println(msg.toString() + "99999999");
        super.write(ctx, msg, promise);
    }
}
