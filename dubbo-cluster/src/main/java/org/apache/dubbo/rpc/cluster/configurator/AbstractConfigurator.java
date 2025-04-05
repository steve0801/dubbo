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
package org.apache.dubbo.rpc.cluster.configurator;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.cluster.Configurator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACES;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.COMPATIBLE_CONFIG_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.CONFIG_VERSION_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.OVERRIDE_PROVIDERS_KEY;

/**
 * AbstractOverrideConfigurator
 */
// 抽象配置器类，实现Configurator接口
public abstract class AbstractConfigurator implements Configurator {

    // 用于标识以~开头的特殊参数
    private static final String TILDE = "~";

    // 配置器URL
    private final URL configuratorUrl;

    // 构造函数，初始化配置器URL
    public AbstractConfigurator(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("configurator url == null");
        }
        this.configuratorUrl = url;
    }

    // 获取配置器URL
    @Override
    public URL getUrl() {
        return configuratorUrl;
    }

    // 配置URL，根据配置器URL对传入的URL进行配置
    @Override
    public URL configure(URL url) {
        // 如果配置器URL未启用或无效，直接返回原URL
        if (!configuratorUrl.getParameter(ENABLED_KEY, true) || configuratorUrl.getHost() == null || url == null || url.getHost() == null) {
            return url;
        }
        /*
         * 从2.7.0版本开始新增的分支
         */
        String apiVersion = configuratorUrl.getParameter(CONFIG_VERSION_KEY);
        if (StringUtils.isNotEmpty(apiVersion)) {
            String currentSide = url.getSide();
            String configuratorSide = configuratorUrl.getSide();
            if (currentSide.equals(configuratorSide) && CONSUMER.equals(configuratorSide) && 0 == configuratorUrl.getPort()) {
                url = configureIfMatch(NetUtils.getLocalHost(), url);
            } else if (currentSide.equals(configuratorSide) && PROVIDER.equals(configuratorSide) &&
                    url.getPort() == configuratorUrl.getPort()) {
                url = configureIfMatch(url.getHost(), url);
            }
        }
        /*
         * 该else分支已弃用，仅用于保持与2.7.0之前版本的兼容性
         */
        else {
            url = configureDeprecated(url);
        }
        return url;
    }

    // 已弃用的配置方法，用于兼容旧版本
    @Deprecated
    private URL configureDeprecated(URL url) {
        // 如果配置器URL有端口，表示是提供者地址
        if (configuratorUrl.getPort() != 0) {
            if (url.getPort() == configuratorUrl.getPort()) {
                return configureIfMatch(url.getHost(), url);
            }
        } else {
            /*
             * 配置器URL没有端口，表示是消费者地址或0.0.0.0
             */
            if (url.getSide(PROVIDER).equals(CONSUMER)) {
                // NetUtils.getLocalHost是消费者注册到注册中心的IP地址
                return configureIfMatch(NetUtils.getLocalHost(), url);
            } else if (url.getSide(CONSUMER).equals(PROVIDER)) {
                // 对所有提供者生效，所以地址必须是0.0.0.0
                return configureIfMatch(ANYHOST_VALUE, url);
            }
        }
        return url;
    }

    // 如果匹配则配置URL
    private URL configureIfMatch(String host, URL url) {
        if (ANYHOST_VALUE.equals(configuratorUrl.getHost()) || host.equals(configuratorUrl.getHost())) {
            // TODO，待补充：支持通配符
            String providers = configuratorUrl.getParameter(OVERRIDE_PROVIDERS_KEY);
            if (StringUtils.isEmpty(providers) || providers.contains(url.getAddress()) || providers.contains(ANYHOST_VALUE)) {
                String configApplication = configuratorUrl.getApplication(configuratorUrl.getUsername());
                String currentApplication = url.getApplication(url.getUsername());
                if (configApplication == null || ANY_VALUE.equals(configApplication)
                        || configApplication.equals(currentApplication)) {
                    Set<String> conditionKeys = new HashSet<String>();
                    conditionKeys.add(CATEGORY_KEY);
                    conditionKeys.add(Constants.CHECK_KEY);
                    conditionKeys.add(DYNAMIC_KEY);
                    conditionKeys.add(ENABLED_KEY);
                    conditionKeys.add(GROUP_KEY);
                    conditionKeys.add(VERSION_KEY);
                    conditionKeys.add(APPLICATION_KEY);
                    conditionKeys.add(SIDE_KEY);
                    conditionKeys.add(CONFIG_VERSION_KEY);
                    conditionKeys.add(COMPATIBLE_CONFIG_KEY);
                    conditionKeys.add(INTERFACES);
                    for (Map.Entry<String, String> entry : configuratorUrl.getParameters().entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        boolean startWithTilde = startWithTilde(key);
                        if (startWithTilde || APPLICATION_KEY.equals(key) || SIDE_KEY.equals(key)) {
                            if (startWithTilde) {
                                conditionKeys.add(key);
                            }
                            if (value != null && !ANY_VALUE.equals(value)
                                    && !value.equals(url.getParameter(startWithTilde ? key.substring(1) : key))) {
                                return url;
                            }
                        }
                    }
                    return doConfigure(url, configuratorUrl.removeParameters(conditionKeys));
                }
            }
        }
        return url;
    }

    // 判断key是否以~开头
    private boolean startWithTilde(String key) {
        if (StringUtils.isNotEmpty(key) && key.startsWith(TILDE)) {
            return true;
        }
        return false;
    }

    // 抽象方法，由子类实现具体的配置逻辑
    protected abstract URL doConfigure(URL currentUrl, URL configUrl);

}

