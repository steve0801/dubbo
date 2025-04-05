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

import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.concurrent.CompletableFuture;

/**
 */
// 部署器接口，定义了组件生命周期的基本操作
public interface Deployer<E extends ScopeModel> {

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

    /**
     * 检查组件是否处于待启动状态
     * @return 如果是待启动状态返回true
     */
    boolean isPending();

    /**
     * 检查组件是否正在运行(包括启动中和已启动)
     * @return 如果正在运行返回true
     */
    boolean isRunning();

    /**
     * 检查组件是否已启动
     * @return 如果已启动返回true
     * @see #start()
     * @see #isStarting()
     */
    boolean isStarted();

    /**
     * 检查组件是否正在启动
     * @return 如果正在启动返回true
     * @see #isStarted()
     */
    boolean isStarting();

    /**
     * 检查组件是否正在停止
     * @return 如果正在停止返回true
     * @see #isStopped()
     */
    boolean isStopping();

    /**
     * 检查组件是否已停止
     * @return 如果已停止返回true
     * @see #isStopped()
     */
    boolean isStopped();

    /**
     * 检查组件是否失败(启动或停止失败)
     * @return 如果失败返回true
     */
    boolean isFailed();

    /**
     * 获取当前部署状态
     * @return 当前部署状态
     */
    DeployState getState();

    /**
     * 添加部署监听器
     * @param listener 要添加的监听器
     */
    void addDeployListener(DeployListener<E> listener);

    /**
     * 移除部署监听器
     * @param listener 要移除的监听器
     */
    void removeDeployListener(DeployListener<E> listener);
}
