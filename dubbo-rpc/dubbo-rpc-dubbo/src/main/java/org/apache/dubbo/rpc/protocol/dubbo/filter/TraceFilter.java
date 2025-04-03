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
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TraceFilter
 */
/**
 * TraceFilter 类实现了 Filter 接口，用于在服务提供者端对方法调用进行跟踪。
 * 该过滤器会记录方法调用的信息，并将其发送给指定的通道，以便进行调试和监控。
 */
@Activate(group = CommonConstants.PROVIDER)
public class TraceFilter implements Filter {

    // 定义日志记录器，用于记录该类的日志信息
    private static final Logger logger = LoggerFactory.getLogger(TraceFilter.class);

    // 定义跟踪最大次数的属性名
    private static final String TRACE_MAX = "trace.max";
    // 定义跟踪计数的属性名
    private static final String TRACE_COUNT = "trace.count";

    // 定义一个并发映射，用于存储每个方法的跟踪通道集合
    private static final ConcurrentMap<String, Set<Channel>> TRACERS = new ConcurrentHashMap<>();

    /**
     * 向指定的方法添加跟踪通道。
     *
     * @param type    方法所属的类
     * @param method  方法名
     * @param channel 要添加的通道
     * @param max     最大跟踪次数
     */
    public static void addTracer(Class<?> type, String method, Channel channel, int max) {
        // 设置通道的最大跟踪次数属性
        channel.setAttribute(TRACE_MAX, max);
        // 设置通道的跟踪计数属性，初始值为 0
        channel.setAttribute(TRACE_COUNT, new AtomicInteger());
        // 生成方法的唯一键
        String key = method != null && !method.isEmpty() ? type.getName() + "." + method : type.getName();
        // 获取该方法的通道集合，如果不存在则创建一个新的集合
        Set<Channel> channels = TRACERS.computeIfAbsent(key, k -> new ConcurrentHashSet<>());
        // 将通道添加到集合中
        channels.add(channel);
    }

    /**
     * 从指定的方法移除跟踪通道。
     *
     * @param type    方法所属的类
     * @param method  方法名
     * @param channel 要移除的通道
     */
    public static void removeTracer(Class<?> type, String method, Channel channel) {
        // 移除通道的最大跟踪次数属性
        channel.removeAttribute(TRACE_MAX);
        // 移除通道的跟踪计数属性
        channel.removeAttribute(TRACE_COUNT);
        // 生成方法的唯一键
        String key = method != null && !method.isEmpty() ? type.getName() + "." + method : type.getName();
        // 获取该方法的通道集合
        Set<Channel> channels = TRACERS.get(key);
        // 如果集合不为空，则移除指定的通道
        if (channels != null) {
            channels.remove(channel);
        }
    }

    /**
     * 拦截方法调用，记录调用信息并发送给跟踪通道。
     *
     * @param invoker    调用器，包含服务调用的相关信息
     * @param invocation 调用信息，包含方法名、参数等
     * @return 调用结果
     * @throws RpcException 远程调用异常
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 记录方法调用开始时间
        long start = System.currentTimeMillis();
        // 执行方法调用
        Result result = invoker.invoke(invocation);
        // 记录方法调用结束时间
        long end = System.currentTimeMillis();
        // 检查是否有跟踪通道
        if (!TRACERS.isEmpty()) {
            // 生成方法的唯一键
            String key = invoker.getInterface().getName() + "." + invocation.getMethodName();
            // 获取该方法的通道集合
            Set<Channel> channels = TRACERS.get(key);
            // 如果集合为空，则尝试获取类级别的通道集合
            if (channels == null || channels.isEmpty()) {
                key = invoker.getInterface().getName();
                channels = TRACERS.get(key);
            }
            // 如果通道集合不为空
            if (CollectionUtils.isNotEmpty(channels)) {
                // 遍历通道集合
                for (Channel channel : new ArrayList<>(channels)) {
                    // 检查通道是否连接
                    if (channel.isConnected()) {
                        try {
                            // 获取最大跟踪次数，默认为 1
                            int max = 1;
                            Integer m = (Integer) channel.getAttribute(TRACE_MAX);
                            if (m != null) {
                                max = m;
                            }
                            // 获取跟踪计数
                            AtomicInteger c = (AtomicInteger) channel.getAttribute(TRACE_COUNT);
                            if (c == null) {
                                c = new AtomicInteger();
                                channel.setAttribute(TRACE_COUNT, c);
                            }
                            // 获取当前跟踪计数并自增
                            int count = c.getAndIncrement();
                            // 如果当前跟踪计数小于最大跟踪次数
                            if (count < max) {
                                // 获取通道的提示信息
                                String prompt = channel.getUrl().getParameter(Constants.PROMPT_KEY, Constants.DEFAULT_PROMPT);
                                // 构建跟踪信息
                                String traceInfo = "\r\n" + RpcContext.getServiceContext().getRemoteAddress() + " -> "
                                        + invoker.getInterface().getName()
                                        + "." + invocation.getMethodName()
                                        + "(" + JSON.toJSONString(invocation.getArguments()) + ")" + " -> " + JSON.toJSONString(result.getValue())
                                        + "\r\nelapsed: " + (end - start) + " ms."
                                        + "\r\n\r\n" + prompt;
                                // 发送跟踪信息到通道
                                channel.send(traceInfo);
                            }
                            // 如果当前跟踪计数达到或超过最大跟踪次数减 1，则移除该通道
                            if (count >= max - 1) {
                                channels.remove(channel);
                            }
                        } catch (Throwable e) {
                            // 移除异常通道
                            channels.remove(channel);
                            // 记录异常信息
                            logger.warn(e.getMessage(), e);
                        }
                    } else {
                        // 移除未连接的通道
                        channels.remove(channel);
                    }
                }
            }
        }
        // 返回调用结果
        return result;
    }

}
