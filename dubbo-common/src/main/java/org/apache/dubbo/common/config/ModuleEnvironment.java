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
import org.apache.dubbo.common.context.ModuleExt;
import org.apache.dubbo.common.extension.DisableInject;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.rpc.model.ModuleModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ModuleEnvironment extends Environment implements ModuleExt {

    // 使用委托模式实现模块环境功能

    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(ModuleEnvironment.class);

    // 环境名称常量
    public static final String NAME = "moduleEnvironment";

    // 模块模型引用
    private final ModuleModel moduleModel;

    // 应用级环境委托对象
    private Environment applicationDelegate;

    // 有序属性配置
    private OrderedPropertiesConfiguration orderedPropertiesConfiguration;

    // 动态全局配置组合
    private CompositeConfiguration dynamicGlobalConfiguration;

    // 动态配置实例
    private DynamicConfiguration dynamicConfiguration;

    // 构造方法，初始化模块环境
    public ModuleEnvironment(ModuleModel moduleModel) {
        super(moduleModel);
        this.moduleModel = moduleModel;
        this.applicationDelegate = moduleModel.getApplicationModel().getModelEnvironment();
    }

    // 初始化方法
    @Override
    public void initialize() throws IllegalStateException {
        this.orderedPropertiesConfiguration = new OrderedPropertiesConfiguration(moduleModel);
    }

    // 获取带前缀的配置
    @Override
    public Configuration getPrefixedConfiguration(AbstractConfig config, String prefix) {
        CompositeConfiguration compositeConfiguration = new CompositeConfiguration();
        compositeConfiguration.addConfiguration(applicationDelegate.getPrefixedConfiguration(config, prefix));
        compositeConfiguration.addConfiguration(orderedPropertiesConfiguration);

        return new PrefixedConfiguration(compositeConfiguration, prefix);
    }

    // 获取组合配置
    @Override
    public CompositeConfiguration getConfiguration() {
        if (globalConfiguration == null) {
            CompositeConfiguration configuration = new CompositeConfiguration();
            configuration.addConfiguration(applicationDelegate.getConfiguration());
            configuration.addConfiguration(orderedPropertiesConfiguration);
            globalConfiguration = configuration;
        }
        return globalConfiguration;
    }

    // 获取配置映射列表
    @Override
    public List<Map<String, String>> getConfigurationMaps(AbstractConfig config, String prefix) {
        List<Map<String, String>> maps = applicationDelegate.getConfigurationMaps(config, prefix);
        maps.add(orderedPropertiesConfiguration.getProperties());
        return maps;
    }

    // 获取动态全局配置
    @Override
    public Configuration getDynamicGlobalConfiguration() {
        if (dynamicConfiguration == null) {
            return applicationDelegate.getDynamicGlobalConfiguration();
        }
        if (dynamicGlobalConfiguration == null) {
            if (dynamicConfiguration == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("dynamicConfiguration is null , return globalConfiguration.");
                }
                return getConfiguration();
            }
            dynamicGlobalConfiguration = new CompositeConfiguration();
            dynamicGlobalConfiguration.addConfiguration(dynamicConfiguration);
            dynamicGlobalConfiguration.addConfiguration(getConfiguration());
        }
        return dynamicGlobalConfiguration;
    }

    // 获取动态配置Optional对象
    @Override
    public Optional<DynamicConfiguration> getDynamicConfiguration() {
        if (dynamicConfiguration == null) {
            return applicationDelegate.getDynamicConfiguration();
        }
        return Optional.ofNullable(dynamicConfiguration);
    }

    // 设置动态配置（禁用依赖注入）
    @Override
    @DisableInject
    public void setDynamicConfiguration(DynamicConfiguration dynamicConfiguration) {
        this.dynamicConfiguration = dynamicConfiguration;
    }

    // 销毁方法，释放资源
    @Override
    public void destroy() throws IllegalStateException {
        this.orderedPropertiesConfiguration = null;
        this.globalConfiguration = null;
        this.dynamicGlobalConfiguration = null;
        this.dynamicConfiguration = null;
    }

    // 设置本地迁移规则
    @Override
    public void setLocalMigrationRule(String localMigrationRule) {
        applicationDelegate.setLocalMigrationRule(localMigrationRule);
    }

    // 设置外部配置映射
    @Override
    public void setExternalConfigMap(Map<String, String> externalConfiguration) {
        applicationDelegate.setExternalConfigMap(externalConfiguration);
    }

    // 设置应用外部配置映射
    @Override
    public void setAppExternalConfigMap(Map<String, String> appExternalConfiguration) {
        applicationDelegate.setAppExternalConfigMap(appExternalConfiguration);
    }

    // 设置应用配置映射
    @Override
    public void setAppConfigMap(Map<String, String> appConfiguration) {
        applicationDelegate.setAppConfigMap(appConfiguration);
    }

    // 获取外部配置映射
    @Override
    public Map<String, String> getExternalConfigMap() {
        return applicationDelegate.getExternalConfigMap();
    }

    // 获取应用外部配置映射
    @Override
    public Map<String, String> getAppExternalConfigMap() {
        return applicationDelegate.getAppExternalConfigMap();
    }

    // 获取应用配置映射
    @Override
    public Map<String, String> getAppConfigMap() {
        return applicationDelegate.getAppConfigMap();
    }

    // 更新外部配置映射
    @Override
    public void updateExternalConfigMap(Map<String, String> externalMap) {
        applicationDelegate.updateExternalConfigMap(externalMap);
    }

    // 更新应用外部配置映射
    @Override
    public void updateAppExternalConfigMap(Map<String, String> externalMap) {
        applicationDelegate.updateAppExternalConfigMap(externalMap);
    }

    // 更新应用配置映射
    @Override
    public void updateAppConfigMap(Map<String, String> map) {
        applicationDelegate.updateAppConfigMap(map);
    }

    // 获取属性配置
    @Override
    public PropertiesConfiguration getPropertiesConfiguration() {
        return applicationDelegate.getPropertiesConfiguration();
    }

    // 获取系统配置
    @Override
    public SystemConfiguration getSystemConfiguration() {
        return applicationDelegate.getSystemConfiguration();
    }

    // 获取环境配置
    @Override
    public EnvironmentConfiguration getEnvironmentConfiguration() {
        return applicationDelegate.getEnvironmentConfiguration();
    }

    // 获取外部配置
    @Override
    public InmemoryConfiguration getExternalConfiguration() {
        return applicationDelegate.getExternalConfiguration();
    }

    // 获取应用外部配置
    @Override
    public InmemoryConfiguration getAppExternalConfiguration() {
        return applicationDelegate.getAppExternalConfiguration();
    }

    // 获取应用配置
    @Override
    public InmemoryConfiguration getAppConfiguration() {
        return applicationDelegate.getAppConfiguration();
    }

    // 获取本地迁移规则
    @Override
    public String getLocalMigrationRule() {
        return applicationDelegate.getLocalMigrationRule();
    }

    // 刷新类加载器
    @Override
    public void refreshClassLoaders() {
        orderedPropertiesConfiguration.refresh();
        applicationDelegate.refreshClassLoaders();
        this.globalConfiguration = null;
        this.globalConfigurationMaps = null;
        this.dynamicGlobalConfiguration = null;
    }
}
