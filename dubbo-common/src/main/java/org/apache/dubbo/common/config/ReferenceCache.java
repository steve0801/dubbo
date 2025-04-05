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

import org.apache.dubbo.config.ReferenceConfigBase;

import java.util.List;

public interface ReferenceCache {
    // 根据ReferenceConfigBase获取缓存引用对象
    @SuppressWarnings("unchecked")
    <T> T get(ReferenceConfigBase<T> referenceConfig);

    // 根据key和类型获取缓存引用对象
    @SuppressWarnings("unchecked")
    <T> T get(String key, Class<T> type);

    // 根据key获取缓存引用对象
    @SuppressWarnings("unchecked")
    <T> T get(String key);

    // 获取指定类型的所有缓存引用对象列表
    @SuppressWarnings("unchecked")
    <T> List<T> getAll(Class<T> type);

    // 获取指定类型的缓存引用对象
    @SuppressWarnings("unchecked")
    <T> T get(Class<T> type);

    // 根据key和类型销毁缓存引用对象
    void destroy(String key, Class<?> type);

    // 销毁指定类型的所有缓存引用对象
    void destroy(Class<?> type);

    // 根据ReferenceConfigBase销毁缓存引用对象
    <T> void destroy(ReferenceConfigBase<T> referenceConfig);

    // 销毁所有缓存引用对象
    void destroyAll();
}
