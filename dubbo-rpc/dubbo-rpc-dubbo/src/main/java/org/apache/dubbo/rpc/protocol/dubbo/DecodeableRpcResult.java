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

import org.apache.dubbo.common.config.Configuration;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.Cleanable;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Decodeable;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;

import static org.apache.dubbo.rpc.Constants.SERIALIZATION_ID_KEY;
import static org.apache.dubbo.rpc.Constants.SERIALIZATION_SECURITY_CHECK_KEY;

public class DecodeableRpcResult extends AppResponse implements Codec, Decodeable {

    private static final Logger log = LoggerFactory.getLogger(DecodeableRpcResult.class);

    private Channel channel;

    private byte serializationType;

    private InputStream inputStream;

    private Response response;

    private Invocation invocation;

    private volatile boolean hasDecoded;

    public DecodeableRpcResult(Channel channel, Response response, InputStream is, Invocation invocation, byte id) {
        Assert.notNull(channel, "channel == null");
        Assert.notNull(response, "response == null");
        Assert.notNull(is, "inputStream == null");
        this.channel = channel;
        this.response = response;
        this.inputStream = is;
        this.invocation = invocation;
        this.serializationType = id;
    }

    @Override
    public void encode(Channel channel, OutputStream output, Object message) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object decode(Channel channel, InputStream input) throws IOException {
        // 如果日志级别设置为DEBUG，则记录当前解码线程的信息
        if (log.isDebugEnabled()) {
            Thread thread = Thread.currentThread();
            log.debug("Decoding in thread -- [" + thread.getName() + "#" + thread.getId() + "]");
        }

        // 切换当前线程的上下文类加载器（TCCL），以便正确加载服务模型相关的类
        if (invocation != null && invocation.getServiceModel() != null) {
            Thread.currentThread().setContextClassLoader(invocation.getServiceModel().getClassLoader());
        }

        // 根据通道URL和序列化类型获取对应的反序列化器，并对输入流进行反序列化处理
        ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), serializationType)
            .deserialize(channel.getUrl(), input);

        // 读取响应标志，该标志指示了接下来如何处理解码的数据
        byte flag = in.readByte();
        switch (flag) {
            case DubboCodec.RESPONSE_NULL_VALUE:
                // 响应值为空，无需进一步处理
                break;
            case DubboCodec.RESPONSE_VALUE:
                // 处理正常的响应值
                handleValue(in);
                break;
            case DubboCodec.RESPONSE_WITH_EXCEPTION:
                // 处理包含异常信息的响应
                handleException(in);
                break;
            case DubboCodec.RESPONSE_NULL_VALUE_WITH_ATTACHMENTS:
                // 处理带附件但值为空的响应
                handleAttachment(in);
                break;
            case DubboCodec.RESPONSE_VALUE_WITH_ATTACHMENTS:
                // 处理同时含有正常值和附件的响应
                handleValue(in);
                handleAttachment(in);
                break;
            case DubboCodec.RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS:
                // 处理同时含有异常信息和附件的响应
                handleException(in);
                handleAttachment(in);
                break;
            default:
                // 当接收到未知的响应标志时抛出异常
                throw new IOException("Unknown result flag, expect '0' '1' '2' '3' '4' '5', but received: " + flag);
        }

        // 如果输入对象实现了Cleanable接口，则调用cleanup方法释放资源
        if (in instanceof Cleanable) {
            ((Cleanable) in).cleanup();
        }

        // 返回当前DecodeableRpcResult实例
        return this;
    }

    /**
     * 实现 Decodeable 接口的 decode 方法，用于对 RPC 结果进行解码。
     * 如果尚未解码且通道和输入流不为空，则执行解码操作。
     *
     * @throws Exception 如果在解码过程中发生错误
     */
    @Override
    public void decode() throws Exception {
        // 检查是否已经解码，以及通道和输入流是否存在
        if (!hasDecoded && channel != null && inputStream != null) {
            try {
                // 检查调用信息是否存在
                if (invocation != null) {
                    // 获取系统配置
                    Configuration systemConfiguration = ConfigurationUtils.getSystemConfiguration(channel.getUrl().getScopeModel());
                    // 检查系统配置是否为空或是否启用了序列化安全检查
                    if (systemConfiguration == null || systemConfiguration.getBoolean(SERIALIZATION_SECURITY_CHECK_KEY, true)) {
                        // 从调用信息中获取序列化类型
                        Object serializationTypeObj = invocation.get(SERIALIZATION_ID_KEY);
                        // 检查序列化类型是否存在
                        if (serializationTypeObj != null) {
                            // 比较序列化类型是否一致
                            if ((byte) serializationTypeObj != serializationType) {
                                // 若不一致，抛出异常提示序列化 ID 不匹配
                                throw new IOException("Unexpected serialization id:" + serializationType + " received from network, please check if the peer send the right id.");
                            }
                        }
                    }
                }

                // 调用 decode 方法进行实际的解码操作
                decode(channel, inputStream);
            } catch (Throwable e) {
                // 若解码过程中出现异常，记录警告日志
                if (log.isWarnEnabled()) {
                    log.warn("Decode rpc result failed: " + e.getMessage(), e);
                }
                // 设置响应状态为客户端错误
                response.setStatus(Response.CLIENT_ERROR);
                // 设置响应错误信息
                response.setErrorMessage(StringUtils.toString(e));
            } finally {
                // 标记为已解码
                hasDecoded = true;
            }
        }
    }

    /**
     * 处理从 ObjectInput 中读取响应的返回值，并根据调用信息设置返回值。
     *
     * @param in 用于读取对象的输入流
     * @throws IOException 当读取输入流时发生 I/O 错误
     */
    private void handleValue(ObjectInput in) throws IOException {
        try {
            // 声明一个数组用于存储返回类型
            Type[] returnTypes;
            // 检查 invocation 是否为 RpcInvocation 类型
            if (invocation instanceof RpcInvocation) {
                // 如果是 RpcInvocation 类型，直接从 invocation 中获取返回类型
                returnTypes = ((RpcInvocation) invocation).getReturnTypes();
            } else {
                // 否则，使用 RpcUtils 工具类获取返回类型
                returnTypes = RpcUtils.getReturnTypes(invocation);
            }
            // 声明一个变量用于存储从输入流中读取的对象
            Object value;
            // 检查返回类型数组是否为空
            if (ArrayUtils.isEmpty(returnTypes)) {
                // 如果返回类型数组为空，直接从输入流中读取对象
                // 这种情况几乎不会发生
                value = in.readObject();
            } else if (returnTypes.length == 1) {
                // 如果返回类型数组长度为 1，根据指定的类型从输入流中读取对象
                value = in.readObject((Class<?>) returnTypes[0]);
            } else {
                // 如果返回类型数组长度大于 1，根据前两个类型从输入流中读取对象
                value = in.readObject((Class<?>) returnTypes[0], returnTypes[1]);
            }
            // 将读取到的对象设置为当前响应结果的值
            setValue(value);
        } catch (ClassNotFoundException e) {
            // 如果在读取对象时发生类未找到异常，重新抛出一个包装后的 IOException
            rethrow(e);
        }
    }

    private void handleException(ObjectInput in) throws IOException {
        try {
            setException(in.readThrowable());
        } catch (ClassNotFoundException e) {
            rethrow(e);
        }
    }

    private void handleAttachment(ObjectInput in) throws IOException {
        try {
            addObjectAttachments(in.readAttachments());
        } catch (ClassNotFoundException e) {
            rethrow(e);
        }
    }

    private void rethrow(Exception e) throws IOException {
        throw new IOException(StringUtils.toString("Read response data failed.", e));
    }
}
