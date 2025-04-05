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
package org.apache.dubbo.common;

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ServiceModel;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_VERSION;

/**
 * 2019-10-10
 */
public class BaseServiceMetadata {
    // 定义冒号分隔符常量，用于构建服务key
    public static final char COLON_SEPARATOR = ':';

    // 服务唯一标识key，格式为[group]/interface:version
    protected String serviceKey;
    // 服务接口全限定名
    protected String serviceInterfaceName;
    // 服务版本号
    protected String version;
    // 服务分组，volatile保证多线程可见性
    protected volatile String group;
    // 关联的服务模型对象
    private ServiceModel serviceModel;

    /**
     * 构建服务key的标准方法
     * @param path 服务接口名
     * @param group 服务分组
     * @param version 服务版本
     * @return 格式化为[group]/interface:version的字符串
     */
    public static String buildServiceKey(String path, String group, String version) {
        // 计算字符串总长度用于优化StringBuilder初始化
        int length = path == null ? 0 : path.length();
        length += group == null ? 0 : group.length();
        length += version == null ? 0 : version.length();
        length += 3;  // 为分隔符预留空间

        // 构建服务key字符串
        StringBuilder buf = new StringBuilder(length);
        // 如果存在分组则添加分组部分
        if (group != null && group.length() > 0) {
            buf.append(group).append('/');
        }
        // 添加接口名
        buf.append(path);
        // 如果存在版本则添加版本部分
        if (version != null && version.length() > 0) {
            buf.append(':').append(version);
        }
        // 使用intern()方法缓存字符串
        return buf.toString().intern();
    }

    /**
     * 从服务key中提取版本信息
     * @param serviceKey 服务key字符串
     * @return 版本号，如果不存在则返回默认版本
     */
    public static String versionFromServiceKey(String serviceKey) {
        int index = serviceKey.indexOf(":");
        if (index == -1) {
            return DEFAULT_VERSION;
        }
        return serviceKey.substring(index + 1);
    }

    /**
     * 从服务key中提取分组信息
     * @param serviceKey 服务key字符串
     * @return 分组名，如果不存在则返回null
     */
    public static String groupFromServiceKey(String serviceKey) {
        int index = serviceKey.indexOf("/");
        if (index == -1) {
            return null;
        }
        return serviceKey.substring(0, index);
    }

    /**
     * 从服务key中提取接口名
     * @param serviceKey 服务key字符串
     * @return 接口全限定名
     */
    public static String interfaceFromServiceKey(String serviceKey) {
        int groupIndex = serviceKey.indexOf("/");
        int versionIndex = serviceKey.indexOf(":");
        // 处理分组不存在的情况
        groupIndex = (groupIndex == -1) ? 0 : groupIndex + 1;
        // 处理版本不存在的情况
        versionIndex = (versionIndex == -1) ? serviceKey.length() : versionIndex;
        return serviceKey.substring(groupIndex, versionIndex);
    }

    /**
     * 获取格式化为interface:version的显示用服务key
     * @return 格式化的服务key字符串
     */
    public String getDisplayServiceKey() {
        StringBuilder serviceNameBuilder = new StringBuilder();
        serviceNameBuilder.append(serviceInterfaceName);
        serviceNameBuilder.append(COLON_SEPARATOR).append(version);
        return serviceNameBuilder.toString();
    }

    /**
     * 将显示用的服务key还原为BaseServiceMetadata对象
     * @param displayKey 格式为interface:version的字符串
     * @return 包含接口名和版本的服务元数据对象
     */
    public static BaseServiceMetadata revertDisplayServiceKey(String displayKey) {
        String[] eles = StringUtils.split(displayKey, COLON_SEPARATOR);
        if (eles == null || eles.length < 1 || eles.length > 2) {
            return new BaseServiceMetadata();
        }
        BaseServiceMetadata serviceDescriptor = new BaseServiceMetadata();
        serviceDescriptor.setServiceInterfaceName(eles[0]);
        if (eles.length == 2) {
            serviceDescriptor.setVersion(eles[1]);
        }
        return serviceDescriptor;
    }

    /**
     * 生成不包含分组的服务key
     * @param interfaceName 接口名
     * @param version 版本号
     * @return 格式为interface:version的字符串
     */
    public static String keyWithoutGroup(String interfaceName, String version) {
        if (StringUtils.isEmpty(version)) {
            return interfaceName + ":0.0.0";
        }
        return interfaceName + ":" + version;
    }

    // 获取服务key
    public String getServiceKey() {
        return serviceKey;
    }

    // 根据当前属性生成服务key
    public void generateServiceKey() {
        this.serviceKey = buildServiceKey(serviceInterfaceName, group, version);
    }

    // 设置服务key
    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    // 获取服务接口名
    public String getServiceInterfaceName() {
        return serviceInterfaceName;
    }

    // 设置服务接口名
    public void setServiceInterfaceName(String serviceInterfaceName) {
        this.serviceInterfaceName = serviceInterfaceName;
    }

    // 获取服务版本
    public String getVersion() {
        return version;
    }

    // 设置服务版本
    public void setVersion(String version) {
        this.version = version;
    }

    // 获取服务分组
    public String getGroup() {
        return group;
    }

    // 设置服务分组
    public void setGroup(String group) {
        this.group = group;
    }

    // 获取服务模型对象
    public ServiceModel getServiceModel() {
        return serviceModel;
    }

    // 设置服务模型对象
    public void setServiceModel(ServiceModel serviceModel) {
        this.serviceModel = serviceModel;
    }
}
