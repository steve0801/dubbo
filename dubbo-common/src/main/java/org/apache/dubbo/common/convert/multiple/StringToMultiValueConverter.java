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

import org.apache.dubbo.common.utils.ArrayUtils;

import static org.apache.dubbo.common.utils.StringUtils.isEmpty;
import static org.apache.dubbo.common.utils.StringUtils.split;

/**
 * The class to convert {@link String} to multiple value object
 *
 * @see MultiValueConverter
 * @since 2.7.6
 */
// 字符串到多值类型的转换器接口
public interface StringToMultiValueConverter extends MultiValueConverter<String> {

    // 默认实现：将字符串转换为多值对象
    @Override
    default Object convert(String source, Class<?> multiValueType, Class<?> elementType) {
        // 如果源字符串为空则返回null
        if (isEmpty(source)) {
            return null;
        }

        // 按逗号分割字符串
        String[] segments = split(source, ',');

        // 如果分割后为空数组，则创建一个包含原始字符串的单元素数组
        if (ArrayUtils.isEmpty(segments)) {
            segments = new String[]{source};
        }

        int size = segments.length;

        // 调用具体实现方法进行转换
        return convert(segments, size, multiValueType, elementType);
    }

    /**
     * 将字符串数组转换为多值对象
     * @param segments    字符串数组
     * @param size       多值对象的大小
     * @param targetType  目标类型
     * @param elementType 元素类型
     * @return 转换后的多值对象
     */
    Object convert(String[] segments, int size, Class<?> targetType, Class<?> elementType);
}
