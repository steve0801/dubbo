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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * If you want to provide a router implementation based on design of v2.7.0, please extend from this abstract class.
 * For 2.6.x style router, please implement and use RouterFactory directly.
 */

// 可缓存的路由器工厂抽象类，实现了RouterFactory接口
public abstract class CacheableRouterFactory implements RouterFactory {
    // 用于缓存路由器的ConcurrentMap，key为服务键，value为路由器实例
    private ConcurrentMap<String, Router> routerMap = new ConcurrentHashMap<>();

    // 获取路由器的方法，如果缓存中不存在则创建新的路由器
    @Override
    public Router getRouter(URL url) {
        return routerMap.computeIfAbsent(url.getServiceKey(), k -> createRouter(url));
    }

    // 抽象方法，用于创建具体的路由器实例
    protected abstract Router createRouter(URL url);
}
