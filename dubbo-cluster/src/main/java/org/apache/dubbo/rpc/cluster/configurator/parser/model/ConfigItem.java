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
// 配置项类，用于表示配置规则中的单个配置项
public class ConfigItem {
    // 配置项类型常量
    public static final String GENERAL_TYPE = "general";
    public static final String WEIGHT_TYPE = "weight";
    public static final String BALANCING_TYPE = "balancing";
    public static final String DISABLED_TYPE = "disabled";

    // 配置项类型
    private String type;
    // 是否启用
    private Boolean enabled;
    // 地址列表
    private List<String> addresses;
    // 提供者地址列表
    private List<String> providerAddresses;
    // 服务列表
    private List<String> services;
    // 应用列表
    private List<String> applications;
    // 参数字典
    private Map<String, String> parameters;
    // 配置项所属端（消费者或提供者）
    private String side;

    // 从Map中解析配置项
    @SuppressWarnings("unchecked")
    public static ConfigItem parseFromMap(Map<String, Object> map) {
        ConfigItem configItem = new ConfigItem();
        configItem.setType((String) map.get("type"));

        Object enabled = map.get("enabled");
        if (enabled != null) {
            configItem.setEnabled(Boolean.parseBoolean(enabled.toString()));
        }

        Object addresses = map.get("addresses");
        if (addresses != null && List.class.isAssignableFrom(addresses.getClass())) {
            configItem.setAddresses(((List<Object>) addresses).stream()
                    .map(String::valueOf).collect(Collectors.toList()));
        }

        Object providerAddresses = map.get("providerAddresses");
        if (providerAddresses != null && List.class.isAssignableFrom(providerAddresses.getClass())) {
            configItem.setProviderAddresses(((List<Object>) providerAddresses).stream()
                    .map(String::valueOf).collect(Collectors.toList()));
        }

        Object services = map.get("services");
        if (services != null && List.class.isAssignableFrom(services.getClass())) {
            configItem.setServices(((List<Object>) services).stream()
                    .map(String::valueOf).collect(Collectors.toList()));
        }

        Object applications = map.get("applications");
        if (applications != null && List.class.isAssignableFrom(applications.getClass())) {
            configItem.setApplications(((List<Object>) applications).stream()
                    .map(String::valueOf).collect(Collectors.toList()));
        }

        Object parameters = map.get("parameters");
        if (parameters != null && Map.class.isAssignableFrom(parameters.getClass())) {
            configItem.setParameters(((Map<String, Object>) parameters).entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString())));
        }

        configItem.setSide((String) map.get("side"));
        return configItem;
    }

    // 获取配置项类型
    public String getType() {
        return type;
    }

    // 设置配置项类型
    public void setType(String type) {
        this.type = type;
    }

    // 获取是否启用
    public Boolean getEnabled() {
        return enabled;
    }

    // 设置是否启用
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    // 获取地址列表
    public List<String> getAddresses() {
        return addresses;
    }

    // 设置地址列表
    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    // 获取服务列表
    public List<String> getServices() {
        return services;
    }

    // 设置服务列表
    public void setServices(List<String> services) {
        this.services = services;
    }

    // 获取应用列表
    public List<String> getApplications() {
        return applications;
    }

    // 设置应用列表
    public void setApplications(List<String> applications) {
        this.applications = applications;
    }

    // 获取提供者地址列表
    public List<String> getProviderAddresses() {
        return providerAddresses;
    }

    // 设置提供者地址列表
    public void setProviderAddresses(List<String> providerAddresses) {
        this.providerAddresses = providerAddresses;
    }

    // 获取参数字典
    public Map<String, String> getParameters() {
        return parameters;
    }

    // 设置参数字典
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    // 获取配置项所属端
    public String getSide() {
        return side;
    }

    // 设置配置项所属端
    public void setSide(String side) {
        this.side = side;
    }
}
