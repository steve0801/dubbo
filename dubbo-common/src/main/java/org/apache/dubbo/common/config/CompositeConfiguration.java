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

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * 这是一个专门为Dubbo属性检索顺序定制的抽象类。
 */
public class CompositeConfiguration implements Configuration {
    // 初始化日志记录器
    private Logger logger = LoggerFactory.getLogger(CompositeConfiguration.class);

    /**
     * 保存所有配置对象的列表
     */
    // 定义一个LinkedList来存储所有的配置对象
    private List<Configuration> configList = new LinkedList<Configuration>();

    //FIXME, 考虑将configList改为SortedMap以替换这个布尔状态
    // 动态包含标志
    private boolean dynamicIncluded;

    // 无参构造函数
    public CompositeConfiguration() {
    }

    // 带参数的构造函数，接受多个配置对象
    public CompositeConfiguration(Configuration... configurations) {
        // 调用无参构造函数
        this();
        // 如果传入的配置对象不为空且长度大于0
        if (configurations != null && configurations.length > 0) {
            // 将配置对象流过滤后添加到configList中
            Arrays.stream(configurations).filter(config -> !configList.contains(config)).forEach(configList::add);
        }
    }

    // 设置动态包含标志
    public void setDynamicIncluded(boolean dynamicIncluded) {
        this.dynamicIncluded = dynamicIncluded;
    }

    // 获取动态包含标志
    //FIXME, 考虑将configList改为SortedMap以替换这个布尔状态
    public boolean isDynamicIncluded() {
        return dynamicIncluded;
    }

    // 添加一个配置对象
    public void addConfiguration(Configuration configuration) {
        // 如果configList中已经包含该配置对象，则直接返回
        if (configList.contains(configuration)) {
            return;
        }
        // 将配置对象添加到configList中
        this.configList.add(configuration);
    }

    // 将配置对象添加到configList的第一个位置
    public void addConfigurationFirst(Configuration configuration) {
        // 调用addConfiguration方法，指定位置为0
        this.addConfiguration(0, configuration);
    }

    // 在指定位置添加配置对象
    public void addConfiguration(int pos, Configuration configuration) {
        // 将配置对象添加到指定位置
        this.configList.add(pos, configuration);
    }

    // 从配置对象中获取内部属性
    @Override
    public Object getInternalProperty(String key) {
        // 遍历configList中的每个配置对象
        for (Configuration config : configList) {
            try {
                // 获取配置对象中的属性值
                Object value = config.getProperty(key);
                // 如果属性值不为空
                if (!ConfigurationUtils.isEmptyValue(value)) {
                    // 返回属性值
                    return value;
                }
            } catch (Exception e) {
                // 记录错误日志
                logger.error("尝试从" + config + "获取键" + key + "的值时发生错误，将继续尝试下一个配置对象。");
            }
        }
        // 如果没有找到属性值，返回null
        return null;
    }

}
