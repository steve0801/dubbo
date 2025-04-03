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
package org.apache.dubbo.rpc.protocol.grpc.interceptors;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.RpcContext;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;

/**
 * Hand over context information from Dubbo to gRPC.
 */
/**
 * Hand over context information from Dubbo to gRPC.
 */
@Activate(group = {PROVIDER, CONSUMER})
public class RpcContextInterceptor implements ClientInterceptor, ServerInterceptor {

    // Dubbo上下文信息在gRPC头中的前缀
    private static final String DUBBO = "D-";

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        // 获取客户端上下文信息
        RpcContext rpcContext = RpcContext.getClientAttachment();
        // 获取所有附件信息
        Map<String, Object> attachments = new HashMap<>(rpcContext.getObjectAttachments());

        // 创建并返回一个包装的ClientCall
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 如果有附件信息，将其添加到gRPC头中
                if (!attachments.isEmpty()) {
                    for (Map.Entry<String, Object> entry : attachments.entrySet()) {
                        // 只处理字符串类型的值
                        if (entry.getValue() instanceof String) {
                            headers.put(Metadata.Key.of(DUBBO + entry.getKey(), ASCII_STRING_MARSHALLER), ((String) entry.getValue()));
                        }
                    }
                }
                super.start(responseListener, headers);
            }
        };
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        // 获取所有头信息
        Set<String> keys = headers.keys();
        Map<String, Object> attachments = new HashMap<>();
        // 过滤出所有Dubbo附件信息并保存到map中
        if (keys != null) {
            keys.stream().filter(k -> k.toUpperCase().startsWith(DUBBO)).forEach(k ->
                    attachments.put(k.substring(DUBBO.length()), headers.get(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER)))
            );
        }

        // 创建并返回一个包装的ServerCallListener
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(next.startCall(call, headers)) {
            @Override
            public void onHalfClose() {
                // 客户端完成所有消息发送，如果是非流式调用，设置附件信息
                if (call.getMethodDescriptor().getType().clientSendsOneMessage()) {
                    RpcContext.getServerAttachment().setObjectAttachments(attachments);
                }
                super.onHalfClose();
            }

            @Override
            public void onMessage(ReqT message) {
                // 服务器接收到客户端请求，如果是流式调用，设置附件信息
                if (!call.getMethodDescriptor().getType().clientSendsOneMessage()) {
                    RpcContext.getServerAttachment().setObjectAttachments(attachments);
                }
                super.onMessage(message);
            }
        };
    }
}

