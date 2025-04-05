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

import org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory;
import org.apache.dubbo.common.extension.ExtensionAccessor;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_SERVER_SHUTDOWN_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_SECONDS_KEY;

/**
 * Utilities for manipulating configurations from different sources
 */
public class ConfigurationUtils {
    // 定义一个日志记录器
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationUtils.class);
    // 定义一个安全密钥列表
    private static final List<String> securityKey;

    // 静态初始化块
    static {
        // 创建一个链表存储安全密钥
        List<String> keys = new LinkedList<>();
        // 添加安全密钥
        keys.add("accesslog");
        keys.add("router");
        keys.add("rule");
        keys.add("runtime");
        keys.add("type");
        // 将链表转换为不可修改列表
        securityKey = Collections.unmodifiableList(keys);
    }

    /**
     * 用于从 JVM 获取属性
     *
     * @return
     */
    public static Configuration getSystemConfiguration(ScopeModel scopeModel) {
        // 从 ScopeModel 获取或默认获取 ApplicationModel 并返回系统配置
        return ScopeModelUtil.getOrDefaultApplicationModel(scopeModel).getModelEnvironment().getSystemConfiguration();
    }

    /**
     * 用于从操作系统环境获取属性
     *
     * @return
     */
    public static Configuration getEnvConfiguration(ScopeModel scopeModel) {
        // 从 ScopeModel 获取或默认获取 ApplicationModel 并返回环境配置
        return ScopeModelUtil.getOrDefaultApplicationModel(scopeModel).getModelEnvironment().getEnvironmentConfiguration();
    }

    /**
     * 用于获取复合属性值
     * <p>
     * 参见 {@link Environment#getConfiguration()}
     *
     * @return
     */
    public static Configuration getGlobalConfiguration(ScopeModel scopeModel) {
        // 从 ScopeModel 获取或默认获取 ApplicationModel 并返回全局配置
        return ScopeModelUtil.getOrDefaultApplicationModel(scopeModel).getModelEnvironment().getConfiguration();
    }

    // 用于获取动态全局配置
    public static Configuration getDynamicGlobalConfiguration(ScopeModel scopeModel) {
        // 从 ScopeModel 获取动态全局配置
        return scopeModel.getModelEnvironment().getDynamicGlobalConfiguration();
    }

    // FIXME

    /**
     * 服务器关闭等待超时时间（毫秒）
     * @return
     */
    @SuppressWarnings("deprecation")
    public static int getServerShutdownTimeout(ScopeModel scopeModel) {
        // 初始化超时时间为默认值
        int timeout = DEFAULT_SERVER_SHUTDOWN_TIMEOUT;
        // 获取全局配置
        Configuration configuration = getGlobalConfiguration(scopeModel);
        // 获取并修剪 SHUTDOWN_WAIT_KEY 的值
        String value = StringUtils.trim(configuration.getString(SHUTDOWN_WAIT_KEY));

        if (value != null && value.length() > 0) {
            try {
                // 尝试将值解析为整数
                timeout = Integer.parseInt(value);
            } catch (Exception e) {
                // 忽略异常
            }
        } else {
            // 获取并修剪 SHUTDOWN_WAIT_SECONDS_KEY 的值
            value = StringUtils.trim(configuration.getString(SHUTDOWN_WAIT_SECONDS_KEY));
            if (value != null && value.length() > 0) {
                try {
                    // 尝试将值解析为整数并转换为毫秒
                    timeout = Integer.parseInt(value) * 1000;
                } catch (Exception e) {
                    // 忽略异常
                }
            }
        }
        // 返回最终的超时时间
        return timeout;
    }

    // 用于获取缓存的动态属性
    public static String getCachedDynamicProperty(ScopeModel realScopeModel, String key, String defaultValue) {
        // 从 ScopeModel 获取或默认获取 ApplicationModel
        ScopeModel scopeModel = ScopeModelUtil.getOrDefaultApplicationModel(realScopeModel);
        // 从 BeanFactory 获取 ConfigurationCache 实例
        ConfigurationCache configurationCache = scopeModel.getBeanFactory().getBean(ConfigurationCache.class);
        // 计算并返回缓存中的值
        String value = configurationCache.computeIfAbsent(key, _k -> ConfigurationUtils.getDynamicProperty(scopeModel, _k, ""));
        // 如果值为空，则返回默认值
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    // 用于获取动态属性
    public static String getDynamicProperty(ScopeModel scopeModel, String property) {
        // 调用重载方法
        return getDynamicProperty(scopeModel, property, null);
    }

    // 用于获取动态属性
    public static String getDynamicProperty(ScopeModel scopeModel, String property, String defaultValue) {
        // 从动态全局配置中获取并修剪字符串值
        return StringUtils.trim(getDynamicGlobalConfiguration(scopeModel).getString(property, defaultValue));
    }

    // 用于获取属性
    public static String getProperty(ScopeModel scopeModel, String property) {
        // 调用重载方法
        return getProperty(scopeModel, property, null);
    }

    // 用于获取属性
    public static String getProperty(ScopeModel scopeModel, String property, String defaultValue) {
        // 从全局配置中获取并修剪字符串值
        return StringUtils.trim(getGlobalConfiguration(scopeModel).getString(property, defaultValue));
    }

    // 用于获取整数值属性
    public static int get(ScopeModel scopeModel, String property, int defaultValue) {
        // 从全局配置中获取整数值
        return getGlobalConfiguration(scopeModel).getInt(property, defaultValue);
    }

    // 用于解析属性内容
    public static Map<String, String> parseProperties(String content) throws IOException {
        // 创建一个哈希映射存储解析后的属性
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isEmpty(content)) {
            // 如果内容为空，记录警告日志
            logger.warn("You specified the config center, but there's not even one single config item in it.");
        } else {
            // 创建 Properties 对象
            Properties properties = new Properties();
            // 从字符串读取器加载属性
            properties.load(new StringReader(content));
            // 遍历所有属性键
            properties.stringPropertyNames().forEach(
                k -> {
                    // 初始状态为不允许
                    boolean deny = false;
                    for (String key : securityKey) {
                        // 如果属性键包含安全密钥，则设置为不允许
                        if (k.contains(key)) {
                            deny = true;
                            break;
                        }
                    }
                    // 如果允许，则将属性添加到映射中
                    if (!deny) {
                        map.put(k, properties.getProperty(k));
                    }
                });
        }
        // 返回解析后的属性映射
        return map;
    }

    // 用于检查值是否为空
    public static boolean isEmptyValue(Object value) {
        // 检查值是否为空或空白字符串
        return value == null ||
            value instanceof String && StringUtils.isBlank((String) value);
    }

    /**
     * 搜索属性并提取子属性
     * <pre>
     * # 属性
     * dubbo.protocol.name=dubbo
     * dubbo.protocol.port=1234
     *
     * # 提取协议属性
     * Map props = getSubProperties("dubbo.protocol.");
     *
     * # 结果
     * props: {"name": "dubbo", "port" : "1234"}
     *
     * </pre>
     * @param configMaps
     * @param prefix
     * @param <V>
     * @return
     */
    public static <V extends Object> Map<String, V> getSubProperties(Collection<Map<String, V>> configMaps, String prefix) {
        // 创建一个有序映射存储子属性
        Map<String, V> map = new LinkedHashMap<>();
        // 遍历所有配置映射
        for (Map<String, V> configMap : configMaps) {
            // 递归提取子属性
            getSubProperties(configMap, prefix, map);
        }
        // 返回提取的子属性
        return map;
    }

    // 用于获取子属性
    public static <V extends Object> Map<String, V> getSubProperties(Map<String, V> configMap, String prefix) {
        // 调用重载方法
        return getSubProperties(configMap, prefix, null);
    }

    // 用于获取子属性
    private static <V extends Object> Map<String, V> getSubProperties(Map<String, V> configMap, String prefix, Map<String, V> resultMap) {
        // 确保前缀以点号结尾
        if (!prefix.endsWith(".")) {
            prefix += ".";
        }

        // 如果结果映射为空，则创建一个新的有序映射
        if (null == resultMap) {
            resultMap = new LinkedHashMap<>();
        }

        // 如果配置映射不为空
        if (null != configMap) {
            // 遍历配置映射的所有条目
            for (Map.Entry<String, V> entry : configMap.entrySet()) {
                String key = entry.getKey();
                V val = entry.getValue();
                // 如果键以指定前缀开头且长度大于前缀长度且值不为空
                if (StringUtils.startsWithIgnoreCase(key, prefix)
                    && key.length() > prefix.length()
                    && !ConfigurationUtils.isEmptyValue(val)) {

                    // 截取前缀后的部分
                    String k = key.substring(prefix.length());
                    // 将驼峰命名/蛇形命名转换为短横线命名
                    k = StringUtils.convertToSplitName(k, "-");
                    // 将属性添加到结果映射中
                    resultMap.putIfAbsent(k, val);
                }
            }
        }

        // 返回结果映射
        return resultMap;
    }

    // 用于检查是否有子属性
    public static <V extends Object> boolean hasSubProperties(Collection<Map<String, V>> configMaps, String prefix) {
        // 确保前缀以点号结尾
        if (!prefix.endsWith(".")) {
            prefix += ".";
        }
        // 遍历所有配置映射
        for (Map<String, V> configMap : configMaps) {
            // 递归检查是否有子属性
            if (hasSubProperties(configMap, prefix)) {
                return true;
            }
        }
        // 返回检查结果
        return false;
    }

    // 用于检查是否有子属性
    public static <V extends Object> boolean hasSubProperties(Map<String, V> configMap, String prefix) {
        // 确保前缀以点号结尾
        if (!prefix.endsWith(".")) {
            prefix += ".";
        }
        // 遍历配置映射的所有条目
        for (Map.Entry<String, V> entry : configMap.entrySet()) {
            String key = entry.getKey();
            // 如果键以指定前缀开头且长度大于前缀长度且值不为空
            if (StringUtils.startsWithIgnoreCase(key, prefix)
                && key.length() > prefix.length()
                && !ConfigurationUtils.isEmptyValue(entry.getValue())) {
                // 返回真
                return true;
            }
        }
        // 返回假
        return false;
    }

    /**
     * 搜索属性并提取配置 ID
     * <pre>
     * # 属性
     * dubbo.registries.registry1.address=xxx
     * dubbo.registries.registry2.port=xxx
     *
     * # 提取 ID
     * Set configIds = getSubIds("dubbo.registries.")
     *
     * # 结果
     * configIds: ["registry1", "registry2"]
     * </pre>
     *
     * @param configMaps
     * @param prefix
     * @return
     */
    public static <V extends Object> Set<String> getSubIds(Collection<Map<String, V>> configMaps, String prefix) {
        // 确保前缀以点号结尾
        if (!prefix.endsWith(".")) {
            prefix += ".";
        }
        // 创建一个有序集合存储配置 ID
        Set<String> ids = new LinkedHashSet<>();
        // 遍历所有配置映射
        for (Map<String, V> configMap : configMaps) {
            // 遍历配置映射的所有条目
            for (Map.Entry<String, V> entry : configMap.entrySet()) {
                String key = entry.getKey();
                V val = entry.getValue();
                // 如果键以指定前缀开头且长度大于前缀长度且值不为空
                if (StringUtils.startsWithIgnoreCase(key, prefix)
                    && key.length() > prefix.length()
                    && !ConfigurationUtils.isEmptyValue(val)) {

                    // 截取前缀后的部分
                    String k = key.substring(prefix.length());
                    // 查找第一个点号的位置
                    int endIndex = k.indexOf(".");
                    // 如果找到了点号
                    if (endIndex > 0) {
                        // 截取点号前的部分作为 ID
                        String id = k.substring(0, endIndex);
                        // 将 ID 添加到集合中
                        ids.add(id);
                    }
                }
            }
        }
        // 返回配置 ID 集合
        return ids;
    }

    /**
     * 根据指定名称获取 DynamicConfigurationFactory 实例。如果没有找到，则使用默认扩展
     *
     * @param name 扩展名
     * @return 非空
     * @see 2.7.4
     */
    public static DynamicConfigurationFactory getDynamicConfigurationFactory(ExtensionAccessor extensionAccessor, String name) {
        // 获取 ExtensionLoader
        ExtensionLoader<DynamicConfigurationFactory> loader = extensionAccessor.getExtensionLoader(DynamicConfigurationFactory.class);
        // 获取或使用默认扩展
        return loader.getOrDefaultExtension(name);
    }

    /**
     * 为了兼容单实例
     */
    @Deprecated
    public static Configuration getSystemConfiguration() {
        // 返回默认模型的系统配置
        return ApplicationModel.defaultModel().getModelEnvironment().getSystemConfiguration();
    }

    @Deprecated
    public static Configuration getEnvConfiguration() {
        // 返回默认模型的环境配置
        return ApplicationModel.defaultModel().getModelEnvironment().getEnvironmentConfiguration();
    }

    @Deprecated
    public static Configuration getGlobalConfiguration() {
        // 返回默认模型的全局配置
        return ApplicationModel.defaultModel().getModelEnvironment().getConfiguration();
    }

    @Deprecated
    public static Configuration getDynamicGlobalConfiguration() {
        // 返回默认模型的动态全局配置
        return ApplicationModel.defaultModel().getDefaultModule().getModelEnvironment().getDynamicGlobalConfiguration();
    }

    @Deprecated
    public static String getCachedDynamicProperty(String key, String defaultValue) {
        // 返回默认模型的缓存动态属性
        return getCachedDynamicProperty(ApplicationModel.defaultModel(), key, defaultValue);
    }

    @Deprecated
    public static String getDynamicProperty(String property) {
        // 返回默认模型的动态属性
        return getDynamicProperty(ApplicationModel.defaultModel(), property);
    }

    @Deprecated
    public static String getDynamicProperty(String property, String defaultValue) {
        // 返回默认模型的动态属性
        return getDynamicProperty(ApplicationModel.defaultModel(), property, defaultValue);
    }

    @Deprecated
    public static String getProperty(String property) {
        // 返回默认模型的属性
        return getProperty(ApplicationModel.defaultModel(), property);
    }

    @Deprecated
    public static String getProperty(String property, String defaultValue) {
        // 返回默认模型的属性
        return getProperty(ApplicationModel.defaultModel(), property, defaultValue);
    }

    @Deprecated
    public static int get(String property, int defaultValue) {
        // 返回默认模型的整数值属性
        return get(ApplicationModel.defaultModel(), property, defaultValue);
    }
}
