package io.netty.example.echo;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author:王洋
 * @Date:Created in 2018/5/8
 */
/*
    netty中的事件是很多的，read 和write事件 会有重复，在 inbound和outbound中
    都会有同样的事件触发，具体的触发顺序

    handler都有一个固定的顺序 ，就是1 --> 2 -->3 -->4

    对于入站事件，是 正序处理
    对于出站事件，是 倒序处理

    入站handler与出站handler中可能都有read 事件处理

 */

/*
    说一下这里的逻辑，在pipeline中的每个节点都是ChannelHandlerContext 节点，当某个节点
    调用事件（比如wrie事件）时，会触发到这个节点的invokeWrite，最终调用到对应ChannelHandler
    的wrie,当handler继承自Adapter时，对应write方法中，默认继续调用ctx.write，这样会把这个事件
    信息继续传给下一个节点，如果想在当前节点处理，那么就重写对应的方法即可。

 */
public class EchoClientOutHandler extends ChannelOutboundHandlerAdapter {

    private static final AtomicInteger ai = new AtomicInteger();

    public ChannelHandlerContext context = null;


    public EchoClientOutHandler() {

        ai.incrementAndGet();
        System.out.println(ai.get() + "-------------");
    }

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        System.out.println("outwrite-----------------------------");
        this.context = ctx;
        System.out.println(ai.get() + "==============dddddddddddddddddddddddd");
//        System.out.println(ctx.hashCode() + "|" + msg.hashCode() + "|" + promise.hashCode());
        ctx.write(msg, promise); // -->如果这里注释掉，那么整个信息就传递不过去
    }


    /**
     * 当从 channel读取数据的时候调用触发  ，该事件
     *
     *
     */
    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        System.out.println("outhandlerread---------------------");
        ctx.read(); // netty事件需要传播，如果这里不调用read，那么该事件不会传播到 ClinetHandler中的read
        //事件中去
    }







}
