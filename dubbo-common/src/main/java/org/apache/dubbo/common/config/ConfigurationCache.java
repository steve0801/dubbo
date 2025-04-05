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
package org.apache.dubbo.common.config;

import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Properties Cache of Configuration {@link ConfigurationUtils#getCachedDynamicProperty(ScopeModel, String, String)}
 */
public class ConfigurationCache {
    // 定义一个并发哈希映射作为缓存
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 获取缓存值
     *
     * @param key 键
     * @param function 生成值的函数，不应返回 null
     * @return 值
     */
    public String computeIfAbsent(String key, Function<String, String> function) {
        // 从缓存中获取值
        String value = cache.get(key);
        if (value == null) {
            // 无锁操作，容忍重复应用，将返回先前的值
            cache.putIfAbsent(key, function.apply(key));
            // 再次从缓存中获取值
            value = cache.get(key);
        }
        // 返回最终的值
        return value;
    }
}
