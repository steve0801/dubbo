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
package org.apache.dubbo.rpc.cluster.configurator.parser;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.cluster.configurator.parser.model.ConfigItem;
import org.apache.dubbo.rpc.cluster.configurator.parser.model.ConfiguratorConfig;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONValidator;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.RegistryConstants.APP_DYNAMIC_CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.rpc.cluster.Constants.OVERRIDE_PROVIDERS_KEY;


/**
 * 配置解析器
 */
public class ConfigParser {

    // 解析配置字符串，生成URL列表
    public static List<URL> parseConfigurators(String rawConfig) {
        // 判断原始配置是否为兼容的JsonArray格式，例如 [ "override://xxx", "override://xxx" ]
        if (isJsonArray(rawConfig)) {
            return parseJsonArray(rawConfig);
        }

        // 初始化URL列表
        List<URL> urls = new ArrayList<>();
        // 将原始配置字符串解析为ConfiguratorConfig对象
        ConfiguratorConfig configuratorConfig = parseObject(rawConfig);

        // 获取配置作用域
        String scope = configuratorConfig.getScope();
        // 获取配置项列表
        List<ConfigItem> items = configuratorConfig.getConfigs();

        // 根据配置作用域是应用级别还是默认的服务级别来处理不同的配置项
        if (ConfiguratorConfig.SCOPE_APPLICATION.equals(scope)) {
            items.forEach(item -> urls.addAll(appItemToUrls(item, configuratorConfig)));
        } else {
            // 默认为服务级别的配置
            items.forEach(item -> urls.addAll(serviceItemToUrls(item, configuratorConfig)));
        }
        // 返回最终的URL列表
        return urls;
    }

    // 如果原始配置是JsonArray格式，则直接解析成URL列表
    private static List<URL> parseJsonArray(String rawConfig) {
        // 初始化URL列表
        List<URL> urls = new ArrayList<>();
        // 使用FastJSON将原始配置字符串解析成String类型的列表
        List<String> list = JSON.parseArray(rawConfig, String.class);
        // 如果列表不为空，则逐个转换成URL对象并添加到URL列表中
        if (!CollectionUtils.isEmpty(list)) {
            list.forEach(u -> urls.add(URL.valueOf(u)));
        }
        // 返回URL列表
        return urls;
    }

    // 将原始配置字符串解析成ConfiguratorConfig对象
    private static ConfiguratorConfig parseObject(String rawConfig) {
        // 创建YAML解析器
        Yaml yaml = new Yaml(new SafeConstructor());
        // 将原始配置字符串加载成Map
        Map<String, Object> map = yaml.load(rawConfig);
        // 从Map中解析出ConfiguratorConfig对象
        return ConfiguratorConfig.parseFromMap(map);
    }

    // 为每个配置项生成URL列表（适用于服务级别）
    private static List<URL> serviceItemToUrls(ConfigItem item, ConfiguratorConfig config) {
        // 初始化URL列表
        List<URL> urls = new ArrayList<>();
        // 获取地址列表
        List<String> addresses = parseAddresses(item);

        // 对每个地址构建URL
        addresses.forEach(addr -> {
            // 初始化StringBuilder用于构建URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("override://").append(addr).append('/');

            // 添加服务相关信息
            urlBuilder.append(appendService(config.getKey()));
            // 添加参数字符串
            urlBuilder.append(toParameterString(item));

            // 处理启用状态
            parseEnabled(item, config, urlBuilder);

            // 添加配置版本
            urlBuilder.append("&configVersion=").append(config.getConfigVersion());

            // 如果存在应用程序列表，则为每个应用程序生成一个URL
            List<String> apps = item.getApplications();
            if (CollectionUtils.isNotEmpty(apps)) {
                apps.forEach(app -> {
                    // 为当前应用程序复制一个新的StringBuilder
                    StringBuilder tmpUrlBuilder = new StringBuilder(urlBuilder);
                    // 添加应用程序信息后转成URL对象并添加到列表中
                    urls.add(URL.valueOf(tmpUrlBuilder.append("&application=").append(app).toString()));
                });
            } else {
                // 直接将构建好的URL字符串转成URL对象并添加到列表中
                urls.add(URL.valueOf(urlBuilder.toString()));
            }
        });

        // 返回URL列表
        return urls;
    }

    // 为每个配置项生成URL列表（适用于应用级别）
    private static List<URL> appItemToUrls(ConfigItem item, ConfiguratorConfig config) {
        // 初始化URL列表
        List<URL> urls = new ArrayList<>();
        // 获取地址列表
        List<String> addresses = parseAddresses(item);
        // 对每个地址进行处理
        for (String addr : addresses) {
            // 初始化StringBuilder用于构建URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("override://").append(addr).append('/');

            // 获取服务列表，如果为空则使用通配符*
            List<String> services = item.getServices();
            if (services == null) {
                services = new ArrayList<>();
            }
            if (services.isEmpty()) {
                services.add("*");
            }

            // 对每个服务进行处理
            for (String s : services) {
                // 为当前服务复制一个新的StringBuilder
                StringBuilder tmpUrlBuilder = new StringBuilder(urlBuilder);
                // 添加服务相关信息
                tmpUrlBuilder.append(appendService(s));
                // 添加参数字符串
                tmpUrlBuilder.append(toParameterString(item));

                // 添加应用程序键
                tmpUrlBuilder.append("&application=").append(config.getKey());

                // 处理启用状态
                parseEnabled(item, config, tmpUrlBuilder);

                // 添加类别和配置版本
                tmpUrlBuilder.append("&category=").append(APP_DYNAMIC_CONFIGURATORS_CATEGORY);
                tmpUrlBuilder.append("&configVersion=").append(config.getConfigVersion());

                // 转成URL对象并添加到列表中
                urls.add(URL.valueOf(tmpUrlBuilder.toString()));
            }
        }
        // 返回URL列表
        return urls;
    }

    // 生成包含所有必要参数的查询字符串
    private static String toParameterString(ConfigItem item) {
        // 初始化StringBuilder用于构建参数字符串
        StringBuilder sb = new StringBuilder();
        sb.append("category=");
        sb.append(DYNAMIC_CONFIGURATORS_CATEGORY);

        // 如果侧边信息不为空，则添加到参数字符串中
        if (item.getSide() != null) {
            sb.append("&side=");
            sb.append(item.getSide());
        }

        // 获取参数映射
        Map<String, String> parameters = item.getParameters();
        // 参数映射不能为空
        if (CollectionUtils.isEmptyMap(parameters)) {
            throw new IllegalStateException("Invalid configurator rule, please specify at least one parameter " +
                "you want to change in the rule.");
        }

        // 将参数映射中的每个键值对添加到参数字符串中
        parameters.forEach((k, v) -> {
            sb.append('&');
            sb.append(k);
            sb.append('=');
            sb.append(v);
        });

        // 如果提供者地址列表不为空，则添加到参数字符串中
        if (CollectionUtils.isNotEmpty(item.getProviderAddresses())) {
            sb.append('&');
            sb.append(OVERRIDE_PROVIDERS_KEY);
            sb.append('=');
            sb.append(CollectionUtils.join(item.getProviderAddresses(), ","));
        }

        // 返回参数字符串
        return sb.toString();
    }

    // 根据服务键构建服务相关部分的查询字符串
    private static String appendService(String serviceKey) {
        // 初始化StringBuilder用于构建查询字符串
        StringBuilder sb = new StringBuilder();
        // 服务键不能为空
        if (StringUtils.isEmpty(serviceKey)) {
            throw new IllegalStateException("service field in configuration is null.");
        }

        // 从服务键中提取接口名
        String interfaceName = serviceKey;
        int i = interfaceName.indexOf('/');
        // 如果服务键包含分组信息，则将其添加到查询字符串中
        if (i > 0) {
            sb.append("group=");
            sb.append(interfaceName, 0, i);
            sb.append('&');

            interfaceName = interfaceName.substring(i + 1);
        }
        int j = interfaceName.indexOf(':');
        // 如果服务键包含版本信息，则将其添加到查询字符串中
        if (j > 0) {
            sb.append("version=");
            sb.append(interfaceName.substring(j + 1));
            sb.append('&');
            interfaceName = interfaceName.substring(0, j);
        }
        // 在最前面插入接口名
        sb.insert(0, interfaceName + "?");

        // 返回构建好的查询字符串
        return sb.toString();
    }

    // 处理配置项的启用状态，并添加到给定的StringBuilder中
    private static void parseEnabled(ConfigItem item, ConfiguratorConfig config, StringBuilder urlBuilder) {
        urlBuilder.append("&enabled=");
        // 根据配置项类型决定启用状态的来源
        if (item.getType() == null || ConfigItem.GENERAL_TYPE.equals(item.getType())) {
            urlBuilder.append(config.getEnabled());
        } else {
            urlBuilder.append(item.getEnabled());
        }
    }

    // 解析配置项中的地址列表
    private static List<String> parseAddresses(ConfigItem item) {
        // 获取地址列表，如果为空则初始化一个空列表
        List<String> addresses = item.getAddresses();
        if (addresses == null) {
            addresses = new ArrayList<>();
        }
        // 如果地址列表为空，则添加默认值
        if (addresses.isEmpty()) {
            addresses.add(ANYHOST_VALUE);
        }
        // 返回地址列表
        return addresses;
    }

    // 检查原始配置字符串是否为有效的JsonArray
    private static boolean isJsonArray(String rawConfig) {
        try {
            // 创建JSON验证器
            JSONValidator validator = JSONValidator.from(rawConfig);
            // 如果验证通过且类型为数组，则返回true
            return validator.validate() && validator.getType() == JSONValidator.Type.Array;
        } catch (Exception e) {
            // 忽略异常并返回false
        }
        // 默认返回false
        return false;
    }
}
