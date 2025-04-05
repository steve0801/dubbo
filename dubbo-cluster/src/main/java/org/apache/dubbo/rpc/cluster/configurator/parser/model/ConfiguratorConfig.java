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
package org.apache.dubbo.rpc.cluster.configurator.parser.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
// 配置器配置类，用于表示配置规则的整体配置
public class ConfiguratorConfig {
    // 服务级别的配置作用域
    public static final String SCOPE_SERVICE = "service";
    // 应用级别的配置作用域
    public static final String SCOPE_APPLICATION = "application";

    // 配置版本
    private String configVersion;
    // 配置作用域
    private String scope;
    // 配置键
    private String key;
    // 是否启用
    private Boolean enabled = true;
    // 配置项列表
    private List<ConfigItem> configs;

    // 从Map中解析配置器配置
    @SuppressWarnings("unchecked")
    public static ConfiguratorConfig parseFromMap(Map<String, Object> map) {
        ConfiguratorConfig configuratorConfig = new ConfiguratorConfig();
        configuratorConfig.setConfigVersion((String) map.get("configVersion"));
        configuratorConfig.setScope((String) map.get("scope"));
        configuratorConfig.setKey((String) map.get("key"));

        Object enabled = map.get("enabled");
        if (enabled != null) {
            configuratorConfig.setEnabled(Boolean.parseBoolean(enabled.toString()));
        }

        Object configs = map.get("configs");
        if (configs != null && List.class.isAssignableFrom(configs.getClass())) {
            configuratorConfig.setConfigs(((List<Map<String, Object>>) configs).stream()
                    .map(ConfigItem::parseFromMap).collect(Collectors.toList()));
        }

        return configuratorConfig;
    }

    // 获取配置版本
    public String getConfigVersion() {
        return configVersion;
    }

    // 设置配置版本
    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    // 获取配置作用域
    public String getScope() {
        return scope;
    }

    // 设置配置作用域
    public void setScope(String scope) {
        this.scope = scope;
    }

    // 获取配置键
    public String getKey() {
        return key;
    }

    // 设置配置键
    public void setKey(String key) {
        this.key = key;
    }

    // 获取是否启用
    public Boolean getEnabled() {
        return enabled;
    }

    // 设置是否启用
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    // 获取配置项列表
    public List<ConfigItem> getConfigs() {
        return configs;
    }

    // 设置配置项列表
    public void setConfigs(List<ConfigItem> configs) {
        this.configs = configs;
    }
}
