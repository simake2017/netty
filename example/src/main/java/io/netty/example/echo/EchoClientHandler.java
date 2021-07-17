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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler implementation for the echo client.  It initiates the ping-pong
 * traffic between the echo client and server by sending the first message to
 * the server.
 */
public class EchoClientHandler extends ChannelInboundHandlerAdapter {

    private static final AtomicInteger i = new AtomicInteger(0);

    public ChannelHandlerContext context = null;

    private final ByteBuf firstMessage;
    /**
     * Creates a client-side handler.
     */
    public EchoClientHandler() {

        try {
            TimeUnit.SECONDS.sleep(15L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //netty 的内存信息承载主要使用ByteBuf，有直接内存与堆内存的区别，
        //内存的分配使用Unpooled类进行分配。
        firstMessage = Unpooled.buffer(EchoClient.SIZE);
        System.out.println(firstMessage.capacity() + "ddddddddddddd"); // 执行bytebuf的容量信息
//        for (int i = 0; i < firstMessage.capacity(); i ++) {
//            firstMessage.writeBytes("你好".getBytes());
//        }
        for (int i = 0; i < 10; i ++) {
            /*
            * 这里往Bytebuf中写入内容，写入的内存区域不能超过
            * 创建的缓存空间，不然会有异常。
            * */
            firstMessage.writeBytes("你好".getBytes()); //输出字节，写入字节
        }

        System.out.println("sssssssssssssssssss");

//        Scanner scanner = new Scanner(System.in);
//        while (true) {
//            System.out.println("请输入内容");
//            String str = scanner.nextLine();
//            if (str.equals("-1"))
//                break;
//            else {
//                System.out.println(str);
//                byte b = 1;
//                firstMessage.writeByte(b);
//            }
//        }
    }

    /**
     * 首先在连接服务器时，触发active事件，这是第一个触发的事件
     * @param ctx
     */
    /*

        wangyang
        这里在 连接成功之后 会触发到这里

     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {

        i.incrementAndGet();
        System.out.println(i.get() + "ddddddddddddddddd");
        ctx.write(firstMessage);//传递到下一个 Handler中继续处理

//        ByteBuf secondMessage= Unpooled.buffer(EchoClient.SIZE);
        ByteBuf secondMessage= ctx.alloc().buffer(EchoClient.SIZE);
        secondMessage.writeBytes("哈哈哈哈哈".getBytes()); //输出字节，写入字节



        ctx.write(secondMessage);//

        ctx.flush();


        context = ctx;
//        System.out.println(ctx.hashCode() + "|||||||||||||||");


    }

    /**
     * 这是一个入站处理器，用于处理入站事件，
     * 获取入站信息，并且能够
     * @param ctx
     * @param msg
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        //读取信息之后，将读取到的信息再写回去
        i.incrementAndGet();
        System.out.println(i.get() + "ddddddddddddddddd222222");
//        ctx.write(msg);

        if (i.get() >= 4) {
//            ReferenceCountUtil.release(msg);
            /*
            warn级别的日志
            14:11:18.183 [nioEventLoopGroup-2-1] WARN
             io.netty.util.ReferenceCountUtil - Failed to release a message: PooledUnsafeDirectByteBuf(freed)
             */
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
       //读取完成之后，进行flush刷新
        i.incrementAndGet();
        System.out.println(i.get() + "ddddddddddddddddd1111111111");
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        System.out.println("exceptionCaught------------");
        cause.printStackTrace();
        ctx.close();
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("inactive .......");
    }
}

