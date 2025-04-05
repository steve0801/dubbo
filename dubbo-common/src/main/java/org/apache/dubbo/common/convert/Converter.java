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
package org.apache.dubbo.common.convert;

import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.lang.Prioritized;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.utils.ClassUtils.isAssignableFrom;
import static org.apache.dubbo.common.utils.TypeUtils.findActualTypeArgument;

/**
 * A class to convert the source-typed value to the target-typed value
 *
 * @param <S> The source type
 * @param <T> The target type
 * @since 2.7.6
 */
// 转换器接口，支持SPI扩展机制
@SPI(scope = ExtensionScope.FRAMEWORK)
@FunctionalInterface
public interface Converter<S, T> extends Prioritized {

    // 判断是否接受指定的源类型和目标类型
    default boolean accept(Class<?> sourceType, Class<?> targetType) {
        return isAssignableFrom(sourceType, getSourceType()) && isAssignableFrom(targetType, getTargetType());
    }

    // 将源类型值转换为目标类型值
    T convert(S source);

    // 获取源类型
    default Class<S> getSourceType() {
        return findActualTypeArgument(getClass(), Converter.class, 0);
    }

    // 获取目标类型
    default Class<T> getTargetType() {
        return findActualTypeArgument(getClass(), Converter.class, 1);
    }

    // 根据源类型和目标类型获取转换器实例
    static Converter<?, ?> getConverter(Class<?> sourceType, Class<?> targetType) {
        return getExtensionLoader(Converter.class)
                .getSupportedExtensionInstances()
                .stream()
                .filter(converter -> converter.accept(sourceType, targetType))
                .findFirst()
                .orElse(null);
    }

    // 如果可能，将源值转换为目标类型值
    static <T> T convertIfPossible(Object source, Class<T> targetType) {
        Converter converter = getConverter(source.getClass(), targetType);
        if (converter != null) {
            return (T) converter.convert(source);
        }
        return null;
    }
}

