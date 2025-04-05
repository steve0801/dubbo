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
package org.apache.dubbo.common.extension;

/**
 * An injector to provide resources for SPI extension.
 */
// 扩展注入器接口，声明为SPI扩展点，作用域为SELF
@SPI(scope = ExtensionScope.SELF)
public interface ExtensionInjector extends ExtensionAccessorAware {

    /**
     * 获取指定类型和名称的实例
     * @param type 对象类型
     * @param name 对象名称
     * @return 对象实例
     * @param <T> 泛型类型
     */
    <T> T getInstance(Class<T> type, String name);

    // 设置扩展访问器的默认空实现
    @Override
    default void setExtensionAccessor(ExtensionAccessor extensionAccessor) {
    }
}
