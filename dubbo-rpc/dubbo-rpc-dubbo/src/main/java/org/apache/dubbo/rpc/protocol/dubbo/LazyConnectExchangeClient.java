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

import org.apache.dubbo.common.Parameters;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.exchange.Exchangers;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.remoting.Constants.SEND_RECONNECT_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_LAZY_CONNECT_INITIAL_STATE;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_LAZY_REQUEST_WITH_WARNING;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.LAZY_CONNECT_INITIAL_STATE_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.LAZY_REQUEST_WITH_WARNING_KEY;

/**
 * dubbo protocol support class.
 */
@SuppressWarnings("deprecation")
final class LazyConnectExchangeClient implements ExchangeClient {

    /**
     * 日志记录器，用于记录该类的相关日志信息
     */
    private final static Logger logger = LoggerFactory.getLogger(LazyConnectExchangeClient.class);

    /**
     * 标记是否在请求时发出警告
     */
    protected final boolean requestWithWarning;

    /**
     * 客户端连接的URL信息
     */
    private final URL url;

    /**
     * 请求处理器，用于处理请求
     */
    private final ExchangeHandler requestHandler;

    /**
     * 用于连接操作的锁，确保线程安全
     */
    private final Lock connectLock = new ReentrantLock();

    /**
     * 警告周期，每调用指定次数发出一次警告
     */
    private final int warningPeriod = 5000;

    /**
     * lazy connect, initial state for connection
     */
    private final boolean initialState;

    /**
     * 实际的ExchangeClient对象，用于执行具体的通信操作
     */
    private volatile ExchangeClient client;

    /**
     * 记录警告调用次数的原子计数器
     */
    private AtomicLong warningcount = new AtomicLong(0);

    /**
     * 构造函数，初始化LazyConnectExchangeClient对象
     *
     * @param url            客户端连接的URL信息
     * @param requestHandler 请求处理器，用于处理请求
     */
    public LazyConnectExchangeClient(URL url, ExchangeHandler requestHandler) {
        // lazy connect, need set send.reconnect = true, to avoid channel bad status.
        this.url = new ServiceConfigURL(url.getProtocol(), url.getUsername(), url.getPassword(), url.getHost(), url.getPort(), url.getPath(), url.getParameters())
            .addParameter(SEND_RECONNECT_KEY, Boolean.TRUE.toString());
        this.requestHandler = requestHandler;
        this.initialState = url.getParameter(LAZY_CONNECT_INITIAL_STATE_KEY, DEFAULT_LAZY_CONNECT_INITIAL_STATE);
        this.requestWithWarning = url.getParameter(LAZY_REQUEST_WITH_WARNING_KEY, DEFAULT_LAZY_REQUEST_WITH_WARNING);
    }

    /**
     * 初始化客户端连接
     *
     * @throws RemotingException 如果在初始化过程中发生远程连接异常
     */
    private void initClient() throws RemotingException {
        if (client != null) {
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("Lazy connect to " + url);
        }
        connectLock.lock();
        try {
            if (client != null) {
                return;
            }
            this.client = Exchangers.connect(url, requestHandler);
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * 发送请求并返回一个CompletableFuture对象
     *
     * @param request 请求对象
     * @return 一个CompletableFuture对象，用于异步获取请求结果
     * @throws RemotingException 如果在请求过程中发生远程连接异常
     */
    @Override
    public CompletableFuture<Object> request(Object request) throws RemotingException {
        warning();
        initClient();
        return client.request(request);
    }

    /**
     * 获取客户端连接的URL信息
     *
     * @return 客户端连接的URL信息
     */
    @Override
    public URL getUrl() {
        return url;
    }

    /**
     * 获取远程地址
     *
     * @return 远程地址的InetSocketAddress对象
     */
    @Override
    public InetSocketAddress getRemoteAddress() {
        if (client == null) {
            return InetSocketAddress.createUnresolved(url.getHost(), url.getPort());
        } else {
            return client.getRemoteAddress();
        }
    }

    /**
     * 发送请求并指定超时时间，返回一个CompletableFuture对象
     *
     * @param request 请求对象
     * @param timeout 超时时间（毫秒）
     * @return 一个CompletableFuture对象，用于异步获取请求结果
     * @throws RemotingException 如果在请求过程中发生远程连接异常
     */
    @Override
    public CompletableFuture<Object> request(Object request, int timeout) throws RemotingException {
        warning();
        initClient();
        return client.request(request, timeout);
    }

    /**
     * 发送请求并指定执行器，返回一个CompletableFuture对象
     *
     * @param request  请求对象
     * @param executor 执行器，用于处理请求
     * @return 一个CompletableFuture对象，用于异步获取请求结果
     * @throws RemotingException 如果在请求过程中发生远程连接异常
     */
    @Override
    public CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException {
        warning();
        initClient();
        return client.request(request, executor);
    }

    /**
     * 发送请求并指定超时时间和执行器，返回一个CompletableFuture对象
     *
     * @param request  请求对象
     * @param timeout  超时时间（毫秒）
     * @param executor 执行器，用于处理请求
     * @return 一个CompletableFuture对象，用于异步获取请求结果
     * @throws RemotingException 如果在请求过程中发生远程连接异常
     */
    @Override
    public CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException {
        warning();
        initClient();
        return client.request(request, timeout, executor);
    }

    /**
     * 如果配置了请求警告，则每调用指定次数发出一次警告
     */
    private void warning() {
        if (requestWithWarning) {
            if (warningcount.get() % warningPeriod == 0) {
                logger.warn(url.getAddress() + " " + url.getServiceKey() + " safe guard client , should not be called ,must have a bug.");
            }
            warningcount.incrementAndGet();
        }
    }

    /**
     * 获取通道处理器
     *
     * @return 通道处理器对象
     */
    @Override
    public ChannelHandler getChannelHandler() {
        checkClient();
        return client.getChannelHandler();
    }

    /**
     * 检查客户端是否已连接
     *
     * @return 如果客户端已连接返回true，否则返回false
     */
    @Override
    public boolean isConnected() {
        if (client == null) {
            return initialState;
        } else {
            return client.isConnected();
        }
    }

    /**
     * 获取本地地址
     *
     * @return 本地地址的InetSocketAddress对象
     */
    @Override
    public InetSocketAddress getLocalAddress() {
        if (client == null) {
            return InetSocketAddress.createUnresolved(NetUtils.getLocalHost(), 0);
        } else {
            return client.getLocalAddress();
        }
    }

    /**
     * 获取交换处理器
     *
     * @return 交换处理器对象
     */
    @Override
    public ExchangeHandler getExchangeHandler() {
        return requestHandler;
    }

    /**
     * 发送消息
     *
     * @param message 要发送的消息对象
     * @throws RemotingException 如果在发送过程中发生远程连接异常
     */
    @Override
    public void send(Object message) throws RemotingException {
        initClient();
        client.send(message);
    }

    /**
     * 发送消息并指定是否等待发送完成
     *
     * @param message 要发送的消息对象
     * @param sent    是否等待发送完成
     * @throws RemotingException 如果在发送过程中发生远程连接异常
     */
    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        initClient();
        client.send(message, sent);
    }

    /**
     * 检查客户端是否已关闭
     *
     * @return 如果客户端已关闭返回true，否则返回false
     */
    @Override
    public boolean isClosed() {
        if (client != null) {
            return client.isClosed();
        } else {
            return false;
        }
    }

    /**
     * 关闭客户端连接
     */
    @Override
    public void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /**
     * 在指定时间内关闭客户端连接
     *
     * @param timeout 超时时间（毫秒）
     */
    @Override
    public void close(int timeout) {
        if (client != null) {
            client.close(timeout);
            client = null;
        }
    }

    /**
     * 开始关闭客户端连接
     */
    @Override
    public void startClose() {
        if (client != null) {
            client.startClose();
        }
    }

    /**
     * 重置客户端连接的URL信息
     *
     * @param url 新的URL信息
     */
    @Override
    public void reset(URL url) {
        checkClient();
        client.reset(url);
    }

    /**
     * 重置客户端连接的参数信息
     *
     * @param parameters 新的参数信息
     */
    @Override
    @Deprecated
    public void reset(Parameters parameters) {
        reset(getUrl().addParameters(parameters.getParameters()));
    }

    /**
     * 重新连接客户端
     *
     * @throws RemotingException 如果在重新连接过程中发生远程连接异常
     */
    @Override
    public void reconnect() throws RemotingException {
        checkClient();
        client.reconnect();
    }

    /**
     * 获取指定键的属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回null
     */
    @Override
    public Object getAttribute(String key) {
        if (client == null) {
            return null;
        } else {
            return client.getAttribute(key);
        }
    }

    /**
     * 设置指定键的属性值
     *
     * @param key   属性键
     * @param value 属性值
     */
    @Override
    public void setAttribute(String key, Object value) {
        checkClient();
        client.setAttribute(key, value);
    }

    /**
     * 移除指定键的属性
     *
     * @param key 属性键
     */
    @Override
    public void removeAttribute(String key) {
        checkClient();
        client.removeAttribute(key);
    }

    /**
     * 检查是否存在指定键的属性
     *
     * @param key 属性键
     * @return 如果存在返回true，否则返回false
     */
    @Override
    public boolean hasAttribute(String key) {
        if (client == null) {
            return false;
        } else {
            return client.hasAttribute(key);
        }
    }

    /**
     * 检查客户端是否已初始化，如果未初始化则抛出异常
     */
    private void checkClient() {
        if (client == null) {
            throw new IllegalStateException(
                "LazyConnectExchangeClient state error. the client has not be init .url:" + url);
        }
    }
}
