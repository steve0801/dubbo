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
package org.apache.dubbo.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicating unstable API, may get removed or changed in the next release.
 */
// 定义注解的保留策略为CLASS级别，表示该注解会保留在class文件中
@Retention(RetentionPolicy.CLASS)
// 指定该注解可以应用的目标元素类型
@Target({
        // 可以应用于注解类型
        ElementType.ANNOTATION_TYPE,
        // 可以应用于构造方法
        ElementType.CONSTRUCTOR,
        // 可以应用于字段
        ElementType.FIELD,
        // 可以应用于方法
        ElementType.METHOD,
        // 可以应用于包
        ElementType.PACKAGE,
        // 可以应用于类、接口、枚举等类型
        ElementType.TYPE})
// 定义一个名为Experimental的注解
public @interface Experimental {
    // 定义注解的value属性，用于存储实验性功能的描述信息
    String value();
}
