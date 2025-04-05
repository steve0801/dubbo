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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ExtensionDirector is a scoped extension loader manager.
 *
 * <p></p>
 * <p>ExtensionDirector supports multiple levels, and the child can inherit the parent's extension instances. </p>
 * <p>The way to find and create an extension instance is similar to Java classloader.</p>
 */
// 扩展点目录类，负责管理扩展点加载器
public class ExtensionDirector implements ExtensionAccessor {

    // 扩展点加载器缓存Map，线程安全
    private final ConcurrentMap<Class<?>, ExtensionLoader<?>> extensionLoadersMap = new ConcurrentHashMap<>(64);

    // 父级扩展点目录
    private ExtensionDirector parent;

    // 当前目录的作用域
    private final ExtensionScope scope;

    // 扩展点后置处理器列表
    private List<ExtensionPostProcessor> extensionPostProcessors = new ArrayList<>();

    // 作用域模型
    private ScopeModel scopeModel;

    // 构造函数
    public ExtensionDirector(ExtensionDirector parent, ExtensionScope scope, ScopeModel scopeModel) {
        this.parent = parent;
        this.scope = scope;
        this.scopeModel = scopeModel;
    }

    // 添加扩展点后置处理器
    public void addExtensionPostProcessor(ExtensionPostProcessor processor) {
        if (!this.extensionPostProcessors.contains(processor)) {
            this.extensionPostProcessors.add(processor);
        }
    }

    // 获取所有扩展点后置处理器
    public List<ExtensionPostProcessor> getExtensionPostProcessors() {
        return extensionPostProcessors;
    }

    // 实现ExtensionAccessor接口，返回自身
    @Override
    public ExtensionDirector getExtensionDirector() {
        return this;
    }

    // 获取指定类型的扩展点加载器
    @Override
    public <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        // 参数校验
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

        // 1. 先从本地缓存查找
        ExtensionLoader<T> loader = (ExtensionLoader<T>) extensionLoadersMap.get(type);

        final SPI annotation = type.getAnnotation(SPI.class);
        ExtensionScope scope = annotation.scope();

        // 如果是SELF作用域，直接创建加载器
        if (loader == null && scope == ExtensionScope.SELF) {
            loader = createExtensionLoader0(type);
        }

        // 2. 从父级目录查找
        if (loader == null) {
            if (this.parent != null) {
                loader = this.parent.getExtensionLoader(type);
            }
        }

        // 3. 创建新的加载器
        if (loader == null) {
            loader = createExtensionLoader(type);
        }

        return loader;
    }

    // 创建扩展点加载器
    private <T> ExtensionLoader<T> createExtensionLoader(Class<T> type) {
        ExtensionLoader<T> loader = null;
        // 检查作用域是否匹配
        if (isScopeMatched(type)) {
            loader = createExtensionLoader0(type);
        }
        return loader;
    }

    // 实际创建扩展点加载器的方法
    private <T> ExtensionLoader<T> createExtensionLoader0(Class<T> type) {
        ExtensionLoader<T> loader;
        // 使用线程安全的方式创建并缓存加载器
        extensionLoadersMap.putIfAbsent(type, new ExtensionLoader<T>(type, this, scopeModel));
        loader = (ExtensionLoader<T>) extensionLoadersMap.get(type);
        return loader;
    }

    // 检查类型的作用域是否匹配当前目录
    private boolean isScopeMatched(Class<?> type) {
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        return defaultAnnotation.scope().equals(scope);
    }

    // 检查类型是否有SPI注解
    private static boolean withExtensionAnnotation(Class<?> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    // 获取父级目录
    public ExtensionDirector getParent() {
        return parent;
    }

    // 清除所有缓存的加载器
    public void removeAllCachedLoader() {
        // extensionLoadersMap.clear();
    }
}
