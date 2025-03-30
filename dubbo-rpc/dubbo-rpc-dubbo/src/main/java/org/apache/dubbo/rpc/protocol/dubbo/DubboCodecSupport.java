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
package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;

import static org.apache.dubbo.rpc.Constants.INVOCATION_KEY;
import static org.apache.dubbo.rpc.Constants.SERIALIZATION_ID_KEY;

/**
 * Dubbo 编解码器支持类，提供获取请求和响应序列化器的静态方法。
 */
public class DubboCodecSupport {

    /**
     * 根据 URL 和调用信息获取请求的序列化器。
     * 首先尝试从调用信息中获取序列化类型 ID，如果存在则通过该 ID 获取序列化器；
     * 若不存在，则从 URL 中获取序列化配置并加载对应的序列化器。
     *
     * @param url        包含配置信息的 URL 对象
     * @param invocation 调用信息对象
     * @return 序列化器实例
     */
    public static Serialization getRequestSerialization(URL url, Invocation invocation) {
        // 从调用信息中获取序列化类型对象
        Object serializationTypeObj = invocation.get(SERIALIZATION_ID_KEY);
        // 检查序列化类型对象是否存在
        if (serializationTypeObj != null) {
            // 若存在，则通过 ID 获取对应的序列化器
            return CodecSupport.getSerializationById((byte) serializationTypeObj);
        }
        // 若调用信息中未指定序列化类型，则从 URL 中获取配置的序列化器
        return url.getOrDefaultFrameworkModel().getExtensionLoader(Serialization.class).getExtension(
            url.getParameter(org.apache.dubbo.remoting.Constants.SERIALIZATION_KEY, Constants.DEFAULT_REMOTING_SERIALIZATION));
    }

    /**
     * 根据 URL 和应用响应信息获取响应的序列化器。
     * 首先尝试从响应的调用信息中获取序列化类型 ID，如果存在则通过该 ID 获取序列化器；
     * 若不存在，则从 URL 中获取序列化配置并加载对应的序列化器。
     *
     * @param url         包含配置信息的 URL 对象
     * @param appResponse 应用响应信息对象
     * @return 序列化器实例
     */
    public static Serialization getResponseSerialization(URL url, AppResponse appResponse) {
        // 从应用响应中获取调用信息对象
        Object invocationObj = appResponse.getAttribute(INVOCATION_KEY);
        // 检查调用信息对象是否存在
        if (invocationObj != null) {
            // 将调用信息对象转换为 Invocation 类型
            Invocation invocation = (Invocation) invocationObj;
            // 从调用信息中获取序列化类型对象
            Object serializationTypeObj = invocation.get(SERIALIZATION_ID_KEY);
            // 检查序列化类型对象是否存在
            if (serializationTypeObj != null) {
                // 若存在，则通过 ID 获取对应的序列化器
                return CodecSupport.getSerializationById((byte) serializationTypeObj);
            }
        }
        // 若响应的调用信息中未指定序列化类型，则从 URL 中获取配置的序列化器
        return url.getOrDefaultFrameworkModel().getExtensionLoader(Serialization.class).getExtension(
            url.getParameter(Constants.SERIALIZATION_KEY, Constants.DEFAULT_REMOTING_SERIALIZATION));
    }
}
