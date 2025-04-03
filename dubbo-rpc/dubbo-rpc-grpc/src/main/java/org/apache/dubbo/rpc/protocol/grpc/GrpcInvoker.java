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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.AbstractInvoker;

import io.grpc.Status;
import io.grpc.StatusException;

import java.util.concurrent.locks.ReentrantLock;

/**
 * GrpcInvoker 类用于实现基于 gRPC 的服务调用者，继承自 AbstractInvoker
 *
 * @param <T> 服务接口类型
 */
public class GrpcInvoker<T> extends AbstractInvoker<T> {
    // 用于确保销毁操作的线程安全
    private final ReentrantLock destroyLock = new ReentrantLock();

    // 目标调用者
    private final Invoker<T> target;
    // gRPC 通道
    private ReferenceCountManagedChannel channel;

//    private static List<Exception> grpcExceptions = new ArrayList<>();
//    static {
//        grpcExceptions.add();
//    }

    /**
     * 构造函数，初始化 GrpcInvoker 实例
     *
     * @param type    服务接口类型
     * @param url     服务的 URL 信息
     * @param target  目标调用者
     * @param channel gRPC 通道
     */
    public GrpcInvoker(Class<T> type, URL url, Invoker<T> target, ReferenceCountManagedChannel channel) {
        // 调用父类构造函数
        super(type, url);
        this.target = target;
        this.channel = channel;
    }

    /**
     * 执行服务调用的核心方法
     *
     * @param invocation 调用信息
     * @return 调用结果
     * @throws Throwable 调用过程中可能抛出的异常
     */
    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        try {
            // 调用目标服务
            Result result = target.invoke(invocation);
            // FIXME result is an AsyncRpcResult instance.
            // 获取调用结果中的异常信息
            Throwable e = result.getException();
            if (e != null) {
                // 如果存在异常，将其转换为 RpcException 并抛出
                throw getRpcException(getInterface(), getUrl(), invocation, e);
            }
            return result;
        } catch (RpcException e) {
            if (e.getCode() == RpcException.UNKNOWN_EXCEPTION) {
                // 如果是未知异常，根据异常原因设置错误码
                e.setCode(getErrorCode(e.getCause()));
            }
            throw e;
        } catch (Throwable e) {
            // 将其他异常转换为 RpcException 并抛出
            throw getRpcException(getInterface(), getUrl(), invocation, e);
        }
    }

    /**
     * 判断当前调用者是否可用
     *
     * @return 如果可用返回 true，否则返回 false
     */
    @Override
    public boolean isAvailable() {
        // 调用父类的 isAvailable 方法，并检查通道是否关闭或终止
        return super.isAvailable() && !channel.isShutdown() && !channel.isTerminated();
    }

    /**
     * 判断当前调用者是否已销毁
     *
     * @return 如果已销毁返回 true，否则返回 false
     */
    @Override
    public boolean isDestroyed() {
        // 调用父类的 isDestroyed 方法，并检查通道是否关闭或终止
        return super.isDestroyed() || channel.isShutdown() || channel.isTerminated();
    }

    /**
     * 销毁当前调用者
     */
    @Override
    public void destroy() {
        if (!super.isDestroyed()) {
            // 双重检查，避免重复关闭
            destroyLock.lock();
            try {
                if (super.isDestroyed()) {
                    return;
                }
                // 调用父类的销毁方法
                super.destroy();
                // 关闭通道
                channel.shutdown();
            } finally {
                // 释放锁
                destroyLock.unlock();
            }
        }
    }

    /**
     * 将 Throwable 异常转换为 RpcException
     *
     * @param type       服务接口类型
     * @param url        服务的 URL 信息
     * @param invocation 调用信息
     * @param e          异常信息
     * @return 转换后的 RpcException
     */
    private RpcException getRpcException(Class<?> type, URL url, Invocation invocation, Throwable e) {
        // 创建 RpcException 实例
        RpcException re = new RpcException("Failed to invoke remote service: " + type + ", method: "
                + invocation.getMethodName() + ", cause: " + e.getMessage(), e);
        // 设置错误码
        re.setCode(getErrorCode(e));
        return re;
    }

    /**
     * 将 gRPC 异常转换为等效的 Dubbo 异常错误码
     *
     * @param e 待处理的异常对象
     * @return 对应的 Dubbo 异常错误码
     */
    private int getErrorCode(Throwable e) {
        // 检查异常是否为 gRPC 的 StatusException
        if (e instanceof StatusException) {
            // 将异常转换为 StatusException 类型
            StatusException statusException = (StatusException) e;
            // 获取 StatusException 的状态信息
            Status status = statusException.getStatus();
            // 检查状态码是否为 DEADLINE_EXCEEDED
            if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                // 如果是，返回 Dubbo 的超时异常错误码
                return RpcException.TIMEOUT_EXCEPTION;
            }
        }
        // 如果不是特定的 gRPC 异常，返回未知异常错误码
        return RpcException.UNKNOWN_EXCEPTION;
    }
}
