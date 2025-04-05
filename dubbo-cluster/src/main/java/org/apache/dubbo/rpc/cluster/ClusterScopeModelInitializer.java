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

import org.apache.dubbo.common.beans.factory.ScopeBeanFactory;
import org.apache.dubbo.rpc.cluster.merger.MergerFactory;
import org.apache.dubbo.rpc.cluster.support.ClusterUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ScopeModelInitializer;

// 集群作用域模型初始化器，实现了ScopeModelInitializer接口
public class ClusterScopeModelInitializer implements ScopeModelInitializer {
    // 初始化框架模型的方法，当前为空实现
    @Override
    public void initializeFrameworkModel(FrameworkModel frameworkModel) {

    }

    // 初始化应用模型的方法，注册MergerFactory和ClusterUtils到Bean工厂
    @Override
    public void initializeApplicationModel(ApplicationModel applicationModel) {
        ScopeBeanFactory beanFactory = applicationModel.getBeanFactory();
        beanFactory.registerBean(MergerFactory.class);
        beanFactory.registerBean(ClusterUtils.class);
    }

    // 初始化模块模型的方法，当前为空实现
    @Override
    public void initializeModuleModel(ModuleModel moduleModel) {

    }
}
