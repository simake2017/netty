/*
* Copyright 2014 The Netty Project
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

/*
    context 接口继承 InboundInvoker 与 OutboundInvoker ,
       可以触发对应 事件
 */
final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {

    /**
     * 对应的 handler 类
     */
    private final ChannelHandler handler;

    /**
     * 通过 判断是否是 inbound 和 outbound handler 进行 判断
     * @param pipeline
     * @param executor
     * @param name
     * @param handler
     */
    /*
        创建一个默认的    HandlerContext   ，判断 handler是否继承 ChannelInboundHandler接口，决定是否是isInBound --> 入站
        判断handler是否继承ChannelOutboundHandler 决定 是否 isoutBound --> 出站

     */
    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
        super(pipeline, executor, name, isInbound(handler), isOutbound(handler));
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }

    private static boolean isInbound(ChannelHandler handler) {
        return handler instanceof ChannelInboundHandler;
    }

    private static boolean isOutbound(ChannelHandler handler) {
        return handler instanceof ChannelOutboundHandler;
    }
}
