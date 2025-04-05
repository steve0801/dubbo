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
package org.apache.dubbo.common.beanutil;

public enum JavaBeanAccessor {

    // 字段访问器。
    /**
     * Field accessor.
     */
    FIELD,
    // 方法访问器。
    /**
     * Method accessor.
     */
    METHOD,
    // 优先使用方法，如果不可用则使用字段。
    /**
     * Method prefer to field.
     */
    ALL;

    // 判断是否通过方法进行访问。
    public static boolean isAccessByMethod(JavaBeanAccessor accessor) {
        return METHOD.equals(accessor) || ALL.equals(accessor);
    }

    // 判断是否通过字段进行访问。
    public static boolean isAccessByField(JavaBeanAccessor accessor) {
        return FIELD.equals(accessor) || ALL.equals(accessor);
    }

}
