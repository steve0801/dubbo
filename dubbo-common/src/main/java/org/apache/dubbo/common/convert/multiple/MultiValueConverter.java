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
package org.apache.dubbo.common.convert.multiple;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.lang.Prioritized;

import java.util.Collection;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.utils.TypeUtils.findActualTypeArgument;

/**
 * An interface to convert the source-typed value to multiple value, e.g , Java array, {@link Collection} or
 * sub-interfaces
 *
 * @param <S> The source type
 * @since 2.7.6
 */
// 多值转换器接口，支持SPI扩展机制
@SPI(scope = ExtensionScope.FRAMEWORK)
public interface MultiValueConverter<S> extends Prioritized {

    /**
     * 判断是否接受指定的源类型和目标类型
     * @param sourceType     源类型
     * @param multiValueType 多值类型
     * @return 如果接受返回true，否则返回false
     */
    boolean accept(Class<S> sourceType, Class<?> multiValueType);

    /**
     * 将源值转换为多值类型
     * @param source         源值
     * @param multiValueType 目标多值类型
     * @param elementType    元素类型
     * @return 转换后的多值对象
     */
    Object convert(S source, Class<?> multiValueType, Class<?> elementType);

    /**
     * 获取源类型
     * @return 非空的源类型
     */
    default Class<S> getSourceType() {
        return findActualTypeArgument(getClass(), MultiValueConverter.class, 0);
    }

    /**
     * 从ExtensionLoader中查找匹配的MultiValueConverter实例
     * @param sourceType 源类型
     * @param targetType 目标类型
     * @return 找到的转换器实例，未找到返回null
     * @see ExtensionLoader#getSupportedExtensionInstances()
     * @since 2.7.8
     */
    static MultiValueConverter<?> find(Class<?> sourceType, Class<?> targetType) {
        return getExtensionLoader(MultiValueConverter.class)
                .getSupportedExtensionInstances()
                .stream()
                .filter(converter -> converter.accept(sourceType, targetType))
                .findFirst()
                .orElse(null);
    }

    /**
     * 如果可能，将源对象转换为指定的多值类型
     * @param source        源对象
     * @param multiValueType 目标多值类型
     * @param elementType   元素类型
     * @param <T>          目标类型
     * @return 转换后的对象，无法转换返回null
     */
    static <T> T convertIfPossible(Object source, Class<?> multiValueType, Class<?> elementType) {
        Class<?> sourceType = source.getClass();
        MultiValueConverter converter = find(sourceType, multiValueType);
        if (converter != null) {
            return (T) converter.convert(source, multiValueType, elementType);
        }
        return null;
    }
}
