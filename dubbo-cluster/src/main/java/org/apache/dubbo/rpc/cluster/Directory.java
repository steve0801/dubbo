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

import org.apache.dubbo.common.Node;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Directory. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Directory_service">Directory Service</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory)
 */
// 目录接口，继承自Node接口
public interface Directory<T> extends Node {

    /**
     * get service type.
     *
     * @return service type.
     */
    // 获取服务类型
    Class<T> getInterface();

    /**
     * list invokers.
     *
     * @return invokers
     */
    // 根据调用信息列出可用的调用者
    List<Invoker<T>> list(Invocation invocation) throws RpcException;

    // 获取所有调用者
    List<Invoker<T>> getAllInvokers();

    // 获取消费者URL
    URL getConsumerUrl();

    // 判断是否已销毁
    boolean isDestroyed();

    // 默认方法，判断目录是否为空
    default boolean isEmpty() {
        return CollectionUtils.isEmpty(getAllInvokers());
    }

    // 默认方法，判断是否为服务发现
    default boolean isServiceDiscovery() {
        return false;
    }

    // 丢弃地址
    void discordAddresses();

    // 获取路由链
    RouterChain<T> getRouterChain();

    // 默认方法，判断是否接收到通知
    default boolean isNotificationReceived() {
        return false;
    }
}
