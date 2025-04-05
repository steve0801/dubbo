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
package org.apache.dubbo.common.compiler;

import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.extension.SPI;

/**
 * Compiler. (SPI, Singleton, ThreadSafe)
 */
@SPI(value = "javassist", scope = ExtensionScope.FRAMEWORK)
// 定义Compiler接口，并使用@SPI注解，指定默认实现为"javassist"，作用范围为框架级
public interface Compiler {

    /**
     * 编译Java源代码。
     *
     * @param code        Java源代码字符串
     * @param classLoader 类加载器
     * @return 编译后的Class对象
     */
    // 该方法用于编译给定的Java源代码，并返回编译后的类对象
    Class<?> compile(String code, ClassLoader classLoader);
}
