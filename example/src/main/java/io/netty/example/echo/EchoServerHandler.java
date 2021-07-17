/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.TimeUnit;

/**
 * Handler implementation for the echo server.
 */
/**
 * 这个server/client的用途是将收取到的信息再发送回客户端
 * 客户端收到后，再发送回服务器，这样循环往复的去操作。
 */
@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {


    /**
     * 这里同样是入站处理
     * @param ctx
     * @param msg
     */
     /*
        从channel上读取 数据时触发该事件
        msg是读取的事件信息，转成ByteBuf对象
        ByteBuf是netty数据容器
      */
     /*

         client 分2次发出的信息，这里直接拼起来
        输出 ：你好你好你好你好你好你好你好你好你好你好哈哈哈哈哈
         msg类型：PooledUnsafeDirectByteBuf(ridx: 0, widx: 75, cap: 1024)
      */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("channelread-----------------------------");
        System.out.println(msg);

        final ChannelHandlerContext context = ctx;


//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(); //自己指定的 bytebuf
//                buffer.writeBytes("你好".getBytes());
//                context.write(buffer);
//            }
//        }).start();
//
//        try {
//            TimeUnit.SECONDS.sleep(2L);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        System.gc();

        /**
         * 这里发生异常，无法进行处理
         *
         */
        System.out.println(msg == null);
        if (msg != null) {
            ByteBuf buf = (ByteBuf) msg;
            byte[] b = new byte[100];
            buf.getBytes(0, b);
            final String s = new String(b);
            System.out.println(s);
        }

        try {
            TimeUnit.SECONDS.sleep(2l);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ctx.write(msg);
//        ctx.close();
    }

    /**
     * 读取完成之后触发 该事件
     *
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        System.out.println("channelreadcomplete-----------------------------");
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised
        cause.printStackTrace();
        ctx.close();
    }
}
