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
package org.apache.dubbo.common.beans;

import org.apache.dubbo.common.beans.factory.ScopeBeanFactory;
import org.apache.dubbo.common.extension.ExtensionInjector;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelAware;

/**
 * Inject scope bean to SPI extension instance
 */
public class ScopeBeanExtensionInjector implements ExtensionInjector, ScopeModelAware {
    // 保存ScopeModel实例的引用。
    private ScopeModel scopeModel;
    // 保存ScopeBeanFactory实例的引用，用于创建和获取bean。
    private ScopeBeanFactory beanFactory;

    // 实现setScopeModel方法，设置当前实例的scopeModel，并从给定的scopeModel中初始化beanFactory。
    @Override
    public void setScopeModel(ScopeModel scopeModel) {
        // 设置当前实例的scopeModel为传入的scopeModel。
        this.scopeModel = scopeModel;
        // 从设置的scopeModel中获取beanFactory并赋值给当前实例的beanFactory。
        this.beanFactory = scopeModel.getBeanFactory();
    }

    // 实现getInstance方法，根据指定的类型和名称从beanFactory中获取相应的bean实例。
    @Override
    public <T> T getInstance(Class<T> type, String name) {
        // 调用beanFactory的getBean方法，传入name和type参数以获取对应的bean实例。
        return beanFactory.getBean(name, type);
    }
}
