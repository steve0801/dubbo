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

import org.apache.dubbo.common.utils.StringUtils;

public class PrefixedConfiguration implements Configuration {

    // 配置项前缀
    private String prefix;

    // 原始配置对象
    private Configuration origin;

    // 构造函数，传入原始配置和前缀
    public PrefixedConfiguration(Configuration origin, String prefix) {
        this.origin = origin;
        this.prefix = prefix;
    }

    // 获取内部属性值（带前缀处理）
    @Override
    public Object getInternalProperty(String key) {
        // 如果前缀为空，直接获取原始配置值
        if (StringUtils.isBlank(prefix)) {
            return origin.getInternalProperty(key);
        }

        // 尝试获取带前缀的配置值
        Object value = origin.getInternalProperty(prefix + "." + key);
        // 如果值不为空则返回
        if (!ConfigurationUtils.isEmptyValue(value)) {
            return value;
        }
        // 否则返回null
        return null;
    }

}
