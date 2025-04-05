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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory configuration
 */
public class InmemoryConfiguration implements Configuration {

    // 配置名称标识
    private String name;

    // 使用LinkedHashMap存储配置项的键值对（保持插入顺序）
    private Map<String, String> store = new LinkedHashMap<>();

    // 无参构造方法
    public InmemoryConfiguration() {
    }

    // 带名称参数的构造方法
    public InmemoryConfiguration(String name) {
        this.name = name;
    }

    // 通过Map初始化配置的构造方法
    public InmemoryConfiguration(Map<String, String> properties) {
        this.setProperties(properties);
    }

    // 实现接口方法：根据key获取内部存储的配置值
    @Override
    public Object getInternalProperty(String key) {
        return store.get(key);
    }

    /**
     * Add one property into the store, the previous value will be replaced if the key exists
     */
    // 添加单个配置项（如果key已存在则覆盖）
    public void addProperty(String key, String value) {
        store.put(key, value);
    }

    /**
     * Add a set of properties into the store
     */
    // 批量添加配置项（合并到现有存储中）
    public void addProperties(Map<String, String> properties) {
        if (properties != null) {
            this.store.putAll(properties);
        }
    }

    /**
     * set store
     */
    // 完全替换现有配置存储
    public void setProperties(Map<String, String> properties) {
        if (properties != null) {
            this.store = properties;
        }
    }

    // 获取当前所有配置项的Map视图
    public Map<String, String> getProperties() {
        return store;
    }

}
