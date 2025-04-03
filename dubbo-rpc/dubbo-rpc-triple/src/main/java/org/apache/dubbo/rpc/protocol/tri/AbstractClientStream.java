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

package org.apache.dubbo.rpc.protocol.tri;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.remoting.api.Connection;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture2;
import org.apache.dubbo.rpc.CancellationContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ConsumerModel;
import org.apache.dubbo.triple.TripleWrapper;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2Error;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;


public abstract class AbstractClientStream extends AbstractStream implements Stream {
    // 消费者模型，包含服务消费者相关信息
    private ConsumerModel consumerModel;
    // 网络连接对象，用于与服务器通信
    private Connection connection;

    // 构造函数，初始化URL
    protected AbstractClientStream(URL url) {
        super(url); // 调用父类构造函数
    }

    // 构造函数，初始化URL和线程池执行器
    protected AbstractClientStream(URL url, Executor executor) {
        super(url, executor); // 调用父类构造函数
    }

    // 创建单向流客户端
    public static UnaryClientStream unary(URL url) {
        return new UnaryClientStream(url); // 返回一个新的单向流客户端实例
    }

    // 创建双向流客户端
    public static ClientStream stream(URL url) {
        return new ClientStream(url); // 返回一个新的双向流客户端实例
    }

    // 根据是否单向流创建客户端流
    public static AbstractClientStream newClientStream(URL url, boolean unary) {
        AbstractClientStream stream = unary ? unary(url) : stream(url); // 根据unary参数选择创建单向或双向流
        final CancellationContext cancellationContext = stream.getCancellationContext(); // 获取取消上下文
        // 添加取消监听器，当客户端取消时发送RST帧给服务端
        cancellationContext.addListener(context -> {
            if (LOGGER.isWarnEnabled()) {
                Throwable throwable = cancellationContext.getCancellationCause(); // 获取取消原因
                LOGGER.warn("Cancel by local throwable is ", throwable); // 记录警告日志
            }
            stream.asTransportObserver().onReset(Http2Error.CANCEL); // 发送RST帧
        });
        return stream; // 返回创建的流实例
    }

    // 设置消费者模型
    public AbstractClientStream service(ConsumerModel model) {
        this.consumerModel = model; // 设置消费者模型
        return this; // 返回当前实例，支持链式调用
    }

    // 获取消费者模型
    public ConsumerModel getConsumerModel() {
        return consumerModel; // 返回当前消费者模型
    }

    // 设置网络连接
    public AbstractClientStream connection(Connection connection) {
        this.connection = connection; // 设置网络连接
        return this; // 返回当前实例，支持链式调用
    }

    // 获取网络连接
    public Connection getConnection() {
        return connection; // 返回当前网络连接
    }

    // 执行任务，处理线程池拒绝和异常情况
    @Override
    public void execute(Runnable runnable) {
        try {
            super.execute(runnable); // 调用父类方法执行任务
        } catch (RejectedExecutionException e) {
            LOGGER.error("Consumer's thread pool is full", e); // 记录线程池满的错误日志
            getStreamSubscriber().onError(GrpcStatus.fromCode(GrpcStatus.Code.RESOURCE_EXHAUSTED)
                .withDescription("Consumer's thread pool is full").asException()); // 发送资源耗尽错误
        } catch (Throwable t) {
            LOGGER.error("Consumer submit request to thread pool error ", t); // 记录提交任务时的错误日志
            getStreamSubscriber().onError(GrpcStatus.fromCode(GrpcStatus.Code.INTERNAL)
                .withCause(t)
                .withDescription("Consumer's error")
                .asException()); // 发送内部错误
        }
    }

    // 编码请求数据
    protected byte[] encodeRequest(Object value) {
        final byte[] out;
        final Object obj;

        if (getMethodDescriptor().isNeedWrap()) {
            obj = getRequestWrapper(value); // 如果需要包装，获取包装后的请求对象
        } else {
            obj = getRequestValue(value); // 否则获取请求值
        }
        out = TripleUtil.pack(obj); // 将对象序列化为字节数组

        return super.compress(out); // 调用父类方法压缩数据
    }

    // 获取请求包装对象
    private TripleWrapper.TripleRequestWrapper getRequestWrapper(Object value) {
        if (getMethodDescriptor().isStream()) {
            String type = getMethodDescriptor().getParameterClasses()[0].getName(); // 获取参数类型名称
            return TripleUtil.wrapReq(getUrl(), getSerializeType(), value, type, getMultipleSerialization()); // 包装流式请求
        } else {
            RpcInvocation invocation = (RpcInvocation) value; // 转换为RpcInvocation
            return TripleUtil.wrapReq(getUrl(), invocation, getMultipleSerialization()); // 包装普通请求
        }
    }

    // 获取请求值
    private Object getRequestValue(Object value) {
        if (getMethodDescriptor().isUnary()) {
            RpcInvocation invocation = (RpcInvocation) value; // 转换为RpcInvocation
            return invocation.getArguments()[0]; // 返回第一个参数
        }

        return value; // 返回原始值
    }

    // 反序列化响应数据
    protected Object deserializeResponse(byte[] data) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader(); // 获取当前线程的类加载器
        try {
            if (getConsumerModel() != null) {
                ClassLoadUtil.switchContextLoader(getConsumerModel().getClassLoader()); // 切换类加载器
            }
            if (getMethodDescriptor().isNeedWrap()) {
                final TripleWrapper.TripleResponseWrapper wrapper = TripleUtil.unpack(data,
                    TripleWrapper.TripleResponseWrapper.class); // 反序列化为响应包装对象
                if (!getSerializeType().equals(TripleUtil.convertHessianFromWrapper(wrapper.getSerializeType()))) {
                    throw new UnsupportedOperationException("Received inconsistent serialization type from server, " +
                        "reject to deserialize! Expected:" + getSerializeType() +
                        " Actual:" + TripleUtil.convertHessianFromWrapper(wrapper.getSerializeType())); // 检查序列化类型是否一致
                }
                return TripleUtil.unwrapResp(getUrl(), wrapper, getMultipleSerialization()); // 解包响应
            } else {
                return TripleUtil.unpack(data, getMethodDescriptor().getReturnClass()); // 直接反序列化
            }
        } finally {
            ClassLoadUtil.switchContextLoader(tccl); // 恢复原始类加载器
        }
    }

    // 创建请求元数据
    protected Metadata createRequestMeta(RpcInvocation inv) {
        Metadata metadata = new DefaultMetadata(); // 创建新的元数据对象
        metadata.put(TripleHeaderEnum.PATH_KEY.getHeader(), "/" + inv.getObjectAttachment(CommonConstants.PATH_KEY) + "/" + inv.getMethodName()) // 设置路径
            .put(TripleHeaderEnum.AUTHORITY_KEY.getHeader(), getUrl().getAddress()) // 设置地址
            .put(TripleHeaderEnum.CONTENT_TYPE_KEY.getHeader(), TripleConstant.CONTENT_PROTO) // 设置内容类型
            .put(TripleHeaderEnum.TIMEOUT.getHeader(), inv.get(CommonConstants.TIMEOUT_KEY) + "m") // 设置超时时间
            .put(HttpHeaderNames.TE, HttpHeaderValues.TRAILERS); // 设置TE头

        metadata.putIfNotNull(TripleHeaderEnum.SERVICE_VERSION.getHeader(), getUrl().getVersion()) // 设置服务版本
            .putIfNotNull(TripleHeaderEnum.CONSUMER_APP_NAME_KEY.getHeader(),
                (String) inv.getObjectAttachments().remove(CommonConstants.APPLICATION_KEY)) // 设置消费者应用名称
            .putIfNotNull(TripleHeaderEnum.CONSUMER_APP_NAME_KEY.getHeader(),
                (String) inv.getObjectAttachments().remove(CommonConstants.REMOTE_APPLICATION_KEY)) // 设置远程应用名称
            .putIfNotNull(TripleHeaderEnum.SERVICE_GROUP.getHeader(), getUrl().getGroup()) // 设置服务组
            .putIfNotNull(TripleHeaderEnum.GRPC_ENCODING.getHeader(), getCompressor().getMessageEncoding()) // 设置gRPC编码
            .putIfNotNull(TripleHeaderEnum.GRPC_ACCEPT_ENCODING.getHeader(), Compressor.getAcceptEncoding(getUrl().getOrDefaultFrameworkModel())); // 设置gRPC接受编码
        final Map<String, Object> attachments = inv.getObjectAttachments(); // 获取附件
        if (attachments != null) {
            convertAttachment(metadata, attachments); // 转换并添加附件到元数据
        }
        return metadata; // 返回元数据对象
    }

    // 远程取消时处理
    @Override
    protected void cancelByRemoteReset(Http2Error http2Error) {
        DefaultFuture2.getFuture(getRequest().getId()).cancel(); // 取消对应的Future
    }

    // 本地取消时处理
    @Override
    protected void cancelByLocal(Throwable throwable) {
        getCancellationContext().cancel(throwable); // 取消上下文
    }
}
