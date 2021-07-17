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
package io.netty.channel;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * Special {@link EventExecutorGroup} which allows registering {@link Channel}s that get
 * processed for later selection during the event loop.
 *
 */
/*
    该接口比EventExecutorGroup更近一步的就是增加了register方法，EventLoopGroup实现类
    类似于一个多线程集合，用于处理多个socket，比如main Reactor用于处理连接，
    sub Reactor用于处理每个socket的各种事件，所以说需要将socket注册到EventLoopGroup中，
    也就是下面的register方法
 */

/*
    subreactor相当于下面的EventLoopGroup，里面的每个EventLoop相当于一个sub reactor线程，
    每个线程用于处理一定数量的Socket的不同的读/写事件，然后将不同的事件交给线程池去处理
    eventloop 只是继承了eventloopgroup,这里并不是说eventloop对eventloopgroup进行了功能上的扩展（因为逻辑上是不通的），
    只是说，这里是通过继承关系去进行类之间的关联
 */

/*
    该接口用扩展了netty线程池接口，提供了，netty的EventLoopGroup的服务-->用于提供对EventLoop的管理接口

 */

/*


    EventLoop表示一个Channel 线程组，用来注册channel
 */

/**
 * io.netty.channel中的类提供了 ， 对channel的管理
 * EventLoop的主要作用便是注册Channel，用于管理channel的事件
 */
//EventLoopGroup主要是用来注册Channel
public interface EventLoopGroup extends EventExecutorGroup {
    /**
     * Return the next {@link EventLoop} to use
     */
    /**
     *该方法覆盖子类的方法，并修改了返回类型，该方法的返回类型是EventLoop，EventLoop继承EventExecutor类型
     * @return
     */
    @Override
    EventLoop next();

//    @Override
//    EventExecutor next();


    /**
     * Register a {@link Channel} with this {@link EventLoop}. The returned {@link ChannelFuture}
     * will get notified once the registration was complete.
     * EventLoopGroup注册channel，实际是调用eventLoop来进行注册，
     * 当前EventLoop注册一个channel，当前eventloop来处理对应channel的任务，当注册成功后，返回通知
     */

    /*
     * 每一个Channel对应着一个SocketChannel对应一个Socket
     * 这是一个异步回调事件，方法都是异步的，所以需要有一个
     * Future作为异步回调，承载异步的执行结果
     *
     * @param channel
     * @return
     */
    ChannelFuture register(Channel channel);

    /**
     * Register a {@link Channel} with this {@link EventLoop} using a {@link ChannelFuture}. The passed
     * {@link ChannelFuture} will get notified once the registration was complete and also will get returned.
     */
    ChannelFuture register(ChannelPromise promise);

    /**
     * Register a {@link Channel} with this {@link EventLoop}. The passed {@link ChannelFuture}
     * will get notified once the registration was complete and also will get returned.
     *
     * @deprecated Use {@link #register(ChannelPromise)} instead.
     */
    @Deprecated
    ChannelFuture register(Channel channel, ChannelPromise promise);
}
