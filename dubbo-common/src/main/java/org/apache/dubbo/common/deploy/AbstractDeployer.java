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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.deploy.DeployState.FAILED;
import static org.apache.dubbo.common.deploy.DeployState.PENDING;
import static org.apache.dubbo.common.deploy.DeployState.STARTED;
import static org.apache.dubbo.common.deploy.DeployState.STARTING;
import static org.apache.dubbo.common.deploy.DeployState.STOPPED;
import static org.apache.dubbo.common.deploy.DeployState.STOPPING;

// 抽象部署器基类，实现了Deployer接口
public abstract class AbstractDeployer<E extends ScopeModel> implements Deployer<E> {

    // 当前部署状态，使用volatile保证可见性
    private volatile DeployState state = PENDING;

    // 初始化状态标记
    protected AtomicBoolean initialized = new AtomicBoolean(false);

    // 部署监听器列表
    private List<DeployListener<E>> listeners = new ArrayList<>();

    // 关联的作用域模型
    private E scopeModel;

    // 构造函数，传入作用域模型
    public AbstractDeployer(E scopeModel) {
        this.scopeModel = scopeModel;
    }

    // 检查是否处于待处理状态
    @Override
    public boolean isPending() {
        return state == PENDING;
    }

    // 检查是否处于运行中状态(包括启动中和已启动)
    @Override
    public boolean isRunning() {
        return state == STARTING || state == STARTED;
    }

    // 检查是否已启动
    @Override
    public boolean isStarted() {
        return state == STARTED;
    }

    // 检查是否正在启动
    @Override
    public boolean isStarting() {
        return state == STARTING;
    }

    // 检查是否正在停止
    @Override
    public boolean isStopping() {
        return state == STOPPING;
    }

    // 检查是否已停止
    @Override
    public boolean isStopped() {
        return state == STOPPED;
    }

    // 检查是否失败
    @Override
    public boolean isFailed() {
        return state == FAILED;
    }

    // 获取当前部署状态
    @Override
    public DeployState getState() {
        return state;
    }

    // 添加部署监听器
    @Override
    public void addDeployListener(DeployListener<E> listener) {
        listeners.add(listener);
    }

    // 移除部署监听器
    @Override
    public void removeDeployListener(DeployListener<E> listener) {
        listeners.remove(listener);
    }

    // 设置为待处理状态
    public void setPending() {
        this.state = PENDING;
    }

    // 设置为启动中状态，并通知所有监听器
    protected void setStarting() {
        this.state = STARTING;
        for (DeployListener<E> listener : listeners) {
            listener.onStarting(scopeModel);
        }
    }

    // 设置为已启动状态，并通知所有监听器
    protected void setStarted() {
        this.state = STARTED;
        for (DeployListener<E> listener : listeners) {
            listener.onStarted(scopeModel);
        }
    }

    // 设置为停止中状态，并通知所有监听器
    protected void setStopping() {
        this.state = STOPPING;
        for (DeployListener<E> listener : listeners) {
            listener.onStopping(scopeModel);
        }
    }

    // 设置为已停止状态，并通知所有监听器
    protected void setStopped() {
        this.state = STOPPED;
        for (DeployListener<E> listener : listeners) {
            listener.onStopped(scopeModel);
        }
    }

    // 设置为失败状态，并通知所有监听器
    protected void setFailed(Throwable cause) {
        this.state = FAILED;
        for (DeployListener<E> listener : listeners) {
            listener.onFailure(scopeModel, cause);
        }
    }

    // 检查是否已初始化
    public boolean isInitialized() {
        return initialized.get();
    }
}
