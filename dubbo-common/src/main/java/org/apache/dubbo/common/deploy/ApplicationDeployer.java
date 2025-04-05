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
package org.apache.dubbo.common.deploy;

import org.apache.dubbo.common.config.ReferenceCache;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.concurrent.CompletableFuture;

/**
 * initialize and start application instance
 */
// 应用部署器接口，继承自Deployer接口
public interface ApplicationDeployer extends Deployer<ApplicationModel> {

    /**
     * 初始化组件
     * @throws IllegalStateException 如果初始化失败抛出异常
     */
    void initialize() throws IllegalStateException;

    /**
     * 启动组件
     * @return 返回CompletableFuture以便异步处理
     * @throws IllegalStateException 如果启动失败抛出异常
     */
    CompletableFuture start() throws IllegalStateException;

    /**
     * 停止组件
     * @throws IllegalStateException 如果停止失败抛出异常
     */
    void stop() throws IllegalStateException;

    // 准备应用实例
    void prepareApplicationInstance();

    // 销毁组件
    void destroy();

    /**
     * 检查应用是否已初始化
     * @return 如果已初始化返回true，否则返回false
     */
    boolean isInitialized();

    // 获取应用模型
    ApplicationModel getApplicationModel();

    // 获取引用缓存
    ReferenceCache getReferenceCache();

    /**
     * 检查是否在后台启动
     * @return 如果是后台启动返回true，否则返回false
     */
    boolean isBackground();

    // 检查启动状态
    void checkStarting();

    /**
     * 检查是否已启动
     * @param checkerStartFuture 启动检查的Future对象
     */
    void checkStarted(CompletableFuture checkerStartFuture);
}
