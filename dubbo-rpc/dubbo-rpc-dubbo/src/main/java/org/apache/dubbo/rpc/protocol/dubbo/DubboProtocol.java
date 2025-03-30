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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.serialize.support.SerializableClassRegistry;
import org.apache.dubbo.common.serialize.support.SerializationOptimizer;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.RemotingServer;
import org.apache.dubbo.remoting.Transporter;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.exchange.ExchangeServer;
import org.apache.dubbo.remoting.exchange.Exchangers;
import org.apache.dubbo.remoting.exchange.support.ExchangeHandlerAdapter;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProtocolServer;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.protocol.AbstractProtocol;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.LAZY_CONNECT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.STUB_EVENT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.remoting.Constants.CHANNEL_READONLYEVENT_SENT_KEY;
import static org.apache.dubbo.remoting.Constants.CLIENT_KEY;
import static org.apache.dubbo.remoting.Constants.CODEC_KEY;
import static org.apache.dubbo.remoting.Constants.CONNECTIONS_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_HEARTBEAT;
import static org.apache.dubbo.remoting.Constants.DEFAULT_REMOTING_CLIENT;
import static org.apache.dubbo.remoting.Constants.HEARTBEAT_KEY;
import static org.apache.dubbo.remoting.Constants.SERVER_KEY;
import static org.apache.dubbo.rpc.Constants.DEFAULT_REMOTING_SERVER;
import static org.apache.dubbo.rpc.Constants.DEFAULT_STUB_EVENT;
import static org.apache.dubbo.rpc.Constants.IS_SERVER_KEY;
import static org.apache.dubbo.rpc.Constants.STUB_EVENT_METHODS_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.CALLBACK_SERVICE_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_SHARE_CONNECTIONS;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.IS_CALLBACK_SERVICE;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.ON_CONNECT_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.ON_DISCONNECT_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.OPTIMIZER_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.SHARE_CONNECTIONS_KEY;


/**
 * dubbo protocol support.
 */
/**
 * dubbo protocol support.
 * 该类提供了 Dubbo 协议的支持，包含服务导出、引用、处理请求等功能。
 */
public class DubboProtocol extends AbstractProtocol {

    /**
     * Dubbo 协议的名称，用于标识该协议。
     */
    public static final String NAME = "dubbo";

    /**
     * Dubbo 协议默认使用的端口号。
     */
    public static final int DEFAULT_PORT = 20880;

    /**
     * 用于标识是否为回调服务调用的附件键名。
     */
    private static final String IS_CALLBACK_SERVICE_INVOKE = "_isCallBackServiceInvoke";

    /**
     * 存储引用客户端的映射，键为地址字符串，值为客户端对象（可能是列表）。
     * 注释中原本的 {@link Map<String, List<ReferenceCountExchangeClient>} 存在非法字符，正确的应该是 {@link Map<String, List<ReferenceCountExchangeClient>>}
     */
    /**
     * <host:port,Exchanger>
     * 存储引用客户端的映射，键为地址字符串，值为客户端对象（可能是列表）
     */
    private final Map<String, Object> referenceClientMap = new ConcurrentHashMap<>();

    /**
     * 表示正在处理中的占位对象，用于并发控制。
     */
    private static final Object PENDING_OBJECT = new Object();

    /**
     * 存储已优化的序列化器类名的集合，避免重复优化。
     */
    private final Set<String> optimizers = new ConcurrentHashSet<>();

    /**
     * 请求处理器，用于处理客户端的请求。
     * 此变量可能可声明为 final，因为它在初始化后不会被重新赋值。
     */
    private ExchangeHandler requestHandler = new ExchangeHandlerAdapter() {

        /**
         * 处理客户端的请求并返回响应的 CompletableFuture。
         *
         * @param channel 客户端与服务端的通道
         * @param message 客户端发送的消息
         * @return 包含响应结果的 CompletableFuture
         * @throws RemotingException 当发生远程通信异常时抛出
         */
        @Override
        public CompletableFuture<Object> reply(ExchangeChannel channel, Object message) throws RemotingException {
            // 检查消息是否为 Invocation 类型，若不是则抛出异常
            if (!(message instanceof Invocation)) {
                throw new RemotingException(channel, "Unsupported request: "
                    + (message == null ? null : (message.getClass().getName() + ": " + message))
                    + ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress());
            }

            // 将消息转换为 Invocation 类型
            Invocation inv = (Invocation) message;
            // 获取对应的 Invoker
            Invoker<?> invoker = getInvoker(channel, inv);
            // 设置服务模型到 Invocation 中
            inv.setServiceModel(invoker.getUrl().getServiceModel());
            // 切换线程上下文类加载器
            if (invoker.getUrl().getServiceModel() != null) {
                Thread.currentThread().setContextClassLoader(invoker.getUrl().getServiceModel().getClassLoader());
            }
            // 检查是否为回调服务调用
            if (Boolean.TRUE.toString().equals(inv.getObjectAttachments().get(IS_CALLBACK_SERVICE_INVOKE))) {
                // 获取服务的方法列表
                String methodsStr = invoker.getUrl().getParameters().get("methods");
                boolean hasMethod = false;
                // 检查方法是否存在
                if (methodsStr == null || !methodsStr.contains(",")) {
                    hasMethod = inv.getMethodName().equals(methodsStr);
                } else {
                    String[] methods = methodsStr.split(",");
                    for (String method : methods) {
                        if (inv.getMethodName().equals(method)) {
                            hasMethod = true;
                            break;
                        }
                    }
                }
                // 若方法不存在，记录警告日志并返回 null
                if (!hasMethod) {
                    logger.warn(new IllegalStateException("The methodName " + inv.getMethodName()
                        + " not found in callback service interface ,invoke will be ignored."
                        + " please update the api interface. url is:"
                        + invoker.getUrl()) + " ,invocation is :" + inv);
                    return null;
                }
            }
            // 设置远程地址到 RpcContext 中
            RpcContext.getServiceContext().setRemoteAddress(channel.getRemoteAddress());
            // 调用 Invoker 执行方法并获取结果
            Result result = invoker.invoke(inv);
            // 将结果转换为 CompletableFuture 并返回
            return result.thenApply(Function.identity());
        }

        /**
         * 处理接收到的消息。
         *
         * @param channel 客户端与服务端的通道
         * @param message 接收到的消息
         * @throws RemotingException 当发生远程通信异常时抛出
         * 此方法中从未抛出异常 'org.apache.dubbo.remoting.RemotingException'，可考虑移除异常声明。
         */
        @Override
        public void received(Channel channel, Object message) throws RemotingException {
            // 若消息为 Invocation 类型，调用 reply 方法处理
            if (message instanceof Invocation) {
                reply((ExchangeChannel) channel, message);
            } else {
                // 否则调用父类的 received 方法处理
                super.received(channel, message);
            }
        }

        /**
         * 处理客户端连接事件。
         *
         * @param channel 客户端与服务端的通道
         * @throws RemotingException 当发生远程通信异常时抛出
         * 此方法中从未抛出异常 'org.apache.dubbo.remoting.RemotingException'，可考虑移除异常声明。
         */
        @Override
        public void connected(Channel channel) throws RemotingException {
            // 调用 invoke 方法处理连接事件
            invoke(channel, ON_CONNECT_KEY);
        }

        /**
         * 处理客户端断开连接事件。
         *
         * @param channel 客户端与服务端的通道
         * @throws RemotingException 当发生远程通信异常时抛出
         * 此方法中从未抛出异常 'org.apache.dubbo.remoting.RemotingException'，可考虑移除异常声明。
         */
        @Override
        public void disconnected(Channel channel) throws RemotingException {
            // 记录调试日志
            if (logger.isDebugEnabled()) {
                logger.debug("disconnected from " + channel.getRemoteAddress() + ",url:" + channel.getUrl());
            }
            // 调用 invoke 方法处理断开连接事件
            invoke(channel, ON_DISCONNECT_KEY);
        }

        /**
         * 调用指定的方法。
         *
         * @param channel   客户端与服务端的通道
         * @param methodKey 方法的键名
         */
        private void invoke(Channel channel, String methodKey) {
            // 创建 Invocation 对象
            Invocation invocation = createInvocation(channel, channel.getUrl(), methodKey);
            if (invocation != null) {
                try {
                    // 调用 received 方法处理 Invocation
                    received(channel, invocation);
                } catch (Throwable t) {
                    // 记录警告日志
                    logger.warn("Failed to invoke event method " + invocation.getMethodName() + "(), cause: " + t.getMessage(), t);
                }
            }
        }

        /**
         * 创建 Invocation 对象。
         * 注意：channel.getUrl() 总是绑定到一个固定的服务，且这个服务是随机的。
         * 如果没有简单的方法获取此连接绑定的特定服务，我们可以选择使用一个通用服务来处理 onConnect 事件。
         *
         * @param channel   客户端与服务端的通道
         * @param url       服务的 URL
         * @param methodKey 方法的键名
         * @return 创建的 Invocation 对象，若方法为空则返回 null
         */
        private Invocation createInvocation(Channel channel, URL url, String methodKey) {
            // 从 URL 中获取方法名
            String method = url.getParameter(methodKey);
            // 检查方法名是否为空，使用 isEmpty() 替代 length() == 0
            if (method == null || method.isEmpty()) {
                return null;
            }

            // 创建 RpcInvocation 对象
            RpcInvocation invocation = new RpcInvocation(url.getServiceModel(), method, url.getParameter(INTERFACE_KEY), "", new Class<?>[0], new Object[0]);
            // 设置附件信息
            invocation.setAttachment(PATH_KEY, url.getPath());
            invocation.setAttachment(GROUP_KEY, url.getGroup());
            invocation.setAttachment(INTERFACE_KEY, url.getParameter(INTERFACE_KEY));
            invocation.setAttachment(VERSION_KEY, url.getVersion());
            // 检查是否启用存根事件
            if (url.getParameter(STUB_EVENT_KEY, false)) {
                invocation.setAttachment(STUB_EVENT_KEY, Boolean.TRUE.toString());
            }

            return invocation;
        }
    };

    /**
     * 无参构造函数。
     */
    public DubboProtocol() {
    }

    /**
     * 获取 Dubbo 协议实例，此方法已弃用，建议使用 {@link DubboProtocol#getDubboProtocol(ScopeModel)} 替代。
     *
     * @return DubboProtocol 实例
     */
    @Deprecated
    public static DubboProtocol getDubboProtocol() {
        return (DubboProtocol) ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(DubboProtocol.NAME, false);
    }

    /**
     * 根据 ScopeModel 获取 Dubbo 协议实例。
     *
     * @param scopeModel 作用域模型
     * @return DubboProtocol 实例
     */
    public static DubboProtocol getDubboProtocol(ScopeModel scopeModel) {
        return (DubboProtocol) scopeModel.getExtensionLoader(Protocol.class).getExtension(DubboProtocol.NAME, false);
    }

    /**
     * 获取已导出的服务 Exporter 集合。
     *
     * @return 不可修改的 Exporter 集合
     */
    @Override
    public Collection<Exporter<?>> getExporters() {
        return Collections.unmodifiableCollection(exporterMap.values());
    }

    /**
     * 判断是否为客户端侧。
     *
     * @param channel 客户端与服务端的通道
     * @return 若是客户端侧返回 true，否则返回 false
     */
    private boolean isClientSide(Channel channel) {
        // 获取远程地址
        InetSocketAddress address = channel.getRemoteAddress();
        // 获取 URL
        URL url = channel.getUrl();
        // 判断是否为客户端侧
        return url.getPort() == address.getPort() &&
            NetUtils.filterLocalHost(channel.getUrl().getIp())
                .equals(NetUtils.filterLocalHost(address.getAddress().getHostAddress()));
    }

    /**
     * 根据通道和调用信息获取对应的 Invoker。
     *
     * @param channel 客户端与服务端的通道
     * @param inv     调用信息
     * @return 对应的 Invoker
     * @throws RemotingException 当发生远程通信异常时抛出
     */
    Invoker<?> getInvoker(Channel channel, Invocation inv) throws RemotingException {
        // 标记是否为回调服务调用
        boolean isCallBackServiceInvoke = false;
        // 标记是否为存根服务调用
        boolean isStubServiceInvoke = false;
        // 获取本地端口
        int port = channel.getLocalAddress().getPort();
        // 获取路径
        String path = (String) inv.getObjectAttachments().get(PATH_KEY);

        // 判断是否为存根服务调用
        isStubServiceInvoke = Boolean.TRUE.toString().equals(inv.getObjectAttachments().get(STUB_EVENT_KEY));
        if (isStubServiceInvoke) {
            // 若是存根服务调用，更新端口为远程端口
            port = channel.getRemoteAddress().getPort();
        }

        // 判断是否为回调服务调用
        isCallBackServiceInvoke = isClientSide(channel) && !isStubServiceInvoke;
        if (isCallBackServiceInvoke) {
            // 若是回调服务调用，更新路径
            path += "." + inv.getObjectAttachments().get(CALLBACK_SERVICE_KEY);
            // 设置回调服务调用标记
            inv.getObjectAttachments().put(IS_CALLBACK_SERVICE_INVOKE, Boolean.TRUE.toString());
        }

        // 生成服务键
        String serviceKey = serviceKey(
            port,
            path,
            (String) inv.getObjectAttachments().get(VERSION_KEY),
            (String) inv.getObjectAttachments().get(GROUP_KEY)
        );
        // 从 exporterMap 中获取 Exporter
        DubboExporter<?> exporter = (DubboExporter<?>) exporterMap.get(serviceKey);

        // 若 Exporter 不存在，抛出异常
        if (exporter == null) {
            throw new RemotingException(channel, "Not found exported service: " + serviceKey + " in " + exporterMap.keySet() + ", may be version or group mismatch " +
                ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress() + ", message:" + getInvocationWithoutData(inv));
        }

        return exporter.getInvoker();
    }

    /**
     * 获取所有的 Invoker 集合。
     *
     * @return 不可修改的 Invoker 集合
     */
    public Collection<Invoker<?>> getInvokers() {
        return Collections.unmodifiableCollection(invokers);
    }

    /**
     * 获取 Dubbo 协议的默认端口。
     *
     * @return 默认端口号
     */
    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    /**
     * 导出服务。
     *
     * @param invoker 要导出的服务 Invoker
     * @param <T>     服务的类型
     * @return 导出的服务 Exporter
     * @throws RpcException 当发生 RPC 异常时抛出
     */
    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        // 获取服务的 URL
        URL url = invoker.getUrl();

        // 导出服务
        String key = serviceKey(url);
        // 显式类型实参 T 可被替换为 <>
        DubboExporter<T> exporter = new DubboExporter<>(invoker, key, exporterMap);
        exporterMap.put(key, exporter);

        // 检查是否启用存根事件且不是回调服务
        boolean isStubSupportEvent = url.getParameter(STUB_EVENT_KEY, DEFAULT_STUB_EVENT);
        boolean isCallbackservice = url.getParameter(IS_CALLBACK_SERVICE, false);
        if (isStubSupportEvent && !isCallbackservice) {
            // 获取存根服务方法
            String stubServiceMethods = url.getParameter(STUB_EVENT_METHODS_KEY);
            // 检查存根服务方法是否为空，使用 isEmpty() 替代 length() == 0
            if (stubServiceMethods == null || stubServiceMethods.isEmpty()) {
                // 若为空，记录警告日志
                if (logger.isWarnEnabled()) {
                    logger.warn(new IllegalStateException("consumer [" + url.getParameter(INTERFACE_KEY) +
                        "], has set stubproxy support event ,but no stub methods founded."));
                }
            }
        }

        // 打开服务器
        openServer(url);
        // 优化序列化
        optimizeSerialization(url);

        return exporter;
    }

    /**
     * 打开服务器。
     *
     * @param url 服务的 URL
     */
    private void openServer(URL url) {
        // 获取服务器地址
        String key = url.getAddress();
        // 判断是否为服务器端
        boolean isServer = url.getParameter(IS_SERVER_KEY, true);
        if (isServer) {
            // 从 serverMap 中获取服务器
            ProtocolServer server = serverMap.get(key);
            if (server == null) {
                // 若服务器不存在，加锁创建或重置
                synchronized (this) {
                    server = serverMap.get(key);
                    if (server == null) {
                        serverMap.put(key, createServer(url));
                    } else {
                        server.reset(url);
                    }
                }
            } else {
                // 若服务器存在，重置服务器
                server.reset(url);
            }
        }
    }

    /**
     * 创建服务器。
     *
     * @param url 服务的 URL
     * @return 创建的 ProtocolServer 实例
     */
    private ProtocolServer createServer(URL url) {
        // 构建 URL，添加必要的参数
        url = URLBuilder.from(url)
            // 发送只读事件
            .addParameterIfAbsent(CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString())
            // 启用心跳
            .addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT))
            .addParameter(CODEC_KEY, DubboCodec.NAME)
            .build();
        // 获取服务器类型
        String str = url.getParameter(SERVER_KEY, DEFAULT_REMOTING_SERVER);

        // 检查服务器类型是否支持，使用 !str.isEmpty() 替代 str.length() > 0
        if (str != null && !str.isEmpty() && !url.getOrDefaultFrameworkModel().getExtensionLoader(Transporter.class).hasExtension(str)) {
            throw new RpcException("Unsupported server type: " + str + ", url: " + url);
        }

        ExchangeServer server;
        try {
            // 绑定服务器
            server = Exchangers.bind(url, requestHandler);
        } catch (RemotingException e) {
            throw new RpcException("Fail to start server(url: " + url + ") " + e.getMessage(), e);
        }

        // 获取客户端类型
        str = url.getParameter(CLIENT_KEY);
        // 检查客户端类型是否支持，使用 !str.isEmpty() 替代 str.length() > 0
        if (str != null && str.length() > 0) {
            Set<String> supportedTypes = url.getOrDefaultFrameworkModel().getExtensionLoader(Transporter.class).getSupportedExtensions();
            if (!supportedTypes.contains(str)) {
                throw new RpcException("Unsupported client type: " + str);
            }
        }

        // 创建 DubboProtocolServer 实例
        DubboProtocolServer protocolServer = new DubboProtocolServer(server);
        // 加载服务器属性
        loadServerProperties(protocolServer);
        return protocolServer;
    }

    /**
     * 优化序列化过程。
     *
     * @param url 服务的 URL
     * @throws RpcException 当发生 RPC 异常时抛出
     */
    private void optimizeSerialization(URL url) throws RpcException {
        // 获取优化器类名
        String className = url.getParameter(OPTIMIZER_KEY, "");
        // 检查类名是否为空或已优化
        if (StringUtils.isEmpty(className) || optimizers.contains(className)) {
            return;
        }

        // 记录优化信息
        logger.info("Optimizing the serialization process for Kryo, FST, etc...");

        try {
            // 加载优化器类，使用泛型避免原始类型使用
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            // 检查是否为 SerializationOptimizer 类型
            if (!SerializationOptimizer.class.isAssignableFrom(clazz)) {
                throw new RpcException("The serialization optimizer " + className + " isn't an instance of " + SerializationOptimizer.class.getName());
            }

            // 创建优化器实例
            SerializationOptimizer optimizer = (SerializationOptimizer) clazz.newInstance();

            // 检查可序列化类数组是否为空
            if (optimizer.getSerializableClasses() == null) {
                return;
            }

            // 注册可序列化类，使用泛型避免原始类型使用
            for (Class<?> c : optimizer.getSerializableClasses()) {
                SerializableClassRegistry.registerClass(c);
            }

            // 添加到优化器集合
            optimizers.add(className);

        } catch (ClassNotFoundException e) {
            throw new RpcException("Cannot find the serialization optimizer class: " + className, e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RpcException("Cannot instantiate the serialization optimizer class: " + className, e);
        }
    }

    /**
     * 引用服务。
     *
     * @param type 服务的接口类型
     * @param url  服务的 URL
     * @param <T>  服务的类型
     * @return 引用的服务 Invoker
     * @throws RpcException 当发生 RPC 异常时抛出
     */
    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        return protocolBindingRefer(type, url);
    }

    /**
     * 协议绑定引用服务。
     *
     * @param serviceType 服务的接口类型
     * @param url         服务的 URL
     * @param <T>         服务的类型
     * @return 引用的服务 Invoker
     * @throws RpcException 当发生 RPC 异常时抛出
     */
    @Override
    public <T> Invoker<T> protocolBindingRefer(Class<T> serviceType, URL url) throws RpcException {
        // 优化序列化
        optimizeSerialization(url);

        // 创建 RPC Invoker，显式类型实参 T 可被替换为 <>
        DubboInvoker<T> invoker = new DubboInvoker<>(serviceType, url, getClients(url), invokers);
        invokers.add(invoker);

        return invoker;
    }

    /**
     * 获取客户端连接数组。
     *
     * @param url 服务的 URL
     * @return 客户端连接数组
     */
    private ExchangeClient[] getClients(URL url) {
        // 是否使用共享连接
        boolean useShareConnect = false;

        // 获取连接数
        int connections = url.getParameter(CONNECTIONS_KEY, 0);
        List<ReferenceCountExchangeClient> shareClients = null;
        // 判断是否使用共享连接
        if (connections == 0) {
            useShareConnect = true;

            // 获取共享连接数
            String shareConnectionsStr = url.getParameter(SHARE_CONNECTIONS_KEY, (String) null);
            connections = Integer.parseInt(StringUtils.isBlank(shareConnectionsStr) ? ConfigurationUtils.getProperty(url.getOrDefaultApplicationModel(), SHARE_CONNECTIONS_KEY,
                DEFAULT_SHARE_CONNECTIONS) : shareConnectionsStr);
            shareClients = getSharedClient(url, connections);
        }

        ExchangeClient[] clients = new ExchangeClient[connections];
        for (int i = 0; i < clients.length; i++) {
            if (useShareConnect) {
                clients[i] = shareClients.get(i);
            } else {
                clients[i] = initClient(url);
            }
        }

        return clients;
    }

    /**
     * 获取共享客户端连接列表。
     *
     * @param url        服务的 URL
     * @param connectNum 连接数
     * @return 共享客户端连接列表
     */
    @SuppressWarnings("unchecked")
    private List<ReferenceCountExchangeClient> getSharedClient(URL url, int connectNum) {
        // 获取地址
        String key = url.getAddress();

        // 从 referenceClientMap 中获取客户端
        Object clients = referenceClientMap.get(key);
        if (clients instanceof List) {
            List<ReferenceCountExchangeClient> typedClients = (List<ReferenceCountExchangeClient>) clients;
            // 检查客户端是否可用
            if (checkClientCanUse(typedClients)) {
                // 增加客户端引用计数
                batchClientRefIncr(typedClients);
                return typedClients;
            }
        }

        List<ReferenceCountExchangeClient> typedClients = null;

        // 加锁处理
        synchronized (referenceClientMap) {
            for (; ; ) {
                clients = referenceClientMap.get(key);

                if (clients instanceof List) {
                    typedClients = (List<ReferenceCountExchangeClient>) clients;
                    // 检查客户端是否可用
                    if (checkClientCanUse(typedClients)) {
                        // 增加客户端引用计数
                        batchClientRefIncr(typedClients);
                        return typedClients;
                    } else {
                        referenceClientMap.put(key, PENDING_OBJECT);
                        break;
                    }
                } else if (clients == PENDING_OBJECT) {
                    try {
                        referenceClientMap.wait();
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    referenceClientMap.put(key, PENDING_OBJECT);
                    break;
                }
            }
        }

        try {
            // 确保连接数大于等于 1
            connectNum = Math.max(connectNum, 1);

            // 初始化客户端列表
            if (CollectionUtils.isEmpty(typedClients)) {
                typedClients = buildReferenceCountExchangeClientList(url, connectNum);
            } else {
                for (int i = 0; i < typedClients.size(); i++) {
                    ReferenceCountExchangeClient referenceCountExchangeClient = typedClients.get(i);
                    // 检查客户端是否可用
                    if (referenceCountExchangeClient == null || referenceCountExchangeClient.isClosed()) {
                        typedClients.set(i, buildReferenceCountExchangeClient(url));
                        continue;
                    }
                    // 增加客户端引用计数
                    referenceCountExchangeClient.incrementAndGetCount();
                }
            }
        } finally {
            // 加锁更新 referenceClientMap
            synchronized (referenceClientMap) {
                if (typedClients == null) {
                    referenceClientMap.remove(key);
                } else {
                    referenceClientMap.put(key, typedClients);
                }
                referenceClientMap.notifyAll();
            }
        }
        return typedClients;
    }

    /**
     * 检查客户端列表是否可用。
     *
     * @param referenceCountExchangeClients 客户端列表
     * @return 若可用返回 true，否则返回 false
     */
    private boolean checkClientCanUse(List<ReferenceCountExchangeClient> referenceCountExchangeClients) {
        // 检查列表是否为空
        if (CollectionUtils.isEmpty(referenceCountExchangeClients)) {
            return false;
        }

        // 遍历客户端列表
        for (ReferenceCountExchangeClient referenceCountExchangeClient : referenceCountExchangeClients) {
            // 检查客户端是否可用
            if (referenceCountExchangeClient == null || referenceCountExchangeClient.getCount() <= 0 || referenceCountExchangeClient.isClosed()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 批量增加客户端引用计数。
     *
     * @param referenceCountExchangeClients 客户端列表
     */
    private void batchClientRefIncr(List<ReferenceCountExchangeClient> referenceCountExchangeClients) {
        // 检查列表是否为空
        if (CollectionUtils.isEmpty(referenceCountExchangeClients)) {
            return;
        }

        // 遍历客户端列表
        for (ReferenceCountExchangeClient referenceCountExchangeClient : referenceCountExchangeClients) {
            if (referenceCountExchangeClient != null) {
                // 增加客户端引用计数
                referenceCountExchangeClient.incrementAndGetCount();
            }
        }
    }

    /**
     * 批量构建客户端列表。
     *
     * @param url        服务的 URL
     * @param connectNum 连接数
     * @return 构建的客户端列表
     */
    private List<ReferenceCountExchangeClient> buildReferenceCountExchangeClientList(URL url, int connectNum) {
        List<ReferenceCountExchangeClient> clients = new ArrayList<>();

        // 循环构建客户端
        for (int i = 0; i < connectNum; i++) {
            clients.add(buildReferenceCountExchangeClient(url));
        }

        return clients;
    }

    /**
     * 构建单个客户端。
     *
     * @param url 服务的 URL
     * @return 构建的客户端
     */
    private ReferenceCountExchangeClient buildReferenceCountExchangeClient(URL url) {
        // 初始化客户端
        ExchangeClient exchangeClient = initClient(url);
        ReferenceCountExchangeClient client = new ReferenceCountExchangeClient(exchangeClient);
        // 读取配置
        int shutdownTimeout = ConfigurationUtils.getServerShutdownTimeout(url.getScopeModel());
        client.setShutdownWaitTime(shutdownTimeout);
        return client;
    }

    /**
     * 初始化客户端。
     *
     * @param url 服务的 URL
     * @return 初始化的客户端
     */
    private ExchangeClient initClient(URL url) {
        // 获取客户端类型
        String str = url.getParameter(CLIENT_KEY, url.getParameter(SERVER_KEY, DEFAULT_REMOTING_CLIENT));
        url = url.addParameter(CODEC_KEY, DubboCodec.NAME);
        // 启用心跳
        url = url.addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT));

        // 检查客户端类型是否支持，使用 !str.isEmpty() 替代 str.length() > 0
        if (str != null && !str.isEmpty() && !url.getOrDefaultFrameworkModel().getExtensionLoader(Transporter.class).hasExtension(str)) {
            throw new RpcException("Unsupported client type: " + str + "," +
                " supported client type is " + StringUtils.join(url.getOrDefaultFrameworkModel().getExtensionLoader(Transporter.class).getSupportedExtensions(), " "));
        }

        ExchangeClient client;
        try {
            // 判断是否为懒连接
            if (url.getParameter(LAZY_CONNECT_KEY, false)) {
                client = new LazyConnectExchangeClient(url, requestHandler);
            } else {
                client = Exchangers.connect(url, requestHandler);
            }
        } catch (RemotingException e) {
            throw new RpcException("Fail to create remoting client for service(" + url + "): " + e.getMessage(), e);
        }

        return client;
    }

    /**
     * 销毁资源。
     */
    @Override
    @SuppressWarnings("unchecked")
    public void destroy() {
        // 关闭服务器
        for (String key : new ArrayList<>(serverMap.keySet())) {
            ProtocolServer protocolServer = serverMap.remove(key);

            if (protocolServer == null) {
                continue;
            }

            RemotingServer server = protocolServer.getRemotingServer();

            try {
                // 记录关闭信息
                if (logger.isInfoEnabled()) {
                    logger.info("Closing dubbo server: " + server.getLocalAddress());
                }

                // 关闭服务器
                server.close(getServerShutdownTimeout(protocolServer));

            } catch (Throwable t) {
                // 记录关闭异常信息
                logger.warn("Close dubbo server [" + server.getLocalAddress() + "] failed: " + t.getMessage(), t);
            }
        }

        // 关闭客户端
        for (String key : new ArrayList<>(referenceClientMap.keySet())) {
            Object clients = referenceClientMap.remove(key);
            if (clients instanceof List) {
                List<ReferenceCountExchangeClient> typedClients = (List<ReferenceCountExchangeClient>) clients;

                if (CollectionUtils.isEmpty(typedClients)) {
                    continue;
                }

                for (ReferenceCountExchangeClient client : typedClients) {
                    closeReferenceCountExchangeClient(client);
                }
            }
        }

        // 调用父类的销毁方法
        super.destroy();
    }

    /**
     * 关闭引用计数客户端。
     *
     * @param client 引用计数客户端
     */
    private void closeReferenceCountExchangeClient(ReferenceCountExchangeClient client) {
        if (client == null) {
            return;
        }

        try {
            // 记录关闭信息
            if (logger.isInfoEnabled()) {
                logger.info("Close dubbo connect: " + client.getLocalAddress() + "-->" + client.getRemoteAddress());
            }

            // 关闭客户端
            client.close(client.getShutdownWaitTime());

            // TODO
            /*
             * At this time, ReferenceCountExchangeClient#client has been replaced with LazyConnectExchangeClient.
             * Do you need to call client.close again to ensure that LazyConnectExchangeClient is also closed?
             */

        } catch (Throwable t) {
            // 记录关闭异常信息
            logger.warn(t.getMessage(), t);
        }
    }

    /**
     * 仅在调试模式下记录调用信息的主体，出于大小和安全考虑。
     *
     * @param invocation 调用信息
     * @return 处理后的调用信息
     */
    private Invocation getInvocationWithoutData(Invocation invocation) {
        // 检查是否为调试模式
        if (logger.isDebugEnabled()) {
            return invocation;
        }
        // 检查是否为 RpcInvocation 类型
        if (invocation instanceof RpcInvocation) {
            RpcInvocation rpcInvocation = (RpcInvocation) invocation;
            // 移除参数
            rpcInvocation.setArguments(null);
            return rpcInvocation;
        }
        return invocation;
    }
}
