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

import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionAccessor;
import org.apache.dubbo.common.extension.ExtensionInjector;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AdaptiveExtensionInjector
 */
// 自适应扩展注入器，实现ExtensionInjector和Lifecycle接口
@Adaptive  // 标记为自适应扩展实现
public class AdaptiveExtensionInjector implements ExtensionInjector, Lifecycle {

    // 注入器列表，初始化为空列表
    private List<ExtensionInjector> injectors = Collections.emptyList();

    // 扩展访问器
    private ExtensionAccessor extensionAccessor;

    // 默认构造函数
    public AdaptiveExtensionInjector() {
    }

    // 设置扩展访问器
    @Override
    public void setExtensionAccessor(ExtensionAccessor extensionAccessor) {
        this.extensionAccessor = extensionAccessor;
    }

    // 初始化方法
    @Override
    public void initialize() throws IllegalStateException {
        // 获取ExtensionInjector的扩展加载器
        ExtensionLoader<ExtensionInjector> loader = extensionAccessor.getExtensionLoader(ExtensionInjector.class);
        List<ExtensionInjector> list = new ArrayList<ExtensionInjector>();
        // 加载所有支持的扩展注入器
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        // 转换为不可修改列表
        injectors = Collections.unmodifiableList(list);
    }

    // 获取指定类型的实例
    @Override
    public <T> T getInstance(Class<T> type, String name) {
        // 遍历所有注入器尝试获取实例
        for (ExtensionInjector injector : injectors) {
            T extension = injector.getInstance(type, name);
            if (extension != null) {
                return extension;
            }
        }
        return null;  // 没有找到返回null
    }

    // 启动方法（空实现）
    @Override
    public void start() throws IllegalStateException {
    }

    // 销毁方法（空实现）
    @Override
    public void destroy() throws IllegalStateException {
    }
}
