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

import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.context.ApplicationExt;
import org.apache.dubbo.common.context.LifecycleAdapter;
import org.apache.dubbo.common.extension.DisableInject;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.context.ConfigConfigurationAdapter;
import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

// 定义Environment类，继承自LifecycleAdapter并实现ApplicationExt接口
public class Environment extends LifecycleAdapter implements ApplicationExt {
    // 获取Logger实例，用于记录日志
    private static final Logger logger = LoggerFactory.getLogger(Environment.class);

    // 环境的名称
    public static final String NAME = "environment";

    // 类路径中的dubbo属性配置
    private PropertiesConfiguration propertiesConfiguration;

    // Java系统属性(-D)
    private SystemConfiguration systemConfiguration;

    // Java系统环境变量
    private EnvironmentConfiguration environmentConfiguration;

    // 外部配置，如配置中心全局/默认配置
    private InmemoryConfiguration externalConfiguration;

    // 应用外部配置，例如配置中心应用配置
    private InmemoryConfiguration appExternalConfiguration;

    // 本地应用配置，例如Spring Environment/PropertySources/application.properties
    private InmemoryConfiguration appConfiguration;

    // 全局配置组合
    protected CompositeConfiguration globalConfiguration;

    // 全局配置映射列表
    protected List<Map<String, String>> globalConfigurationMaps;

    // 默认动态全局配置
    private CompositeConfiguration defaultDynamicGlobalConfiguration;

    // 默认动态配置
    private DynamicConfiguration defaultDynamicConfiguration;

    // 本地迁移规则
    private String localMigrationRule;

    // 初始化标志，使用AtomicBoolean确保线程安全
    private AtomicBoolean initialized = new AtomicBoolean(false);

    // 范围模型
    private ScopeModel scopeModel;

    // 构造函数，接收ScopeModel参数
    public Environment(ScopeModel scopeModel) {
        this.scopeModel = scopeModel;
    }

    // 实现initialize方法，初始化环境
    @Override
    public void initialize() throws IllegalStateException {
        if (initialized.compareAndSet(false, true)) {
            // 根据范围模型初始化PropertiesConfiguration
            this.propertiesConfiguration = new PropertiesConfiguration(scopeModel);
            // 初始化SystemConfiguration
            this.systemConfiguration = new SystemConfiguration();
            // 初始化EnvironmentConfiguration
            this.environmentConfiguration = new EnvironmentConfiguration();
            // 使用"ExternalConfig"作为标识初始化InmemoryConfiguration
            this.externalConfiguration = new InmemoryConfiguration("ExternalConfig");
            // 使用"AppExternalConfig"作为标识初始化InmemoryConfiguration
            this.appExternalConfiguration = new InmemoryConfiguration("AppExternalConfig");
            // 使用"AppConfig"作为标识初始化InmemoryConfiguration
            this.appConfiguration = new InmemoryConfiguration("AppConfig");

            // 加载迁移规则
            loadMigrationRule();
        }
    }

    // 加载迁移规则的方法
    private void loadMigrationRule() {
        // 尝试从系统属性中获取迁移规则路径
        String path = System.getProperty(CommonConstants.DUBBO_MIGRATION_KEY);
        if (path == null || path.length() == 0) {
            // 如果系统属性中没有，则尝试从环境变量中获取
            path = System.getenv(CommonConstants.DUBBO_MIGRATION_KEY);
            if (path == null || path.length() == 0) {
                // 如果环境变量也没有设置，则使用默认路径
                path = CommonConstants.DEFAULT_DUBBO_MIGRATION_FILE;
            }
        }
        // 根据指定路径加载迁移规则
        this.localMigrationRule = ConfigUtils.loadMigrationRule(scopeModel.getClassLoaders(), path);
    }

    // 设置本地迁移规则（已弃用）
    @Deprecated
    public void setLocalMigrationRule(String localMigrationRule) {
        this.localMigrationRule = localMigrationRule;
    }

    // 设置外部配置映射（禁用注入）
    @DisableInject
    public void setExternalConfigMap(Map<String, String> externalConfiguration) {
        if (externalConfiguration != null) {
            this.externalConfiguration.setProperties(externalConfiguration);
        }
    }

    // 设置应用外部配置映射（禁用注入）
    @DisableInject
    public void setAppExternalConfigMap(Map<String, String> appExternalConfiguration) {
        if (appExternalConfiguration != null) {
            this.appExternalConfiguration.setProperties(appExternalConfiguration);
        }
    }

    // 设置应用配置映射（禁用注入）
    @DisableInject
    public void setAppConfigMap(Map<String, String> appConfiguration) {
        if (appConfiguration != null) {
            this.appConfiguration.setProperties(appConfiguration);
        }
    }

    // 获取外部配置映射
    public Map<String, String> getExternalConfigMap() {
        return externalConfiguration.getProperties();
    }

    // 获取应用外部配置映射
    public Map<String, String> getAppExternalConfigMap() {
        return appExternalConfiguration.getProperties();
    }

    // 获取应用配置映射
    public Map<String, String> getAppConfigMap() {
        return appConfiguration.getProperties();
    }

    // 更新外部配置映射
    public void updateExternalConfigMap(Map<String, String> externalMap) {
        this.externalConfiguration.addProperties(externalMap);
    }

    // 更新应用外部配置映射
    public void updateAppExternalConfigMap(Map<String, String> externalMap) {
        this.appExternalConfiguration.addProperties(externalMap);
    }

    /**
     * 合并目标映射属性到应用配置
     * @param map
     */
    public void updateAppConfigMap(Map<String, String> map) {
        this.appConfiguration.addProperties(map);
    }

    /**
     * 在启动时，Dubbo由各种配置驱动，比如Application、Registry、Protocol等。
     * 所有配置将被汇聚成一个数据总线 - URL，然后驱动后续流程。
     * <p>
     * 目前有许多配置源，包括AbstractConfig (API, XML, 注解), -D, 配置中心等。
     * 此方法帮助我们从多种配置源中筛选出优先级最高的值。
     *
     * @param config
     * @param prefix
     * @return
     */
    public Configuration getPrefixedConfiguration(AbstractConfig config, String prefix) {

        // 序列将会是：SystemConfiguration -> AppExternalConfiguration -> ExternalConfiguration  -> AppConfiguration -> AbstractConfig -> PropertiesConfiguration
        Configuration instanceConfiguration = new ConfigConfigurationAdapter(config, prefix);
        CompositeConfiguration compositeConfiguration = new CompositeConfiguration();
        compositeConfiguration.addConfiguration(systemConfiguration);
        compositeConfiguration.addConfiguration(environmentConfiguration);
        compositeConfiguration.addConfiguration(appExternalConfiguration);
        compositeConfiguration.addConfiguration(externalConfiguration);
        compositeConfiguration.addConfiguration(appConfiguration);
        compositeConfiguration.addConfiguration(instanceConfiguration);
        compositeConfiguration.addConfiguration(propertiesConfiguration);

        return new PrefixedConfiguration(compositeConfiguration, prefix);
    }

    /**
     * 在暴露/引用或运行时获取配置有两种方式：
     * 1. URL, URL中的值相对固定。我们可以直接获取值。
     * 2. 本方法公开的配置便于我们从多个优先级来源查询最新值，同时也保证了动态变更的配置可以即时生效。
     */
    public CompositeConfiguration getConfiguration() {
        if (globalConfiguration == null) {
            CompositeConfiguration configuration = new CompositeConfiguration();
            configuration.addConfiguration(systemConfiguration);
            configuration.addConfiguration(environmentConfiguration);
            configuration.addConfiguration(appExternalConfiguration);
            configuration.addConfiguration(externalConfiguration);
            configuration.addConfiguration(appConfiguration);
            configuration.addConfiguration(propertiesConfiguration);
            globalConfiguration = configuration;
        }
        return globalConfiguration;
    }

    /**
     * 获取目标实例的配置映射列表
     * @param config
     * @param prefix
     * @return
     */
    public List<Map<String, String>> getConfigurationMaps(AbstractConfig config, String prefix) {
        // 序列将会是：SystemConfiguration -> AppExternalConfiguration -> ExternalConfiguration  -> AppConfiguration -> AbstractConfig -> PropertiesConfiguration

        List<Map<String, String>> maps = new ArrayList<>();
        maps.add(systemConfiguration.getProperties());
        maps.add(environmentConfiguration.getProperties());
        maps.add(appExternalConfiguration.getProperties());
        maps.add(externalConfiguration.getProperties());
        maps.add(appConfiguration.getProperties());
        if (config != null) {
            ConfigConfigurationAdapter configurationAdapter = new ConfigConfigurationAdapter(config, prefix);
            maps.add(configurationAdapter.getProperties());
        }
        maps.add(propertiesConfiguration.getProperties());
        return maps;
    }

    /**
     * 获取全局配置为映射列表
     * @return
     */
    public List<Map<String, String>> getConfigurationMaps() {
        if (globalConfigurationMaps == null) {
            globalConfigurationMaps = getConfigurationMaps(null, null);
        }
        return globalConfigurationMaps;
    }

    // 实现destroy方法，销毁环境
    @Override
    public void destroy() throws IllegalStateException {
        initialized.set(false);
        systemConfiguration = null;
        propertiesConfiguration = null;
        environmentConfiguration = null;
        externalConfiguration = null;
        appExternalConfiguration = null;
        appConfiguration = null;
        globalConfiguration = null;
        globalConfigurationMaps = null;
        defaultDynamicGlobalConfiguration = null;
        defaultDynamicConfiguration = null;
    }

    /**
     * 重置环境。
     * 仅供测试使用。
     */
    public void reset() {
        destroy();
        initialize();
    }

    // 解析占位符
    public String resolvePlaceholders(String str) {
        return ConfigUtils.replaceProperty(str, getConfiguration());
    }

    // 获取PropertiesConfiguration
    public PropertiesConfiguration getPropertiesConfiguration() {
        return propertiesConfiguration;
    }

    // 获取SystemConfiguration
    public SystemConfiguration getSystemConfiguration() {
        return systemConfiguration;
    }

    // 获取EnvironmentConfiguration
    public EnvironmentConfiguration getEnvironmentConfiguration() {
        return environmentConfiguration;
    }

    // 获取外部配置
    public InmemoryConfiguration getExternalConfiguration() {
        return externalConfiguration;
    }

    // 获取应用外部配置
    public InmemoryConfiguration getAppExternalConfiguration() {
        return appExternalConfiguration;
    }

    // 获取应用配置
    public InmemoryConfiguration getAppConfiguration() {
        return appConfiguration;
    }

    // 获取本地迁移规则
    public String getLocalMigrationRule() {
        return localMigrationRule;
    }

    // 刷新类加载器
    public void refreshClassLoaders() {
        propertiesConfiguration.refresh();
        loadMigrationRule();
        this.globalConfiguration = null;
        this.globalConfigurationMaps = null;
        this.defaultDynamicGlobalConfiguration = null;
    }

    // 获取动态全局配置
    public Configuration getDynamicGlobalConfiguration() {
        if (defaultDynamicGlobalConfiguration == null) {
            if (defaultDynamicConfiguration == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("dynamicConfiguration is null , return globalConfiguration.");
                }
                return getConfiguration();
            }
            defaultDynamicGlobalConfiguration = new CompositeConfiguration();
            defaultDynamicGlobalConfiguration.addConfiguration(defaultDynamicConfiguration);
            defaultDynamicGlobalConfiguration.addConfiguration(getConfiguration());
        }
        return defaultDynamicGlobalConfiguration;
    }

    // 获取可选的动态配置
    public Optional<DynamicConfiguration> getDynamicConfiguration() {
        return Optional.ofNullable(defaultDynamicConfiguration);
    }

    // 设置动态配置（禁用注入）
    @DisableInject
    public void setDynamicConfiguration(DynamicConfiguration defaultDynamicConfiguration) {
        this.defaultDynamicConfiguration = defaultDynamicConfiguration;
    }
}
