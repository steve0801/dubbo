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

import static org.apache.dubbo.common.utils.StringUtils.length;

/**
 * The class to convert {@link String} to {@link Character}
 *
 * @since 2.7.6
 */
public class StringToCharacterConverter implements StringConverter<Character> {

    // 将字符串转换为Character类型
    @Override
    public Character convert(String source) {
        // 获取字符串长度
        int length = length(source);
        // 如果字符串为空则返回null
        if (length == 0) {
            return null;
        }
        // 如果字符串长度大于1则抛出异常
        if (length > 1) {
            throw new IllegalArgumentException("The source String is more than one character!");
        }
        // 返回字符串的第一个字符
        return source.charAt(0);
    }

    // 获取转换器优先级
    @Override
    public int getPriority() {
        // 返回普通优先级+8
        return NORMAL_PRIORITY + 8;
    }
}
