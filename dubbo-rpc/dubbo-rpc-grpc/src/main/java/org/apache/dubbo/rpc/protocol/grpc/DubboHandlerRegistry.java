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

import io.grpc.BindableService;
import io.grpc.HandlerRegistry;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
/**
 * 自定义的gRPC处理程序注册表，用于管理服务和方法的注册与查找
 */
public class DubboHandlerRegistry extends HandlerRegistry {

    // 存储服务定义的映射，键为服务的键，值为服务定义
    private final Map<String, ServerServiceDefinition> services = new ConcurrentHashMap<>();
    // 存储方法定义的映射，键为方法的全名，值为方法定义
    private final Map<String, ServerMethodDefinition<?, ?>> methods = new ConcurrentHashMap<>();

    /**
     * 构造函数，初始化DubboHandlerRegistry实例
     */
    public DubboHandlerRegistry() {
    }

    /**
     * 返回此注册表中所有的服务定义
     * @return 服务定义的不可修改列表
     */
    @Override
    public List<ServerServiceDefinition> getServices() {
        // 返回服务定义的不可修改列表
        return Collections.unmodifiableList(new ArrayList<>(services.values()));
    }

    /**
     * 根据方法名和权限信息查找对应的方法定义
     * @param methodName 要查找的方法名
     * @param authority 权限信息，可为空
     * @return 找到的方法定义，若未找到则返回null
     */
    @Nullable
    @Override
    public ServerMethodDefinition<?, ?> lookupMethod(String methodName, @Nullable String authority) {
        // TODO (carl-mastrangelo): 支持权限头信息
        // 根据方法名从方法映射中查找方法定义
        return methods.get(methodName);
    }

    /**
     * 向注册表中添加一个可绑定的服务
     * @param bindableService 可绑定的服务实例
     * @param key 服务的键
     */
    void addService(BindableService bindableService, String key) {
        // 绑定服务并获取服务定义
        ServerServiceDefinition service = bindableService.bindService();
        // 将服务定义存储到服务映射中
        services.put(key, service);
        // 遍历服务中的所有方法
        for (ServerMethodDefinition<?, ?> method : service.getMethods()) {
            // 将方法定义存储到方法映射中，键为方法的全名
            methods.put(method.getMethodDescriptor().getFullMethodName(), method);
        }
    }

    /**
     * 从注册表中移除指定键的服务
     * @param serviceKey 要移除的服务的键
     */
    void removeService(String serviceKey) {
        // 从服务映射中移除指定键的服务定义
        ServerServiceDefinition service = services.remove(serviceKey);
        // 如果服务定义存在
        if (null != service) {
            // 遍历服务中的所有方法
            for (ServerMethodDefinition<?, ?> method : service.getMethods()) {
                // 从方法映射中移除对应的方法定义
                methods.remove(method.getMethodDescriptor().getFullMethodName(), method);
            }
        }
    }
}
