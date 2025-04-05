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
package org.apache.dubbo.common.beans.support;

import org.apache.dubbo.common.beans.factory.ScopeBeanFactory;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelAccessor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface to create instance for specify type, using both in {@link ExtensionLoader} and {@link ScopeBeanFactory}.
 */
public class InstantiationStrategy {

    // 是否支持带有参数的构造函数。
    private boolean supportConstructorWithArguments;
    // 用于访问作用域模型的工具类。
    private ScopeModelAccessor scopeModelAccessor;

    // 默认构造函数，调用另一个构造函数初始化。
    public InstantiationStrategy() {
        this(null);
    }

    // 构造函数，初始化InstantiationStrategy。
    public InstantiationStrategy(ScopeModelAccessor scopeModelAccessor) {
        // 设置作用域模型访问器。
        this.scopeModelAccessor = scopeModelAccessor;
        // 根据作用域模型访问器是否为空来决定是否支持带有参数的构造函数。
        this.supportConstructorWithArguments = (this.scopeModelAccessor != null);
    }

    // 实例化指定类型的对象。
    public <T> T instantiate(Class<T> type) throws ReflectiveOperationException {

        // 1. 尝试使用默认构造函数创建实例。
        try {
            return type.getConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            // 如果没有默认构造函数且不支持带参数的构造函数，则抛出异常。
            if (!supportConstructorWithArguments) {
                throw new IllegalArgumentException("Default constructor was not found for type: " + type.getName());
            }
        }

        // 2. 查找匹配的构造函数。
        List<Constructor> matchedConstructors = new ArrayList<>();
        Constructor<?>[] declaredConstructors = type.getConstructors();
        for (Constructor<?> constructor : declaredConstructors) {
            // 检查构造函数是否匹配。
            if (isMatched(constructor)) {
                matchedConstructors.add(constructor);
            }
        }
        // 如果找到多个匹配的构造函数，则抛出异常。
        if (matchedConstructors.size() > 1) {
            throw new IllegalArgumentException("Expect only one but found " +
                matchedConstructors.size() + " matched constructors for type: " + type.getName() +
                ", matched constructors: " + matchedConstructors);
        } else if (matchedConstructors.size() == 0) {
            // 如果没有找到匹配的构造函数，则抛出异常。
            throw new IllegalArgumentException("None matched constructor was found for type: " + type.getName());
        }

        // 使用匹配的构造函数创建实例。
        Constructor constructor = matchedConstructors.get(0);
        Class[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            // 获取构造函数参数的值。
            args[i] = getArgumentValueForType(parameterTypes[i]);
        }
        return (T) constructor.newInstance(args);
    }

    // 检查构造函数是否匹配。
    private boolean isMatched(Constructor<?> constructor) {
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            // 检查每个参数类型是否是支持的类型。
            if (!isSupportedConstructorParameterType(parameterType)) {
                return false;
            }
        }
        return true;
    }

    // 检查构造函数参数类型是否是支持的类型。
    private boolean isSupportedConstructorParameterType(Class<?> parameterType) {
        // 支持的类型必须是ScopeModel或其子类。
        return ScopeModel.class.isAssignableFrom(parameterType);
    }

    // 根据参数类型获取参数值。
    private Object getArgumentValueForType(Class parameterType) {
        // 获取作用域模型的值。
        if (scopeModelAccessor != null) {
            if (parameterType == ScopeModel.class) {
                return scopeModelAccessor.getScopeModel();
            } else if (parameterType == FrameworkModel.class) {
                return scopeModelAccessor.getFrameworkModel();
            } else if (parameterType == ApplicationModel.class) {
                return scopeModelAccessor.getApplicationModel();
            } else if (parameterType == ModuleModel.class) {
                return scopeModelAccessor.getModuleModel();
            }
        }
        return null;
    }

}
