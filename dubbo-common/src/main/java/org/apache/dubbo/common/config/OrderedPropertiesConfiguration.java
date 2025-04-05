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

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.model.ModuleModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class OrderedPropertiesConfiguration implements Configuration{
    // 存储配置属性的Properties对象
    private Properties properties;
    // 模块模型对象
    private ModuleModel moduleModel;

    // 构造函数，传入ModuleModel对象
    public OrderedPropertiesConfiguration(ModuleModel moduleModel) {
        this.moduleModel = moduleModel;
        refresh();
    }

    // 刷新配置属性
    public void refresh() {
        // 初始化新的Properties对象
        properties = new Properties();
        // 获取OrderedPropertiesProvider的扩展加载器
        ExtensionLoader<OrderedPropertiesProvider> propertiesProviderExtensionLoader = moduleModel.getExtensionLoader(OrderedPropertiesProvider.class);
        // 获取所有支持的扩展名称
        Set<String> propertiesProviderNames = propertiesProviderExtensionLoader.getSupportedExtensions();
        // 如果没有扩展则直接返回
        if (propertiesProviderNames == null || propertiesProviderNames.isEmpty()) {
            return;
        }
        // 创建OrderedPropertiesProvider列表
        List<OrderedPropertiesProvider> orderedPropertiesProviders = new ArrayList<>();
        // 遍历所有扩展名称并获取对应的扩展实例
        for (String propertiesProviderName : propertiesProviderNames) {
            orderedPropertiesProviders.add(propertiesProviderExtensionLoader.getExtension(propertiesProviderName));
        }

        // 根据优先级降序排序Provider列表
        orderedPropertiesProviders.sort((OrderedPropertiesProvider a, OrderedPropertiesProvider b) -> {
            return b.priority() - a.priority();
        });


        // 按优先级顺序覆盖属性
        for (OrderedPropertiesProvider orderedPropertiesProvider :
            orderedPropertiesProviders) {
            properties.putAll(orderedPropertiesProvider.initProperties());
        }

    }

    // 获取指定key的属性值
    @Override
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    // 获取指定key的内部属性值
    @Override
    public Object getInternalProperty(String key) {
        return properties.getProperty(key);
    }

    // 设置属性键值对
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    // 移除指定key的属性
    public String remove(String key) {
        return (String) properties.remove(key);
    }

    // 设置Properties对象（已废弃）
    @Deprecated
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    // 获取所有属性Map
    public Map<String, String> getProperties() {
        return (Map) properties;
    }
}
