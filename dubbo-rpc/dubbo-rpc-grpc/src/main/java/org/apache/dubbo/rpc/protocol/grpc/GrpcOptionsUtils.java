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
package org.apache.dubbo.rpc.protocol.grpc;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ServerBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.rpc.protocol.grpc.interceptors.ClientInterceptor;
import org.apache.dubbo.rpc.protocol.grpc.interceptors.GrpcConfigurator;
import org.apache.dubbo.rpc.protocol.grpc.interceptors.ServerInterceptor;
import org.apache.dubbo.rpc.protocol.grpc.interceptors.ServerTransportFilter;

import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.SSL_ENABLED_KEY;
import static org.apache.dubbo.remoting.Constants.DISPATCHER_KEY;
import static org.apache.dubbo.rpc.Constants.EXECUTES_KEY;
import static org.apache.dubbo.rpc.protocol.grpc.GrpcConstants.CLIENT_INTERCEPTORS;
import static org.apache.dubbo.rpc.protocol.grpc.GrpcConstants.EXECUTOR;
import static org.apache.dubbo.rpc.protocol.grpc.GrpcConstants.MAX_CONCURRENT_CALLS_PER_CONNECTION;
import static org.apache.dubbo.rpc.protocol.grpc.GrpcConstants.MAX_INBOUND_MESSAGE_SIZE;
import static org.apache.dubbo.rpc.protocol.grpc.GrpcConstants.MAX_INBOUND_METADATA_SIZE;
import static org.apache.dubbo.rpc.protocol.grpc.GrpcConstants.SERVER_INTERCEPTORS;
import static org.apache.dubbo.rpc.protocol.grpc.GrpcConstants.TRANSPORT_FILTERS;

/**
 * Support gRPC configs in a Dubbo specific way.
 */
/**
 * Support gRPC configs in a Dubbo specific way.
 */
public class GrpcOptionsUtils {

    /**
     * 构建gRPC服务器构建器，根据URL参数配置服务器构建器的各项参数
     *
     * @param url     包含配置信息的URL
     * @param builder Netty服务器构建器
     * @return 配置好的服务器构建器
     */
    static ServerBuilder buildServerBuilder(URL url, NettyServerBuilder builder) {

        // 从URL中获取最大入站消息大小，如果大于0则设置到构建器中
        int maxInboundMessageSize = url.getParameter(MAX_INBOUND_MESSAGE_SIZE, 0);
        if (maxInboundMessageSize > 0) {
            builder.maxInboundMessageSize(maxInboundMessageSize);
        }

        // 从URL中获取最大入站元数据大小，如果大于0则设置到构建器中
        int maxInboundMetadataSize = url.getParameter(MAX_INBOUND_METADATA_SIZE, 0);
        if (maxInboundMetadataSize > 0) {
            builder.maxInboundMetadataSize(maxInboundMetadataSize);
        }

        // 如果URL中配置了启用SSL，则为构建器设置SSL上下文
        if (url.getParameter(SSL_ENABLED_KEY, false)) {
            builder.sslContext(buildServerSslContext(url));
        }

        // 从URL中获取流控制窗口大小，如果大于0则设置到构建器中
        int flowControlWindow = url.getParameter(MAX_INBOUND_MESSAGE_SIZE, 0);
        if (flowControlWindow > 0) {
            builder.flowControlWindow(flowControlWindow);
        }

        // 从URL中获取每个连接的最大并发调用数，如果大于0则设置到构建器中
        int maxCalls = url.getParameter(MAX_CONCURRENT_CALLS_PER_CONNECTION, url.getParameter(EXECUTES_KEY, 0));
        if (maxCalls > 0) {
            builder.maxConcurrentCallsPerConnection(maxCalls);
        }

        // 服务器拦截器配置
        // 从扩展加载器中获取激活的服务器拦截器列表
        List<ServerInterceptor> serverInterceptors = url.getOrDefaultFrameworkModel().getExtensionLoader(ServerInterceptor.class)
                .getActivateExtension(url, SERVER_INTERCEPTORS, PROVIDER_SIDE);
        // 为构建器添加每个服务器拦截器
        for (ServerInterceptor serverInterceptor : serverInterceptors) {
            builder.intercept(serverInterceptor);
        }

        // 服务器传输过滤器配置
        // 从扩展加载器中获取激活的服务器传输过滤器列表
        List<ServerTransportFilter> transportFilters = url.getOrDefaultFrameworkModel().getExtensionLoader(ServerTransportFilter.class)
                .getActivateExtension(url, TRANSPORT_FILTERS, PROVIDER_SIDE);
        // 为构建器添加每个服务器传输过滤器
        for (ServerTransportFilter transportFilter : transportFilters) {
            builder.addTransportFilter(transportFilter.grpcTransportFilter());
        }

        // 线程池配置
        // 从URL中获取线程池配置信息
        String thread = url.getParameter(EXECUTOR, url.getParameter(DISPATCHER_KEY));
        if ("direct".equals(thread)) {
            // 如果配置为直接执行器，则使用直接执行器
            builder.directExecutor();
        } else {
            // 否则，从扩展加载器中获取自适应线程池并设置到构建器中
            builder.executor(url.getOrDefaultFrameworkModel().getExtensionLoader(ThreadPool.class).getAdaptiveExtension().getExecutor(url));
        }

        // 给用户提供自定义ServerBuilder的机会
        return getConfigurator()
                .map(configurator -> configurator.configureServerBuilder(builder, url))
                .orElse(builder);
    }

    /**
     * 构建gRPC管理通道，根据URL参数配置通道构建器的各项参数
     *
     * @param url 包含配置信息的URL
     * @return 配置好的管理通道
     */
    static ManagedChannel buildManagedChannel(URL url) {

        // 创建Netty通道构建器，指定地址
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(url.getHost(), url.getPort());
        if (url.getParameter(SSL_ENABLED_KEY, false)) {
            // 如果URL中配置了启用SSL，则为构建器设置SSL上下文
            builder.sslContext(buildClientSslContext(url));
        } else {
            // 否则，使用明文传输
            builder.usePlaintext();
        }

        // 禁用重试机制
        builder.disableRetry();
//        builder.directExecutor();

        // 客户端拦截器配置
        // 从扩展加载器中获取激活的客户端拦截器列表
        List<io.grpc.ClientInterceptor> interceptors = new ArrayList<>(
                url.getOrDefaultFrameworkModel().getExtensionLoader(ClientInterceptor.class)
                        .getActivateExtension(url, CLIENT_INTERCEPTORS, CONSUMER_SIDE)
        );
        // 为构建器添加客户端拦截器
        builder.intercept(interceptors);

        // 给用户提供自定义通道构建器的机会
        return getConfigurator()
                .map(configurator -> configurator.configureChannelBuilder(builder, url))
                .orElse(builder)
                .build();
    }

    /**
     * 构建gRPC调用选项，根据URL参数配置调用选项
     *
     * @param url 包含配置信息的URL
     * @return 配置好的调用选项
     */
    static CallOptions buildCallOptions(URL url) {
        // gRPC Deadline starts counting when it's created, so we need to create and add a new Deadline for each RPC call.
//        CallOptions callOptions = CallOptions.DEFAULT
//                .withDeadline(Deadline.after(url.getParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS));
        // 使用默认调用选项
        CallOptions callOptions = CallOptions.DEFAULT;
        // 给用户提供自定义调用选项的机会
        return getConfigurator()
                .map(configurator -> configurator.configureCallOptions(callOptions, url))
                .orElse(callOptions);
    }

    /**
     * 构建服务器SSL上下文，根据URL中的SSL配置信息创建SSL上下文
     *
     * @param url 包含配置信息的URL
     * @return 配置好的SSL上下文
     */
    private static SslContext buildServerSslContext(URL url) {
        // 获取全局配置管理器
        ConfigManager globalConfigManager = url.getOrDefaultApplicationModel().getApplicationConfigManager();
        // 获取SSL配置，如果未配置则抛出异常
        SslConfig sslConfig = globalConfigManager.getSsl().orElseThrow(() -> new IllegalStateException("Ssl enabled, but no ssl cert information provided!"));

        // 初始化SSL上下文构建器
        SslContextBuilder sslClientContextBuilder;
        try {
            // 获取服务器私钥密码
            String password = sslConfig.getServerKeyPassword();
            if (password != null) {
                // 如果有密码，使用密码初始化SSL上下文构建器
                sslClientContextBuilder = GrpcSslContexts.forServer(sslConfig.getServerKeyCertChainPathStream(),
                        sslConfig.getServerPrivateKeyPathStream(), password);
            } else {
                // 否则，不使用密码初始化SSL上下文构建器
                sslClientContextBuilder = GrpcSslContexts.forServer(sslConfig.getServerKeyCertChainPathStream(),
                        sslConfig.getServerPrivateKeyPathStream());
            }

            // 获取服务器信任证书集合输入流
            InputStream trustCertCollectionFilePath = sslConfig.getServerTrustCertCollectionPathStream();
            if (trustCertCollectionFilePath != null) {
                // 如果有信任证书集合，设置信任管理器并要求客户端认证
                sslClientContextBuilder.trustManager(trustCertCollectionFilePath);
                sslClientContextBuilder.clientAuth(ClientAuth.REQUIRE);
            }
        } catch (Exception e) {
            // 如果出现异常，抛出非法参数异常
            throw new IllegalArgumentException("Could not find certificate file or the certificate is invalid.", e);
        }
        try {
            // 构建SSL上下文
            return sslClientContextBuilder.build();
        } catch (SSLException e) {
            // 如果构建失败，抛出非法状态异常
            throw new IllegalStateException("Build SslSession failed.", e);
        }
    }

    /**
     * 构建客户端SSL上下文，根据URL中的SSL配置信息创建SSL上下文
     *
     * @param url 包含配置信息的URL
     * @return 配置好的SSL上下文
     */
    private static SslContext buildClientSslContext(URL url) {
        // 获取全局配置管理器
        ConfigManager globalConfigManager = url.getOrDefaultApplicationModel().getApplicationConfigManager();
        // 获取SSL配置，如果未配置则抛出异常
        SslConfig sslConfig = globalConfigManager.getSsl().orElseThrow(() -> new IllegalStateException("Ssl enabled, but no ssl cert information provided!"));

        // 创建客户端SSL上下文构建器
        SslContextBuilder builder = GrpcSslContexts.forClient();
        try {
            // 获取客户端信任证书集合输入流
            InputStream trustCertCollectionFilePath = sslConfig.getClientTrustCertCollectionPathStream();
            if (trustCertCollectionFilePath != null) {
                // 如果有信任证书集合，设置信任管理器
                builder.trustManager(trustCertCollectionFilePath);
            }
            // 获取客户端证书链输入流和客户端私钥输入流
            InputStream clientCertChainFilePath = sslConfig.getClientKeyCertChainPathStream();
            InputStream clientPrivateKeyFilePath = sslConfig.getClientPrivateKeyPathStream();
            if (clientCertChainFilePath != null && clientPrivateKeyFilePath != null) {
                // 如果有客户端证书链和私钥，设置客户端密钥管理器
                String password = sslConfig.getClientKeyPassword();
                if (password != null) {
                    // 如果有密码，使用密码设置客户端密钥管理器
                    builder.keyManager(clientCertChainFilePath, clientPrivateKeyFilePath, password);
                } else {
                    // 否则，不使用密码设置客户端密钥管理器
                    builder.keyManager(clientCertChainFilePath, clientPrivateKeyFilePath);
                }
            }
        } catch (Exception e) {
            // 如果出现异常，抛出非法参数异常
            throw new IllegalArgumentException("Could not find certificate file or find invalid certificate.", e);
        }
        try {
            // 构建SSL上下文
            return builder.build();
        } catch (SSLException e) {
            // 如果构建失败，抛出非法状态异常
            throw new IllegalStateException("Build SslSession failed.", e);
        }
    }

    /**
     * 获取GrpcConfigurator实例，用于自定义服务器构建器、通道构建器和调用选项
     *
     * @return 可选的GrpcConfigurator实例
     */
    private static Optional<GrpcConfigurator> getConfigurator() {
        // Give users the chance to customize ServerBuilder
        // 从扩展加载器中获取支持的GrpcConfigurator实例集合
        Set<GrpcConfigurator> configurators = ExtensionLoader.getExtensionLoader(GrpcConfigurator.class)
                .getSupportedExtensionInstances();
        if (CollectionUtils.isNotEmpty(configurators)) {
            // 如果集合不为空，返回第一个实例
            return Optional.of(configurators.iterator().next());
        }
        // 否则，返回空的Optional
        return Optional.empty();
    }
}
