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
package org.apache.dubbo.common.beans.factory;

import org.apache.dubbo.common.beans.ScopeBeanException;
import org.apache.dubbo.common.beans.support.InstantiationStrategy;
import org.apache.dubbo.common.extension.ExtensionAccessor;
import org.apache.dubbo.common.extension.ExtensionAccessorAware;
import org.apache.dubbo.common.extension.ExtensionPostProcessor;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ScopeModelAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A bean factory for internal sharing.
 */
public class ScopeBeanFactory {

    // 保存父级ScopeBeanFactory的引用。
    private final ScopeBeanFactory parent;
    // 用于访问扩展点的工具类。
    private ExtensionAccessor extensionAccessor;
    // 存储扩展后处理器的列表。
    private List<ExtensionPostProcessor> extensionPostProcessors;
    // 用于生成bean名称ID的计数器映射。
    private Map<Class, AtomicInteger> beanNameIdCounterMap = new ConcurrentHashMap<>();
    // 存储已注册bean信息的同步列表。
    private List<BeanInfo> registeredBeanInfos = Collections.synchronizedList(new ArrayList<>());
    // 实例化策略对象，用于创建bean实例。
    private InstantiationStrategy instantiationStrategy;

    // 构造函数，初始化ScopeBeanFactory。
    public ScopeBeanFactory(ScopeBeanFactory parent, ExtensionAccessor extensionAccessor) {
        // 设置父级ScopeBeanFactory。
        this.parent = parent;
        // 设置扩展访问器。
        this.extensionAccessor = extensionAccessor;
        // 从扩展访问器中获取扩展后处理器列表。
        extensionPostProcessors = extensionAccessor.getExtensionDirector().getExtensionPostProcessors();
        // 初始化实例化策略。
        initInstantiationStrategy();
    }

    // 初始化实例化策略的方法。
    private void initInstantiationStrategy() {
        // 遍历所有扩展后处理器。
        for (ExtensionPostProcessor extensionPostProcessor : extensionPostProcessors) {
            // 如果扩展后处理器实现了ScopeModelAccessor接口，则设置实例化策略。
            if (extensionPostProcessor instanceof ScopeModelAccessor) {
                instantiationStrategy = new InstantiationStrategy((ScopeModelAccessor) extensionPostProcessor);
                break;
            }
        }
        // 如果未找到合适的实例化策略，则使用默认的实例化策略。
        if (instantiationStrategy == null) {
            instantiationStrategy = new InstantiationStrategy();
        }
    }

    // 注册一个无名的bean。
    public <T> T registerBean(Class<T> bean) throws ScopeBeanException {
        return this.getOrRegisterBean(null, bean);
    }

    // 注册一个指定名称的bean。
    public <T> T registerBean(String name, Class<T> clazz) throws ScopeBeanException {
        return getOrRegisterBean(name, clazz);
    }

    // 创建并注册一个新的bean。
    private <T> T createAndRegisterBean(String name, Class<T> clazz) {
        // 检查是否已经存在相同名称和类型的bean。
        T instance = getBean(name, clazz);
        if (instance != null) {
            throw new ScopeBeanException("already exists bean with same name and type, name=" + name + ", type=" + clazz.getName());
        }
        try {
            // 使用实例化策略创建bean实例。
            instance = instantiationStrategy.instantiate(clazz);
        } catch (Throwable e) {
            throw new ScopeBeanException("create bean instance failed, type=" + clazz.getName(), e);
        }
        // 注册创建好的bean。
        registerBean(name, instance);
        return instance;
    }

    // 注册一个无名的对象bean。
    public void registerBean(Object bean) {
        this.registerBean(null, bean);
    }

    // 注册一个指定名称的对象bean。
    public void registerBean(String name, Object bean) {
        // 避免重复注册相同的bean。
        if (containsBean(name, bean)) {
            return;
        }

        Class<?> beanClass = bean.getClass();
        // 如果名称为空，则生成一个名称。
        if (name == null) {
            name = beanClass.getName() + "#" + getNextId(beanClass);
        }
        // 初始化bean。
        initializeBean(name, bean);

        // 将bean信息添加到注册列表中。
        registeredBeanInfos.add(new BeanInfo(name, bean));
    }

    // 获取或注册一个无名的bean。
    public <T> T getOrRegisterBean(Class<T> type) {
        return getOrRegisterBean(null, type);
    }

    // 获取或注册一个指定名称的bean。
    public <T> T getOrRegisterBean(String name, Class<T> type) {
        T bean = getBean(name, type);
        if (bean == null) {
            // 通过类型加锁以确保线程安全。
            synchronized (type) {
                bean = getBean(name, type);
                if (bean == null) {
                    bean = createAndRegisterBean(name, type);
                }
            }
        }
        return bean;
    }

    // 根据映射函数获取或注册一个无名的bean。
    public <T> T getOrRegisterBean(Class<T> type, Function<? super Class<T>, ? extends T> mappingFunction) {
        return getOrRegisterBean(null, type, mappingFunction);
    }

    // 根据映射函数获取或注册一个指定名称的bean。
    public <T> T getOrRegisterBean(String name, Class<T> type, Function<? super Class<T>, ? extends T> mappingFunction) {
        T bean = getBean(name, type);
        if (bean == null) {
            // 通过类型加锁以确保线程安全。
            synchronized (type) {
                bean = getBean(name, type);
                if (bean == null) {
                    // 使用映射函数创建bean实例。
                    bean = mappingFunction.apply(type);
                    // 注册创建好的bean。
                    registerBean(name, bean);
                }
            }
        }
        return bean;
    }

    // 初始化一个bean，并返回该bean。
    public <T> T initializeBean(T bean) {
        this.initializeBean(null, bean);
        return bean;
    }

    // 初始化一个指定名称的bean。
    private void initializeBean(String name, Object bean) {
        try {
            // 如果bean实现了ExtensionAccessorAware接口，则设置扩展访问器。
            if (bean instanceof ExtensionAccessorAware) {
                ((ExtensionAccessorAware) bean).setExtensionAccessor(extensionAccessor);
            }
            // 对bean进行后处理。
            for (ExtensionPostProcessor processor : extensionPostProcessors) {
                processor.postProcessAfterInitialization(bean, name);
            }
        } catch (Exception e) {
            throw new ScopeBeanException("register bean failed! name=" + name + ", type=" + bean.getClass().getName(), e);
        }
    }

    // 检查是否已经包含指定名称的bean。
    private boolean containsBean(String name, Object bean) {
        for (BeanInfo beanInfo : registeredBeanInfos) {
            if (beanInfo.instance == bean &&
                (name == null || StringUtils.isEquals(name, beanInfo.name))) {
                return true;
            }
        }
        return false;
    }

    // 获取下一个可用的ID。
    private int getNextId(Class<?> beanClass) {
        return beanNameIdCounterMap.computeIfAbsent(beanClass, key -> new AtomicInteger()).incrementAndGet();
    }

    // 获取一个无名的bean。
    public <T> T getBean(Class<T> type) {
        return this.getBean(null, type);
    }

    // 获取一个指定名称的bean。
    public <T> T getBean(String name, Class<T> type) {
        T bean = getBeanInternal(name, type);
        // 如果当前ScopeBeanFactory中没有找到bean，则尝试从父级ScopeBeanFactory中获取。
        if (bean == null && parent != null) {
            return parent.getBean(name, type);
        }
        return bean;
    }

    // 在当前ScopeBeanFactory中查找bean。
    private <T> T getBeanInternal(String name, Class<T> type) {
        // 如果类型是Object，则无法通过类型过滤bean。
        if (type == Object.class) {
            return null;
        }
        List<BeanInfo> candidates = null;
        BeanInfo firstCandidate = null;
        for (BeanInfo beanInfo : registeredBeanInfos) {
            // 如果所需的bean类型与注册的bean类型匹配。
            if (type.isAssignableFrom(beanInfo.instance.getClass())) {
                if (StringUtils.isEquals(beanInfo.name, name)) {
                    return (T) beanInfo.instance;
                } else {
                    // 优化处理：如果只有一个匹配的bean，则直接返回。
                    if (firstCandidate == null) {
                        firstCandidate = beanInfo;
                    } else {
                        if (candidates == null) {
                            candidates = new ArrayList<>();
                            candidates.add(firstCandidate);
                        }
                        candidates.add(beanInfo);
                    }
                }
            }
        }

        // 如果名称不匹配且只有一个候选者。
        if (candidates != null) {
            if (candidates.size() == 1) {
                return (T) candidates.get(0).instance;
            } else if (candidates.size() > 1) {
                // 如果有多个候选者，则抛出异常。
                List<String> candidateBeanNames = candidates.stream().map(beanInfo -> beanInfo.name).collect(Collectors.toList());
                throw new ScopeBeanException("expected single matching bean but found " + candidates.size() + " candidates for type [" + type.getName() + "]: " + candidateBeanNames);
            }
        } else if (firstCandidate != null) {
            return (T) firstCandidate.instance;
        }
        return null;
    }

    // 内部类，用于存储bean的信息。
    static class BeanInfo {
        private String name;
        private Object instance;

        // 构造函数，初始化BeanInfo。
        public BeanInfo(String name, Object instance) {
            this.name = name;
            this.instance = instance;
        }
    }
}
