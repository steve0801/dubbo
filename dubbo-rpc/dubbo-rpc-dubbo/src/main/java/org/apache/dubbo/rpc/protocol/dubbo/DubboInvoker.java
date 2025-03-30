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
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.utils.AtomicPositiveInteger;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.TimeoutException;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.FutureContext;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.TimeoutCountDown;
import org.apache.dubbo.rpc.protocol.AbstractInvoker;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_VERSION;
import static org.apache.dubbo.common.constants.CommonConstants.ENABLE_TIMEOUT_COUNTDOWN_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_ATTACHMENT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIME_COUNTDOWN_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;

/**
 * DubboInvoker
 */
/**
 * DubboInvoker 类用于实现 Dubbo 协议的远程调用逻辑，继承自 AbstractInvoker 类。
 */
public class DubboInvoker<T> extends AbstractInvoker<T> {

    /**
     * 存储与远程服务建立的连接客户端数组
     */
    private final ExchangeClient[] clients;

    /**
     * 用于轮询选择客户端的原子正整数索引
     */
    private final AtomicPositiveInteger index = new AtomicPositiveInteger();

    /**
     * 服务的版本号
     */
    private final String version;

    /**
     * 用于保证销毁操作线程安全的可重入锁
     */
    private final ReentrantLock destroyLock = new ReentrantLock();

    /**
     * 存储相关的 Invoker 集合
     */
    private final Set<Invoker<?>> invokers;

    /**
     * 服务器关闭时的超时时间
     */
    private final int serverShutdownTimeout;

    /**
     * 构造函数，调用另一个构造函数并将 invokers 参数设为 null
     *
     * @param serviceType 服务接口类型
     * @param url         服务的 URL 地址
     * @param clients     客户端连接数组
     */
    public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients) {
        this(serviceType, url, clients, null);
    }

    /**
     * 构造函数，初始化 DubboInvoker 的各项属性
     *
     * @param serviceType 服务接口类型
     * @param url         服务的 URL 地址
     * @param clients     客户端连接数组
     * @param invokers    相关的 Invoker 集合
     */
    public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients, Set<Invoker<?>> invokers) {
        // 调用父类构造函数，传入服务类型、URL 和需要关注的参数键
        super(serviceType, url, new String[]{INTERFACE_KEY, GROUP_KEY, TOKEN_KEY});
        this.clients = clients;
        // 获取服务的版本号
        this.version = url.getVersion(DEFAULT_VERSION);
        this.invokers = invokers;
        // 获取服务器关闭的超时时间
        this.serverShutdownTimeout = ConfigurationUtils.getServerShutdownTimeout(getUrl().getScopeModel());
    }

    /**
     * 执行远程调用的核心方法
     *
     * @param invocation 调用信息对象
     * @return 调用结果
     * @throws Throwable 可能抛出的异常
     */
    @Override
    protected Result doInvoke(final Invocation invocation) throws Throwable {
        // 将 Invocation 转换为 RpcInvocation 类型
        RpcInvocation inv = (RpcInvocation) invocation;
        // 获取调用的方法名
        final String methodName = RpcUtils.getMethodName(invocation);
        // 设置调用的路径和版本信息
        inv.setAttachment(PATH_KEY, getUrl().getPath());
        inv.setAttachment(VERSION_KEY, version);

        ExchangeClient currentClient;
        if (clients.length == 1) {
            // 如果只有一个客户端，直接使用该客户端
            currentClient = clients[0];
        } else {
            // 多个客户端时，通过轮询方式选择一个客户端
            currentClient = clients[index.getAndIncrement() % clients.length];
        }
        try {
            // 判断是否为单向调用
            boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
            // 计算调用的超时时间
            int timeout = calculateTimeout(invocation, methodName);
            // 将超时时间设置到调用信息中
            invocation.put(TIMEOUT_KEY, timeout);
            if (isOneway) {
                // 单向调用，不需要等待响应
                boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
                currentClient.send(inv, isSent);
                return AsyncRpcResult.newDefaultAsyncResult(invocation);
            } else {
                // 非单向调用，需要等待响应
                ExecutorService executor = getCallbackExecutor(getUrl(), inv);
                CompletableFuture<AppResponse> appResponseFuture =
                        currentClient.request(inv, timeout, executor).thenApply(obj -> (AppResponse) obj);
                // 保存为 2.6.x 版本兼容性使用
                FutureContext.getContext().setCompatibleFuture(appResponseFuture);
                AsyncRpcResult result = new AsyncRpcResult(appResponseFuture, inv);
                result.setExecutor(executor);
                return result;
            }
        } catch (TimeoutException e) {
            // 处理超时异常
            throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        } catch (RemotingException e) {
            // 处理远程通信异常
            throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * 检查 Invoker 是否可用
     *
     * @return 如果可用返回 true，否则返回 false
     */
    @Override
    public boolean isAvailable() {
        if (!super.isAvailable()) {
            return false;
        }
        for (ExchangeClient client : clients) {
            if (client.isConnected() && !client.hasAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 销毁 Invoker，调用 destroyInternal 方法，不关闭所有连接
     */
    @Override
    public void destroy() {
        destroyInternal(false);
    }

    /**
     * 销毁 Invoker 并关闭所有连接，调用 destroyInternal 方法
     */
    @Override
    public void destroyAll() {
        destroyInternal(true);
    }

    /**
     * 内部销毁方法，根据 closeAll 参数决定是否关闭所有连接
     *
     * @param closeAll 是否关闭所有连接
     */
    private void destroyInternal(boolean closeAll) {
        // 避免重复关闭
        if (!super.isDestroyed()) {
            // 加锁保证线程安全
            destroyLock.lock();
            try {
                if (super.isDestroyed()) {
                    return;
                }
                // 调用父类的销毁方法
                super.destroy();
                if (invokers != null) {
                    // 从 invokers 集合中移除当前 Invoker
                    invokers.remove(this);
                }
                for (ExchangeClient client : clients) {
                    try {
                        if (closeAll) {
                            // 关闭所有连接
                            client.closeAll(serverShutdownTimeout);
                        } else {
                            // 关闭单个连接
                            client.close(serverShutdownTimeout);
                        }
                    } catch (Throwable t) {
                        // 记录关闭异常信息
                        logger.warn(t.getMessage(), t);
                    }
                }

            } finally {
                // 释放锁
                destroyLock.unlock();
            }
        }
    }

    /**
     * 计算调用的超时时间
     *
     * @param invocation 调用信息对象
     * @param methodName 调用的方法名
     * @return 超时时间
     */
    private int calculateTimeout(Invocation invocation, String methodName) {
        // 检查是否有超时倒计时信息
        Object countdown = RpcContext.getClientAttachment().getObjectAttachment(TIME_COUNTDOWN_KEY);
        int timeout = DEFAULT_TIMEOUT;
        if (countdown == null) {
            // 没有倒计时信息，从 URL 中获取超时时间
            timeout = (int) RpcUtils.getTimeout(getUrl(), methodName, RpcContext.getClientAttachment(), DEFAULT_TIMEOUT);
            if (getUrl().getParameter(ENABLE_TIMEOUT_COUNTDOWN_KEY, false)) {
                // 如果启用了超时倒计时，将超时时间传递给远程服务器
                invocation.setObjectAttachment(TIMEOUT_ATTACHMENT_KEY, timeout);
            }
        } else {
            // 有倒计时信息，获取剩余的超时时间
            TimeoutCountDown timeoutCountDown = (TimeoutCountDown) countdown;
            timeout = (int) timeoutCountDown.timeRemaining(TimeUnit.MILLISECONDS);
            // 将超时时间传递给远程服务器
            invocation.setObjectAttachment(TIMEOUT_ATTACHMENT_KEY, timeout);
        }
        return timeout;
    }
}
