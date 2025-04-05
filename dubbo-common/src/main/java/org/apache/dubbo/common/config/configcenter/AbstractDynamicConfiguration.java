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
package org.apache.dubbo.common.config.configcenter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * The abstract implementation of {@link DynamicConfiguration}
 *
 * @since 2.7.5
 */
public abstract class AbstractDynamicConfiguration implements DynamicConfiguration {

    // 配置中心参数名前缀
    public static final String PARAM_NAME_PREFIX = "dubbo.config-center.";

    // 线程池前缀参数名
    public static final String THREAD_POOL_PREFIX_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.prefix";

    // 默认线程池前缀
    public static final String DEFAULT_THREAD_POOL_PREFIX = PARAM_NAME_PREFIX + "workers";

    // 线程池大小参数名
    public static final String THREAD_POOL_SIZE_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.size";

    // 线程池线程保持存活时间参数名（毫秒）
    public static final String THREAD_POOL_KEEP_ALIVE_TIME_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.keep-alive-time";

    // 配置中心分组参数名
    public static final String GROUP_PARAM_NAME = PARAM_NAME_PREFIX + GROUP_KEY;

    // 配置中心超时参数名
    public static final String TIMEOUT_PARAM_NAME = PARAM_NAME_PREFIX + TIMEOUT_KEY;

    // 默认线程池大小
    public static final int DEFAULT_THREAD_POOL_SIZE = 1;

    // 默认线程保持存活时间（1分钟）
    public static final long DEFAULT_THREAD_POOL_KEEP_ALIVE_TIME = TimeUnit.MINUTES.toMillis(1);

    // 日志记录器
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // 工作线程池
    private final ThreadPoolExecutor workersThreadPool;

    // 分组名称
    private final String group;

    // 超时时间
    private final long timeout;

    // 构造函数，基于URL初始化
    public AbstractDynamicConfiguration(URL url) {
        this(getThreadPoolPrefixName(url), getThreadPoolSize(url), getThreadPoolKeepAliveTime(url), getGroup(url),
                getTimeout(url));
    }

    // 构造函数，基于参数初始化
    public AbstractDynamicConfiguration(String threadPoolPrefixName,
                                        int threadPoolSize,
                                        long keepAliveTime,
                                        String group,
                                        long timeout) {
        this.workersThreadPool = initWorkersThreadPool(threadPoolPrefixName, threadPoolSize, keepAliveTime);
        this.group = group;
        this.timeout = timeout;
    }

    // 添加配置监听器（空实现）
    @Override
    public void addListener(String key, String group, ConfigurationListener listener) {
    }

    // 移除配置监听器（空实现）
    @Override
    public void removeListener(String key, String group, ConfigurationListener listener) {
    }

    // 获取配置（带超时）
    @Override
    public final String getConfig(String key, String group, long timeout) throws IllegalStateException {
        return execute(() -> doGetConfig(key, group), timeout);
    }

    // 获取内部属性（空实现）
    @Override
    public Object getInternalProperty(String key) {
        return null;
    }

    // 关闭配置中心
    @Override
    public final void close() throws Exception {
        try {
            doClose();
        } finally {
            doFinally();
        }
    }

    // 移除配置
    @Override
    public boolean removeConfig(String key, String group) {
        return Boolean.TRUE.equals(execute(() -> doRemoveConfig(key, group), -1L));
    }

    // 获取默认分组
    @Override
    public String getDefaultGroup() {
        return getGroup();
    }

    // 获取默认超时时间
    @Override
    public long getDefaultTimeout() {
        return getTimeout();
    }

    // 抽象方法：获取配置内容
    protected abstract String doGetConfig(String key, String group) throws Exception;

    // 抽象方法：关闭资源
    protected abstract void doClose() throws Exception;

    // 抽象方法：移除配置
    protected abstract boolean doRemoveConfig(String key, String group) throws Exception;

    // 执行Runnable任务（带超时）
    protected final void execute(Runnable task, long timeout) {
        execute(() -> {
            task.run();
            return null;
        }, timeout);
    }

    // 执行Callable任务（带超时）
    protected final <V> V execute(Callable<V> task, long timeout) {
        V value = null;
        try {
            if (timeout < 1) { // 小于等于0
                value = task.call();
            } else {
                Future<V> future = workersThreadPool.submit(task);
                value = future.get(timeout, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(e.getMessage(), e);
            }
        }
        return value;
    }

    // 获取工作线程池
    protected ThreadPoolExecutor getWorkersThreadPool() {
        return workersThreadPool;
    }

    // 最终处理（关闭线程池）
    private void doFinally() {
        shutdownWorkersThreadPool();
    }

    // 关闭工作线程池
    private void shutdownWorkersThreadPool() {
        if (!workersThreadPool.isShutdown()) {
            workersThreadPool.shutdown();
        }
    }

    // 初始化工作线程池
    protected ThreadPoolExecutor initWorkersThreadPool(String threadPoolPrefixName,
                                                       int threadPoolSize,
                                                       long keepAliveTime) {
        return new ThreadPoolExecutor(threadPoolSize, threadPoolSize, keepAliveTime,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory(threadPoolPrefixName, true));
    }

    // 从URL获取线程池前缀名称
    protected static String getThreadPoolPrefixName(URL url) {
        return getParameter(url, THREAD_POOL_PREFIX_PARAM_NAME, DEFAULT_THREAD_POOL_PREFIX);
    }

    // 从URL获取线程池大小
    protected static int getThreadPoolSize(URL url) {
        return getParameter(url, THREAD_POOL_SIZE_PARAM_NAME, DEFAULT_THREAD_POOL_SIZE);
    }

    // 从URL获取线程池保持存活时间
    protected static long getThreadPoolKeepAliveTime(URL url) {
        return getParameter(url, THREAD_POOL_KEEP_ALIVE_TIME_PARAM_NAME, DEFAULT_THREAD_POOL_KEEP_ALIVE_TIME);
    }

    // 从URL获取String类型参数
    protected static String getParameter(URL url, String name, String defaultValue) {
        if (url != null) {
            return url.getParameter(name, defaultValue);
        }
        return defaultValue;
    }

    // 从URL获取int类型参数
    protected static int getParameter(URL url, String name, int defaultValue) {
        if (url != null) {
            return url.getParameter(name, defaultValue);
        }
        return defaultValue;
    }

    // 从URL获取long类型参数
    protected static long getParameter(URL url, String name, long defaultValue) {
        if (url != null) {
            return url.getParameter(name, defaultValue);
        }
        return defaultValue;
    }

    // 获取分组名称
    protected String getGroup() {
        return group;
    }

    // 获取超时时间
    protected long getTimeout() {
        return timeout;
    }

    // 从URL获取分组名称
    protected static String getGroup(URL url) {
        String group = getParameter(url, GROUP_PARAM_NAME, null);
        return StringUtils.isBlank(group) ? getParameter(url, GROUP_KEY, DEFAULT_GROUP) : group;
    }

    // 从URL获取超时时间
    protected static long getTimeout(URL url) {
        return getParameter(url, TIMEOUT_PARAM_NAME, -1L);
    }
}
