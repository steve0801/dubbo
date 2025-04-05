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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.router.state.AddrCache;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.apache.dubbo.rpc.cluster.router.state.RouterCache;
import org.apache.dubbo.rpc.cluster.router.state.StateRouter;
import org.apache.dubbo.rpc.cluster.router.state.StateRouterFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.apache.dubbo.rpc.cluster.Constants.ROUTER_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.STATE_ROUTER_KEY;

/**
 * Router chain
 */
// 路由器链类，用于管理和执行多个路由器
public class RouterChain<T> {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(RouterChain.class);

    // 从注册中心获取的完整地址列表，按方法名分类
    private volatile List<Invoker<T>> invokers = Collections.emptyList();

    // 包含所有路由器的列表，每次'route://' URLs变化时重建
    private volatile List<Router> routers = Collections.emptyList();

    // 固定的路由器实例，例如ConfigConditionRouter, TagRouter等
    private List<Router> builtinRouters = Collections.emptyList();

    // 固定的状态路由器实例
    private List<StateRouter> builtinStateRouters = Collections.emptyList();
    // 当前状态路由器列表
    private List<StateRouter> stateRouters = Collections.emptyList();
    // 执行器仓库
    private final ExecutorRepository executorRepository;

    // URL对象
    protected URL url;

    // 地址缓存
    private AtomicReference<AddrCache<T>> cache = new AtomicReference<>();

    // 循环许可信号量
    private final Semaphore loopPermit = new Semaphore(1);
    private final Semaphore loopPermitNotify = new Semaphore(1);

    // 循环线程池
    private final ExecutorService loopPool;

    // 首次构建缓存标志
    private AtomicBoolean firstBuildCache = new AtomicBoolean(true);

    // 构建路由器链的静态方法
    public static <T> RouterChain<T> buildChain(URL url) {
        return new RouterChain<>(url);
    }

    // 构造函数，初始化路由器链
    private RouterChain(URL url) {
        executorRepository = url.getOrDefaultApplicationModel().getExtensionLoader(ExecutorRepository.class)
            .getDefaultExtension();
        loopPool = executorRepository.nextExecutorExecutor();
        List<RouterFactory> extensionFactories = url.getOrDefaultApplicationModel().getExtensionLoader(RouterFactory.class)
            .getActivateExtension(url, ROUTER_KEY);

        List<Router> routers = extensionFactories.stream()
            .map(factory -> factory.getRouter(url))
            .sorted(Router::compareTo)
            .collect(Collectors.toList());

        initWithRouters(routers);

        List<StateRouterFactory> extensionStateRouterFactories = url.getOrDefaultApplicationModel()
            .getExtensionLoader(StateRouterFactory.class)
            .getActivateExtension(url, STATE_ROUTER_KEY);

        List<StateRouter> stateRouters = extensionStateRouterFactories.stream()
            .map(factory -> factory.getRouter(url, this))
            .sorted(StateRouter::compareTo)
            .collect(Collectors.toList());

        // 初始化状态路由器
        initWithStateRouters(stateRouters);
    }

    // 初始化固定路由器
    public void initWithRouters(List<Router> builtinRouters) {
        this.builtinRouters = builtinRouters;
        this.routers = new ArrayList<>(builtinRouters);
    }

    // 初始化固定状态路由器
    private void initWithStateRouters(List<StateRouter> builtinRouters) {
        this.builtinStateRouters = builtinRouters;
        this.stateRouters = new ArrayList<>(builtinRouters);
    }

    // 添加路由器
    public void addRouters(List<Router> routers) {
        List<Router> newRouters = new ArrayList<>();
        newRouters.addAll(builtinRouters);
        newRouters.addAll(routers);
        CollectionUtils.sort(newRouters);
        this.routers = newRouters;
    }

    // 添加状态路由器
    public void addStateRouters(List<StateRouter> stateRouters) {
        List<StateRouter> newStateRouters = new ArrayList<>();
        newStateRouters.addAll(builtinStateRouters);
        newStateRouters.addAll(stateRouters);
        CollectionUtils.sort(newStateRouters);
        this.stateRouters = newStateRouters;
    }

    // 获取路由器列表
    public List<Router> getRouters() {
        return routers;
    }

    // 获取状态路由器列表
    public List<StateRouter> getStateRouters() {
        return stateRouters;
    }

    // 路由方法，根据URL和调用信息选择调用者
    public List<Invoker<T>> route(URL url, Invocation invocation) {

        AddrCache<T> cache = this.cache.get();
        List<Invoker<T>> finalInvokers = null;

        if (cache != null) {
            BitList<Invoker<T>> finalBitListInvokers = new BitList<>(invokers, false);
            for (StateRouter stateRouter : stateRouters) {
                if (stateRouter.isEnable()) {
                    RouterCache<T> routerCache = cache.getCache().get(stateRouter.getName());
                    finalBitListInvokers = stateRouter.route(finalBitListInvokers, routerCache, url, invocation);
                }
            }
            finalInvokers = new ArrayList<>(finalBitListInvokers.size());

            finalInvokers.addAll(finalBitListInvokers);
        }

        if (finalInvokers == null) {
            finalInvokers = new ArrayList<>(invokers);
        }

        for (Router router : routers) {
            finalInvokers = router.route(finalInvokers, url, invocation);
        }
        return finalInvokers;
    }

    // 设置调用者列表，并通知路由器链
    public void setInvokers(List<Invoker<T>> invokers) {
        this.invokers = (invokers == null ? Collections.emptyList() : invokers);
        stateRouters.forEach(router -> router.notify(this.invokers));
        routers.forEach(router -> router.notify(this.invokers));
        loop(true);
    }

    // 构建异步地址缓存
    private void buildCache(boolean notify) {
        if (CollectionUtils.isEmpty(invokers)) {
            return;
        }
        AddrCache<T> origin = cache.get();
        List<Invoker<T>> copyInvokers = new ArrayList<>(this.invokers);
        AddrCache<T> newCache = new AddrCache<T>();
        Map<String, RouterCache<T>> routerCacheMap = new HashMap<>((int) (stateRouters.size() / 0.75f) + 1);
        newCache.setInvokers(invokers);
        for (StateRouter stateRouter : stateRouters) {
            try {
                RouterCache routerCache = poolRouter(stateRouter, origin, copyInvokers, notify);
                // 文件缓存
                routerCacheMap.put(stateRouter.getName(), routerCache);
            } catch (Throwable t) {
                logger.error("Failed to pool router: " + stateRouter.getUrl() + ", cause: " + t.getMessage(), t);
                return;
            }
        }

        newCache.setCache(routerCacheMap);
        this.cache.set(newCache);
    }

    // 为每个状态路由器缓存地址列表
    private RouterCache poolRouter(StateRouter router, AddrCache<T> origin, List<Invoker<T>> invokers, boolean notify) {
        String routerName = router.getName();
        RouterCache routerCache;
        if (isCacheMiss(origin, routerName) || router.shouldRePool() || notify) {
            return router.pool(invokers);
        } else {
            routerCache = origin.getCache().get(routerName);
        }
        if (routerCache == null) {
            return new RouterCache();
        }
        return routerCache;
    }

    // 判断缓存是否缺失
    private boolean isCacheMiss(AddrCache<T> cache, String routerName) {
        return cache == null || cache.getCache() == null || cache.getInvokers() == null || cache.getCache().get(
            routerName)
            == null;
    }

    // 构建异步地址缓存
    public void loop(boolean notify) {
        if (firstBuildCache.compareAndSet(true,false)) {
            buildCache(notify);
        }

        try {
            if (notify) {
                if (loopPermitNotify.tryAcquire()) {
                    loopPool.submit(new NotifyLoopRunnable(true, loopPermitNotify));
                }
            } else {
                if (loopPermit.tryAcquire()) {
                    loopPool.submit(new NotifyLoopRunnable(false, loopPermit));
                }
            }
        } catch (RejectedExecutionException e) {
            if (loopPool.isShutdown()){
                logger.warn("loopPool executor service is shutdown, ignoring notify loop");
                return;
            }
            throw e;
        }
    }

    // 通知循环任务
    class NotifyLoopRunnable implements Runnable {

        private final boolean notify;
        private final Semaphore loopPermit;

        public NotifyLoopRunnable(boolean notify, Semaphore loopPermit) {
            this.notify = notify;
            this.loopPermit = loopPermit;
        }

        @Override
        public void run() {
            buildCache(notify);
            loopPermit.release();
        }
    }

    // 销毁路由器链
    public void destroy() {
        invokers = Collections.emptyList();
        for (Router router : routers) {
            try {
                router.stop();
            } catch (Exception e) {
                logger.error("Error trying to stop router " + router.getClass(), e);
            }
        }
        routers = Collections.emptyList();
        builtinRouters = Collections.emptyList();

        for (StateRouter router : stateRouters) {
            try {
                router.stop();
            } catch (Exception e) {
                logger.error("Error trying to stop stateRouter " + router.getClass(), e);
            }
        }
        stateRouters = Collections.emptyList();
        builtinStateRouters = Collections.emptyList();
    }
}

