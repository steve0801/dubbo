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

import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.Map;
import java.util.Properties;

/**
 * Configuration from system properties and dubbo.properties
 */
public class PropertiesConfiguration implements Configuration {

    // 存储配置属性的Properties对象
    private Properties properties;
    // 作用域模型对象
    private final ScopeModel scopeModel;

    // 构造函数，传入ScopeModel对象
    public PropertiesConfiguration(ScopeModel scopeModel) {
        this.scopeModel = scopeModel;
        refresh();
    }

    // 刷新配置属性
    public void refresh() {
        // 通过ConfigUtils获取Properties配置
        properties = ConfigUtils.getProperties(scopeModel.getClassLoaders());
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
