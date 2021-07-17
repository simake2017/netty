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

import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * Will handle all the I/O operations for a {@link Channel} once registered.
 *
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 *
 */

/*
 *    EventLoop继承自EventExecutor(OrderedEventExecutor继承自EventExecutor)
 *    每个eventloop都对应一个线程  -->
 */

/*
EventLoop的功能主要继承自他的父类EventLoopGroup,众多的子类，通过统一的父类中的接口
进行实现自己的功能--> 注册Channel等，每个EventLoop只要实现自己的paraen()接口就可以了
找到自己的父类 --> EventLoopGroup

 */

/*
    每个eventloop 都是一个reactor线程组中的线程，用于处理注册对应的channel
    当EventLoopGroup将channel注册到对应的EventLoop，则由对应的EventLoop进行处理
 */

//EventLoop的功能主要是获取parent
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    @Override
    EventLoopGroup parent();
}
