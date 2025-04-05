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

import org.apache.dubbo.common.convert.StringConverter;

import java.util.Collection;
import java.util.Optional;

import static org.apache.dubbo.common.convert.Converter.getConverter;
import static org.apache.dubbo.common.utils.ClassUtils.getAllInterfaces;
import static org.apache.dubbo.common.utils.ClassUtils.isAssignableFrom;
import static org.apache.dubbo.common.utils.TypeUtils.findActualTypeArgument;

/**
 * The class to convert {@link String} to {@link Iterable}-based value
 *
 * @since 2.7.6
 */
// 抽象类，实现字符串到Iterable集合的转换
public abstract class StringToIterableConverter<T extends Iterable> implements StringToMultiValueConverter {

    // 判断是否支持指定的源类型和目标类型
    public boolean accept(Class<String> type, Class<?> multiValueType) {
        return isAssignableFrom(getSupportedType(), multiValueType);
    }

    // 将字符串数组转换为Iterable集合
    @Override
    public final Object convert(String[] segments, int size, Class<?> multiValueType, Class<?> elementType) {
        // 获取字符串到元素类型的转换器
        Optional<StringConverter> stringConverter = getStringConverter(elementType);

        return stringConverter.map(converter -> {
            // 创建目标集合实例
            T convertedObject = createMultiValue(size, multiValueType);

            // 如果是Collection类型，则逐个转换并添加元素
            if (convertedObject instanceof Collection) {
                Collection collection = (Collection) convertedObject;
                for (int i = 0; i < size; i++) {
                    String segment = segments[i];
                    Object element = converter.convert(segment);
                    collection.add(element);
                }
                return collection;
            }

            return convertedObject;
        }).orElse(null);
    }

    // 抽象方法，创建指定大小的集合实例
    protected abstract T createMultiValue(int size, Class<?> multiValueType);

    // 获取字符串到指定元素类型的转换器
    protected Optional<StringConverter> getStringConverter(Class<?> elementType) {
        StringConverter converter = (StringConverter) getConverter(String.class, elementType);
        return Optional.ofNullable(converter);
    }

    // 获取支持的集合类型
    protected final Class<T> getSupportedType() {
        return findActualTypeArgument(getClass(), StringToIterableConverter.class, 0);
    }

    // 获取转换器优先级
    @Override
    public final int getPriority() {
        // 根据继承层级计算优先级
        int level = getAllInterfaces(getSupportedType(), type ->
                isAssignableFrom(Iterable.class, type)).size();
        return MIN_PRIORITY - level;
    }
}
