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
package org.apache.dubbo.common.extension.inject;

import org.apache.dubbo.common.extension.ExtensionAccessor;
import org.apache.dubbo.common.extension.ExtensionInjector;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;

/**
 * SpiExtensionInjector
 */
// SPI扩展注入器实现类，用于通过SPI机制注入扩展点
public class SpiExtensionInjector implements ExtensionInjector {

    // 扩展访问器，用于获取ExtensionLoader
    private ExtensionAccessor extensionAccessor;

    // 设置扩展访问器
    @Override
    public void setExtensionAccessor(ExtensionAccessor extensionAccessor) {
        this.extensionAccessor = extensionAccessor;
    }

    /**
     * 获取指定类型和名称的实例
     * @param type 需要注入的类型
     * @param name 扩展点名称
     * @return 返回适配扩展实例
     */
    @Override
    public <T> T getInstance(Class<T> type, String name) {
        // 检查类型是否为接口且带有SPI注解
        if (type.isInterface() && type.isAnnotationPresent(SPI.class)) {
            // 获取对应类型的ExtensionLoader
            ExtensionLoader<T> loader = extensionAccessor.getExtensionLoader(type);
            if (loader == null) {
                return null;
            }
            // 如果有支持的扩展点，返回适配扩展
            if (!loader.getSupportedExtensions().isEmpty()) {
                return loader.getAdaptiveExtension();
            }
        }
        return null;
    }
}

