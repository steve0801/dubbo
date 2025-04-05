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
package org.apache.dubbo.common.config.configcenter;

import org.apache.dubbo.common.URL;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;

/**
 * Abstract {@link DynamicConfigurationFactory} implementation with cache ability
 *
 * @see DynamicConfigurationFactory
 * @since 2.7.5
 */
public abstract class AbstractDynamicConfigurationFactory implements DynamicConfigurationFactory {

    // 存储动态配置实例的Map，使用ConcurrentHashMap保证线程安全
    private volatile Map<String, DynamicConfiguration> dynamicConfigurations = new ConcurrentHashMap<>();

    // 获取动态配置实例（线程安全）
    @Override
    public final DynamicConfiguration getDynamicConfiguration(URL url) {
        // 使用URL作为key，如果URL为空则使用默认key
        String key = url == null ? DEFAULT_KEY : url.toServiceString();
        // 如果Map中不存在则创建新的配置实例
        return dynamicConfigurations.computeIfAbsent(key, k -> createDynamicConfiguration(url));
    }

    // 抽象方法：创建具体的动态配置实例
    protected abstract DynamicConfiguration createDynamicConfiguration(URL url);
}
