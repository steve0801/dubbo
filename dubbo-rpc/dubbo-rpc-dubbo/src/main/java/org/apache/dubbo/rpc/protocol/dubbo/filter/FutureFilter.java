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
package org.apache.dubbo.rpc.protocol.dubbo.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;
import org.apache.dubbo.rpc.model.AsyncMethodInfo;
import org.apache.dubbo.rpc.model.ConsumerModel;
import org.apache.dubbo.rpc.model.ServiceModel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.apache.dubbo.common.constants.CommonConstants.$INVOKE;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.ASYNC_METHOD_INFO;

/**
 * EventFilter
 */
/**
 * FutureFilter 类实现了 ClusterFilter 和 ClusterFilter.Listener 接口，用于处理异步方法调用的回调逻辑。
 * 该过滤器在消费者端激活，可在方法调用前后执行回调操作。
 */
@Activate(group = CommonConstants.CONSUMER)
public class FutureFilter implements ClusterFilter, ClusterFilter.Listener {

    // 定义日志记录器，用于记录该类的日志信息
    protected static final Logger logger = LoggerFactory.getLogger(FutureFilter.class);

    /**
     * 调用方法时触发，在实际调用方法前执行回调逻辑。
     *
     * @param invoker    调用器，包含服务调用的相关信息
     * @param invocation 调用信息，包含方法名、参数等
     * @return 调用结果
     * @throws RpcException 远程调用异常
     */
    @Override
    public Result invoke(final Invoker<?> invoker, final Invocation invocation) throws RpcException {
        // 触发调用前的回调
        fireInvokeCallback(invoker, invocation);
        // 在调用前配置是否有返回值，以帮助调用器判断是否需要返回 Future
        return invoker.invoke(invocation);
    }

    /**
     * 当调用成功返回结果时触发，根据结果是否有异常执行不同的回调逻辑。
     *
     * @param result     调用结果
     * @param invoker    调用器
     * @param invocation 调用信息
     */
    @Override
    public void onResponse(Result result, Invoker<?> invoker, Invocation invocation) {
        // 检查结果是否包含异常
        if (result.hasException()) {
            // 触发异常回调
            fireThrowCallback(invoker, invocation, result.getException());
        } else {
            // 触发返回值回调
            fireReturnCallback(invoker, invocation, result.getValue());
        }
    }

    /**
     * 当调用过程中出现异常时触发，执行异常回调逻辑。
     *
     * @param t          异常对象
     * @param invoker    调用器
     * @param invocation 调用信息
     */
    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        // 触发异常回调
        fireThrowCallback(invoker, invocation, t);
    }

    /**
     * 触发调用前的回调逻辑。
     *
     * @param invoker    调用器
     * @param invocation 调用信息
     */
    private void fireInvokeCallback(final Invoker<?> invoker, final Invocation invocation) {
        // 获取异步方法信息
        final AsyncMethodInfo asyncMethodInfo = getAsyncMethodInfo(invoker, invocation);
        // 如果异步方法信息为空，则直接返回
        if (asyncMethodInfo == null) {
            return;
        }
        // 获取 oninvoke 方法
        final Method onInvokeMethod = asyncMethodInfo.getOninvokeMethod();
        // 获取 oninvoke 实例
        final Object onInvokeInst = asyncMethodInfo.getOninvokeInstance();

        // 如果 oninvoke 方法和实例都为空，则直接返回
        if (onInvokeMethod == null && onInvokeInst == null) {
            return;
        }
        // 如果 oninvoke 方法或实例有一个为空，则抛出异常
        if (onInvokeMethod == null || onInvokeInst == null) {
            throw new IllegalStateException("service:" + invoker.getUrl().getServiceKey() + " has a oninvoke callback config , but no such " + (onInvokeMethod == null ? "method" : "instance") + " found. url:" + invoker.getUrl());
        }
        // 如果 oninvoke 方法不可访问，则设置为可访问
        if (!onInvokeMethod.isAccessible()) {
            onInvokeMethod.setAccessible(true);
        }

        // 获取调用参数
        Object[] params = invocation.getArguments();
        try {
            // 调用 oninvoke 方法
            onInvokeMethod.invoke(onInvokeInst, params);
        } catch (InvocationTargetException e) {
            // 触发异常回调
            fireThrowCallback(invoker, invocation, e.getTargetException());
        } catch (Throwable e) {
            // 触发异常回调
            fireThrowCallback(invoker, invocation, e);
        }
    }

    /**
     * 触发调用成功返回结果时的回调逻辑。
     *
     * @param invoker    调用器
     * @param invocation 调用信息
     * @param result     调用结果
     */
    private void fireReturnCallback(final Invoker<?> invoker, final Invocation invocation, final Object result) {
        // 获取异步方法信息
        final AsyncMethodInfo asyncMethodInfo = getAsyncMethodInfo(invoker, invocation);
        // 如果异步方法信息为空，则直接返回
        if (asyncMethodInfo == null) {
            return;
        }

        // 获取 onreturn 方法
        final Method onReturnMethod = asyncMethodInfo.getOnreturnMethod();
        // 获取 onreturn 实例
        final Object onReturnInst = asyncMethodInfo.getOnreturnInstance();

        // 如果 onreturn 方法和实例都为空，则直接返回
        if (onReturnMethod == null && onReturnInst == null) {
            return;
        }

        // 如果 onreturn 方法或实例有一个为空，则抛出异常
        if (onReturnMethod == null || onReturnInst == null) {
            throw new IllegalStateException("service:" + invoker.getUrl().getServiceKey() + " has a onreturn callback config , but no such " + (onReturnMethod == null ? "method" : "instance") + " found. url:" + invoker.getUrl());
        }
        // 如果 onreturn 方法不可访问，则设置为可访问
        if (!onReturnMethod.isAccessible()) {
            onReturnMethod.setAccessible(true);
        }

        // 获取调用参数
        Object[] args = invocation.getArguments();
        Object[] params;
        // 获取 onreturn 方法的参数类型
        Class<?>[] rParaTypes = onReturnMethod.getParameterTypes();
        if (rParaTypes.length > 1) {
            if (rParaTypes.length == 2 && rParaTypes[1].isAssignableFrom(Object[].class)) {
                // 如果参数类型为两个，且第二个参数类型为 Object[]，则创建包含结果和参数的数组
                params = new Object[2];
                params[0] = result;
                params[1] = args;
            } else {
                // 否则，创建包含结果和所有参数的数组
                params = new Object[args.length + 1];
                params[0] = result;
                // 将参数复制到新数组中
                System.arraycopy(args, 0, params, 1, args.length);
            }
        } else {
            // 如果参数类型只有一个，则创建只包含结果的数组
            params = new Object[]{result};
        }
        try {
            // 调用 onreturn 方法
            onReturnMethod.invoke(onReturnInst, params);
        } catch (InvocationTargetException e) {
            // 触发异常回调
            fireThrowCallback(invoker, invocation, e.getTargetException());
        } catch (Throwable e) {
            // 触发异常回调
            fireThrowCallback(invoker, invocation, e);
        }
    }

    /**
     * 触发调用过程中出现异常时的回调逻辑。
     *
     * @param invoker    调用器
     * @param invocation 调用信息
     * @param exception  异常对象
     */
    private void fireThrowCallback(final Invoker<?> invoker, final Invocation invocation, final Throwable exception) {
        // 获取异步方法信息
        final AsyncMethodInfo asyncMethodInfo = getAsyncMethodInfo(invoker, invocation);
        // 如果异步方法信息为空，则直接返回
        if (asyncMethodInfo == null) {
            return;
        }

        // 获取 onthrow 方法
        final Method onthrowMethod = asyncMethodInfo.getOnthrowMethod();
        // 获取 onthrow 实例
        final Object onthrowInst = asyncMethodInfo.getOnthrowInstance();

        // 如果 onthrow 方法和实例都为空，则直接返回
        if (onthrowMethod == null && onthrowInst == null) {
            return;
        }
        // 如果 onthrow 方法或实例有一个为空，则抛出异常
        if (onthrowMethod == null || onthrowInst == null) {
            throw new IllegalStateException("service:" + invoker.getUrl().getServiceKey() + " has a onthrow callback config , but no such " + (onthrowMethod == null ? "method" : "instance") + " found. url:" + invoker.getUrl());
        }
        // 如果 onthrow 方法不可访问，则设置为可访问
        if (!onthrowMethod.isAccessible()) {
            onthrowMethod.setAccessible(true);
        }
        // 获取 onthrow 方法的参数类型
        Class<?>[] rParaTypes = onthrowMethod.getParameterTypes();
        // 检查异常类型是否匹配 onthrow 方法的第一个参数类型
        if (rParaTypes[0].isAssignableFrom(exception.getClass())) {
            try {
                // 获取调用参数
                Object[] args = invocation.getArguments();
                Object[] params;

                if (rParaTypes.length > 1) {
                    if (rParaTypes.length == 2 && rParaTypes[1].isAssignableFrom(Object[].class)) {
                        // 如果参数类型为两个，且第二个参数类型为 Object[]，则创建包含异常和参数的数组
                        params = new Object[2];
                        params[0] = exception;
                        params[1] = args;
                    } else {
                        // 否则，创建包含异常和所有参数的数组
                        params = new Object[args.length + 1];
                        params[0] = exception;
                        // 将参数复制到新数组中
                        System.arraycopy(args, 0, params, 1, args.length);
                    }
                } else {
                    // 如果参数类型只有一个，则创建只包含异常的数组
                    params = new Object[]{exception};
                }
                // 调用 onthrow 方法
                onthrowMethod.invoke(onthrowInst, params);
            } catch (Throwable e) {
                // 记录异常信息
                logger.error(invocation.getMethodName() + ".call back method invoke error . callback method :" + onthrowMethod + ", url:" + invoker.getUrl(), e);
            }
        } else {
            // 记录异常信息
            logger.error(invocation.getMethodName() + ".call back method invoke error . callback method :" + onthrowMethod + ", url:" + invoker.getUrl(), exception);
        }
    }

    /**
     * 获取异步方法信息。
     *
     * @param invoker    调用器
     * @param invocation 调用信息
     * @return 异步方法信息
     */
    private AsyncMethodInfo getAsyncMethodInfo(Invoker<?> invoker, Invocation invocation) {
        // 从调用信息中获取异步方法信息
        AsyncMethodInfo asyncMethodInfo = (AsyncMethodInfo) invocation.get(ASYNC_METHOD_INFO);
        if (asyncMethodInfo != null) {
            return asyncMethodInfo;
        }

        // 获取服务模型
        ServiceModel serviceModel = invocation.getServiceModel();
        // 如果服务模型不是消费者模型，则返回 null
        if (!(serviceModel instanceof ConsumerModel)) {
            return null;
        }

        // 获取方法名
        String methodName = invocation.getMethodName();
        // 如果方法名是 $invoke，则从参数中获取实际方法名
        if (methodName.equals($INVOKE)) {
            methodName = (String) invocation.getArguments()[0];
        }

        // 从消费者模型中获取异步方法信息
        return ((ConsumerModel) serviceModel).getAsyncInfo(methodName);
    }

}
