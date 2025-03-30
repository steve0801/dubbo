/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Codec2;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.MultiMessage;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.FrameworkModel;

import java.io.IOException;

import static org.apache.dubbo.rpc.Constants.INPUT_KEY;
import static org.apache.dubbo.rpc.Constants.OUTPUT_KEY;

/**
 * DubboCountCodec 类实现了 Codec2 接口，用于处理 Dubbo 协议的编解码操作，
 * 同时记录请求和响应消息的长度。
 */
public final class DubboCountCodec implements Codec2 {

    /**
     * 用于实际编解码操作的 DubboCodec 实例
     */
    private DubboCodec codec;
    /**
     * 框架模型实例，用于初始化 DubboCodec
     */
    private FrameworkModel frameworkModel;

    /**
     * 构造函数，使用传入的 FrameworkModel 初始化 DubboCodec 实例
     *
     * @param frameworkModel 框架模型实例
     */
    public DubboCountCodec(FrameworkModel frameworkModel) {
        this.frameworkModel = frameworkModel;
        codec = new DubboCodec(frameworkModel);
    }

    /**
     * 对消息进行编码操作，调用 DubboCodec 实例的 encode 方法
     *
     * @param channel 通道实例
     * @param buffer  通道缓冲区
     * @param msg     要编码的消息对象
     * @throws IOException 编码过程中可能出现的 IO 异常
     */
    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object msg) throws IOException {
        codec.encode(channel, buffer, msg);
    }

    /**
     * 对通道缓冲区中的数据进行解码操作，支持多消息解码
     *
     * @param channel 通道实例
     * @param buffer  通道缓冲区
     * @return 解码后的对象，如果需要更多输入则返回 NEED_MORE_INPUT
     * @throws IOException 解码过程中可能出现的 IO 异常
     */
    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        // 保存当前缓冲区的读取位置
        int save = buffer.readerIndex();
        // 创建一个多消息对象
        MultiMessage result = MultiMessage.create();
        do {
            // 调用 DubboCodec 实例的 decode 方法进行解码
            Object obj = codec.decode(channel, buffer);
            // 如果需要更多输入，恢复缓冲区的读取位置并跳出循环
            if (Codec2.DecodeResult.NEED_MORE_INPUT == obj) {
                buffer.readerIndex(save);
                break;
            } else {
                // 将解码后的对象添加到多消息对象中
                result.addMessage(obj);
                // 记录消息长度
                logMessageLength(obj, buffer.readerIndex() - save);
                // 更新保存的读取位置
                save = buffer.readerIndex();
            }
        } while (true);
        // 如果多消息对象为空，返回 NEED_MORE_INPUT
        if (result.isEmpty()) {
            return Codec2.DecodeResult.NEED_MORE_INPUT;
        }
        // 如果多消息对象中只有一个消息，返回该消息
        if (result.size() == 1) {
            return result.get(0);
        }
        // 否则返回多消息对象
        return result;
    }

    /**
     * 记录消息长度，将消息长度作为附件添加到请求或响应对象中
     *
     * @param result 解码后的对象
     * @param bytes  消息的字节数
     */
    private void logMessageLength(Object result, int bytes) {
        // 如果字节数小于等于 0，直接返回
        if (bytes <= 0) {
            return;
        }
        // 如果结果是 Request 类型
        if (result instanceof Request) {
            try {
                // 将消息长度作为附件添加到 RpcInvocation 中
                ((RpcInvocation) ((Request) result).getData()).setAttachment(INPUT_KEY, String.valueOf(bytes));
            } catch (Throwable e) {
                // 忽略异常
                /* ignore */
            }
        }
        // 如果结果是 Response 类型
        else if (result instanceof Response) {
            try {
                // 将消息长度作为附件添加到 AppResponse 中
                ((AppResponse) ((Response) result).getResult()).setAttachment(OUTPUT_KEY, String.valueOf(bytes));
            } catch (Throwable e) {
                // 忽略异常
                /* ignore */
            }
        }
    }

}
