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
package org.apache.dubbo.common.config.configcenter.wrapper;

import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * support multiple config center, simply iterating each concrete config center.
 */
public class CompositeDynamicConfiguration implements DynamicConfiguration {

    // 组合配置的名称标识
    public static final String NAME = "COMPOSITE";

    // 存储多个动态配置的集合
    private Set<DynamicConfiguration> configurations = new HashSet<>();

    // 添加一个动态配置到组合中
    public void addConfiguration(DynamicConfiguration configuration) {
        if (configuration != null) {
            this.configurations.add(configuration);
        }
    }

    // 获取内部所有的动态配置集合
    public Set<DynamicConfiguration> getInnerConfigurations() {
        return configurations;
    }

    // 添加配置监听器到所有配置中
    @Override
    public void addListener(String key, String group, ConfigurationListener listener) {
        iterateListenerOperation(configuration -> configuration.addListener(key, group, listener));
    }

    // 从所有配置中移除配置监听器
    @Override
    public void removeListener(String key, String group, ConfigurationListener listener) {
        iterateListenerOperation(configuration -> configuration.removeListener(key, group, listener));
    }

    // 从组合配置中获取指定key和group的配置值
    @Override
    public String getConfig(String key, String group, long timeout) throws IllegalStateException {
        return (String) iterateConfigOperation(configuration -> configuration.getConfig(key, group, timeout));
    }

    // 从组合配置中获取指定key和group的属性值
    @Override
    public String getProperties(String key, String group, long timeout) throws IllegalStateException {
        return (String) iterateConfigOperation(configuration -> configuration.getProperties(key, group, timeout));
    }

    // 从组合配置中获取内部属性值
    @Override
    public Object getInternalProperty(String key) {
        return iterateConfigOperation(configuration -> configuration.getInternalProperty(key));
    }

    // 发布配置到所有配置中心
    @Override
    public boolean publishConfig(String key, String group, String content) throws UnsupportedOperationException {
        boolean publishedAll = true;
        for (DynamicConfiguration configuration : configurations) {
            if (!configuration.publishConfig(key, group, content)) {
                publishedAll = false;
            }
        }
        return publishedAll;
    }

    // 获取指定group下的所有配置key集合
    @Override
    @SuppressWarnings("unchecked")
    public SortedSet<String> getConfigKeys(String group) throws UnsupportedOperationException {
        return (SortedSet<String>) iterateConfigOperation(configuration -> configuration.getConfigKeys(group));
    }

    // 遍历执行监听器操作
    private void iterateListenerOperation(Consumer<DynamicConfiguration> consumer) {
        for (DynamicConfiguration configuration : configurations) {
            consumer.accept(configuration);
        }
    }

    // 遍历执行配置操作，返回第一个非空结果
    private Object iterateConfigOperation(Function<DynamicConfiguration, Object> func) {
        Object value = null;
        for (DynamicConfiguration configuration : configurations) {
            value = func.apply(configuration);
            if (value != null) {
                break;
            }
        }
        return value;
    }
}
