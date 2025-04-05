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
package org.apache.dubbo.common;

import org.apache.dubbo.common.beans.factory.ScopeBeanFactory;
import org.apache.dubbo.common.config.ConfigurationCache;
import org.apache.dubbo.common.lang.ShutdownHookCallbacks;
import org.apache.dubbo.common.status.reporter.FrameworkStatusReportService;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ScopeModelInitializer;

// 定义一个实现了ScopeModelInitializer接口的类，用于初始化不同层级模型。
public class CommonScopeModelInitializer implements ScopeModelInitializer {
    // 实现initializeFrameworkModel方法。当前实现为空，没有具体逻辑。
    @Override
    public void initializeFrameworkModel(FrameworkModel frameworkModel) {

    }

    // 实现initializeApplicationModel方法，该方法负责应用级模型的初始化。
    @Override
    public void initializeApplicationModel(ApplicationModel applicationModel) {
        // 获取应用模型中的bean工厂实例。
        ScopeBeanFactory beanFactory = applicationModel.getBeanFactory();
        // 在bean工厂中注册ShutdownHookCallbacks类作为bean。
        beanFactory.registerBean(ShutdownHookCallbacks.class);
        // 在bean工厂中注册FrameworkStatusReportService类作为bean。
        beanFactory.registerBean(FrameworkStatusReportService.class);
        // 在bean工厂中注册一个新的ConfigurationCache实例作为bean。
        beanFactory.registerBean(new ConfigurationCache());
    }

    // 实现initializeModuleModel方法，该方法负责模块级模型的初始化。
    @Override
    public void initializeModuleModel(ModuleModel moduleModel) {
        // 获取模块模型中的bean工厂实例。
        ScopeBeanFactory beanFactory = moduleModel.getBeanFactory();
        // 在bean工厂中注册一个新的ConfigurationCache实例作为bean。
        beanFactory.registerBean(new ConfigurationCache());
    }
}
