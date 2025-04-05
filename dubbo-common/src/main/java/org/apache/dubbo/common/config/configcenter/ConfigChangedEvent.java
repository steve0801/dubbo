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

import java.util.EventObject;
import java.util.Objects;

/**
 * An event raised when the config changed, immutable.
 *
 * @see ConfigChangeType
 */
public class ConfigChangedEvent extends EventObject {

    // 配置项的key
    private final String key;

    // 配置项所属的group
    private final String group;

    // 配置项的内容
    private final String content;

    // 配置变更类型
    private final ConfigChangeType changeType;

    // 构造函数，默认变更类型为MODIFIED
    public ConfigChangedEvent(String key, String group, String content) {
        this(key, group, content, ConfigChangeType.MODIFIED);
    }

    // 完整构造函数
    public ConfigChangedEvent(String key, String group, String content, ConfigChangeType changeType) {
        // 调用父类构造函数，使用key和group组合作为事件源
        super(key + "," + group);
        this.key = key;
        this.group = group;
        this.content = content;
        this.changeType = changeType;
    }

    // 获取配置项的key
    public String getKey() {
        return key;
    }

    // 获取配置项的group
    public String getGroup() {
        return group;
    }

    // 获取配置项的内容
    public String getContent() {
        return content;
    }

    // 获取配置变更类型
    public ConfigChangeType getChangeType() {
        return changeType;
    }

    // 重写toString方法，输出事件详情
    @Override
    public String toString() {
        return "ConfigChangedEvent{" +
                "key='" + key + '\'' +
                ", group='" + group + '\'' +
                ", content='" + content + '\'' +
                ", changeType=" + changeType +
                "} " + super.toString();
    }

    // 重写equals方法，比较两个事件是否相同
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConfigChangedEvent)) {
            return false;
        }
        ConfigChangedEvent that = (ConfigChangedEvent) o;
        return Objects.equals(getKey(), that.getKey()) &&
                Objects.equals(getGroup(), that.getGroup()) &&
                Objects.equals(getContent(), that.getContent()) &&
                getChangeType() == that.getChangeType();
    }

    // 重写hashCode方法
    @Override
    public int hashCode() {
        return Objects.hash(getKey(), getGroup(), getContent(), getChangeType());
    }
}
