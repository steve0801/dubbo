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
// 包声明
package org.apache.dubbo.rpc.protocol.dubbo;

// 导入所需的类和接口
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.UnsafeByteArrayInputStream;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.codec.ExchangeCodec;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.FrameworkModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DECODE_IN_IO_THREAD_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_DECODE_IN_IO_THREAD;

/**
 * Dubbo codec.
 */
// 定义Dubbo编解码器类，继承自ExchangeCodec
public class DubboCodec extends ExchangeCodec {

    // 定义常量：编解码器名称
    public static final String NAME = "dubbo";
    // 定义常量：Dubbo协议版本
    public static final String DUBBO_VERSION = Version.getProtocolVersion();
    // 定义常量：响应类型 - 带异常
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    // 定义常量：响应类型 - 带值
    public static final byte RESPONSE_VALUE = 1;
    // 定义常量：响应类型 - 空值
    public static final byte RESPONSE_NULL_VALUE = 2;
    // 定义常量：响应类型 - 带异常和附件
    public static final byte RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
    // 定义常量：响应类型 - 带值和附件
    public static final byte RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
    // 定义常量：响应类型 - 空值和附件
    public static final byte RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;
    // 定义常量：空对象数组
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    // 定义常量：空类数组
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    // 定义日志记录器
    private static final Logger log = LoggerFactory.getLogger(DubboCodec.class);
    // 定义回调服务编解码器
    private CallbackServiceCodec callbackServiceCodec;
    // 定义框架模型
    private FrameworkModel frameworkModel;

    // 构造函数，初始化框架模型和回调服务编解码器
    public DubboCodec(FrameworkModel frameworkModel) {
        this.frameworkModel = frameworkModel;
        callbackServiceCodec = new CallbackServiceCodec(frameworkModel);
    }

    // 重写decodeBody方法，用于解码消息体
    @Override
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        // 获取标志位
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        // 获取请求ID
        long id = Bytes.bytes2long(header, 4);
        // 判断是否是请求
        if ((flag & FLAG_REQUEST) == 0) {
            // 解码响应
            Response res = new Response(id);
            // 判断是否是事件
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(true);
            }
            // 获取状态
            byte status = header[3];
            res.setStatus(status);
            try {
                // 如果状态是OK
                if (status == Response.OK) {
                    Object data;
                    // 如果是事件
                    if (res.isEvent()) {
                        // 获取事件负载
                        byte[] eventPayload = CodecSupport.getPayload(is);
                        // 判断是否是心跳
                        if (CodecSupport.isHeartBeat(eventPayload, proto)) {
                            // 心跳响应数据总是null
                            data = null;
                        } else {
                            // 反序列化输入流
                            ObjectInput in = CodecSupport.deserialize(channel.getUrl(), new ByteArrayInputStream(eventPayload), proto);
                            // 解码事件数据
                            data = decodeEventData(channel, in, eventPayload);
                        }
                    } else {
                        DecodeableRpcResult result;
                        // 判断是否在IO线程中解码
                        if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                            // 创建可解码的结果对象并解码
                            result = new DecodeableRpcResult(channel, res, is,
                                (Invocation) getRequestData(id), proto);
                            result.decode();
                        } else {
                            // 创建可解码的结果对象但不立即解码
                            result = new DecodeableRpcResult(channel, res,
                                new UnsafeByteArrayInputStream(readMessageData(is)),
                                (Invocation) getRequestData(id), proto);
                        }
                        // 设置结果数据
                        data = result;
                    }
                    res.setResult(data);
                } else {
                    // 反序列化输入流
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    // 设置错误消息
                    res.setErrorMessage(in.readUTF());
                }
            } catch (Throwable t) {
                // 记录解码响应失败的日志
                if (log.isWarnEnabled()) {
                    log.warn("Decode response failed: " + t.getMessage(), t);
                }
                // 设置响应状态为客户端错误
                res.setStatus(Response.CLIENT_ERROR);
                // 设置错误消息
                res.setErrorMessage(StringUtils.toString(t));
            }
            // 返回响应对象
            return res;
        } else {
            // 解码请求
            Request req = new Request(id);
            // 设置协议版本
            req.setVersion(Version.getProtocolVersion());
            // 判断是否是双向通信
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            // 判断是否是事件
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(true);
            }
            try {
                Object data;
                // 如果是事件
                if (req.isEvent()) {
                    // 获取事件负载
                    byte[] eventPayload = CodecSupport.getPayload(is);
                    // 判断是否是心跳
                    if (CodecSupport.isHeartBeat(eventPayload, proto)) {
                        // 心跳响应数据总是null
                        data = null;
                    } else {
                        // 反序列化输入流
                        ObjectInput in = CodecSupport.deserialize(channel.getUrl(), new ByteArrayInputStream(eventPayload), proto);
                        // 解码事件数据
                        data = decodeEventData(channel, in, eventPayload);
                    }
                } else {
                    DecodeableRpcInvocation inv;
                    // 判断是否在IO线程中解码
                    if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                        // 创建可解码的调用对象并解码
                        inv = new DecodeableRpcInvocation(frameworkModel, channel, req, is, proto);
                        inv.decode();
                    } else {
                        // 创建可解码的调用对象但不立即解码
                        inv = new DecodeableRpcInvocation(frameworkModel, channel, req,
                            new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    // 设置数据
                    data = inv;
                }
                req.setData(data);
            } catch (Throwable t) {
                // 记录解码请求失败的日志
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // 设置请求为损坏
                req.setBroken(true);
                // 设置数据为异常
                req.setData(t);
            }

            // 返回请求对象
            return req;
        }
    }

    // 从输入流中读取消息数据
    private byte[] readMessageData(InputStream is) throws IOException {
        // 检查输入流是否有可用数据
        if (is.available() > 0) {
            // 创建字节数组来存储数据
            byte[] result = new byte[is.available()];
            // 从输入流中读取数据到字节数组
            is.read(result);
            // 返回读取的数据
            return result;
        }
        // 如果没有可用数据，返回空字节数组
        return new byte[]{};
    }

    // 重写encodeRequestData方法，用于编码请求数据
    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        // 调用带版本号的编码方法
        encodeRequestData(channel, out, data, DUBBO_VERSION);
    }

    // 重写encodeResponseData方法，用于编码响应数据
    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        // 调用带版本号的编码方法
        encodeResponseData(channel, out, data, DUBBO_VERSION);
    }

    // 重写encodeRequestData方法，用于编码请求数据（带版本号）
    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        // 将数据转换为RpcInvocation对象
        RpcInvocation inv = (RpcInvocation) data;

        // 写入协议版本
        out.writeUTF(version);
        // 获取服务名称
        String serviceName = inv.getAttachment(INTERFACE_KEY);
        // 如果服务名称为空，则获取路径
        if (serviceName == null) {
            serviceName = inv.getAttachment(PATH_KEY);
        }
        // 写入服务名称
        out.writeUTF(serviceName);
        // 写入版本号
        out.writeUTF(inv.getAttachment(VERSION_KEY));

        // 写入方法名
        out.writeUTF(inv.getMethodName());
        // 写入参数类型描述
        out.writeUTF(inv.getParameterTypesDesc());
        // 获取参数
        Object[] args = inv.getArguments();
        // 如果参数不为空
        if (args != null) {
            // 遍历参数并编码
            for (int i = 0; i < args.length; i++) {
                out.writeObject(callbackServiceCodec.encodeInvocationArgument(channel, inv, i));
            }
        }
        // 写入附加信息
        out.writeAttachments(inv.getObjectAttachments());
    }

    // 重写encodeResponseData方法，用于编码响应数据（带版本号）
    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        // 将数据转换为Result对象
        Result result = (Result) data;
        // 判断当前版本是否支持响应附加信息
        boolean attach = Version.isSupportResponseAttachment(version);
        // 获取异常
        Throwable th = result.getException();
        // 如果没有异常
        if (th == null) {
            // 获取返回值
            Object ret = result.getValue();
            // 如果返回值为空
            if (ret == null) {
                // 写入响应类型 - 空值
                out.writeByte(attach ? RESPONSE_NULL_VALUE_WITH_ATTACHMENTS : RESPONSE_NULL_VALUE);
            } else {
                // 写入响应类型 - 带值
                out.writeByte(attach ? RESPONSE_VALUE_WITH_ATTACHMENTS : RESPONSE_VALUE);
                // 写入返回值
                out.writeObject(ret);
            }
        } else {
            // 写入响应类型 - 带异常
            out.writeByte(attach ? RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS : RESPONSE_WITH_EXCEPTION);
            // 写入异常
            out.writeThrowable(th);
        }

        // 如果支持响应附加信息
        if (attach) {
            // 在响应附加信息中添加当前协议版本
            result.getObjectAttachments().put(DUBBO_VERSION_KEY, Version.getProtocolVersion());
            // 写入附加信息
            out.writeAttachments(result.getObjectAttachments());
        }
    }

    // 重写getSerialization方法，用于获取序列化
    @Override
    protected Serialization getSerialization(Channel channel, Request req) {
        // 如果请求数据不是Invocation类型
        if (!(req.getData() instanceof Invocation)) {
            // 调用父类方法
            return super.getSerialization(channel, req);
        }
        // 获取请求序列化
        return DubboCodecSupport.getRequestSerialization(channel.getUrl(), (Invocation) req.getData());
    }

    // 重写getSerialization方法，用于获取序列化
    @Override
    protected Serialization getSerialization(Channel channel, Response res) {
        // 如果响应结果不是AppResponse类型
        if (!(res.getResult() instanceof AppResponse)) {
            // 调用父类方法
            return super.getSerialization(channel, res);
        }
        // 获取响应序列化
        return DubboCodecSupport.getResponseSerialization(channel.getUrl(), (AppResponse) res.getResult());
    }

}
