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

import static java.lang.Boolean.valueOf;
import static org.apache.dubbo.common.utils.StringUtils.isNotEmpty;

/**
 * The class to convert {@link String} to {@link Boolean}
 *
 * @since 2.7.6
 */
public class StringToBooleanConverter implements StringConverter<Boolean> {

    // 将字符串转换为Boolean类型
    @Override
    public Boolean convert(String source) {
        // 如果字符串不为空则转换，否则返回null
        return isNotEmpty(source) ? valueOf(source) : null;
    }

    // 获取转换器优先级
    @Override
    public int getPriority() {
        // 返回普通优先级+5
        return NORMAL_PRIORITY + 5;
    }
}
