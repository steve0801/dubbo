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
package org.apache.dubbo.common.extension.support;

import org.apache.dubbo.common.extension.Activate;

import java.util.Comparator;

/**
 * OrderComparator  
 * Derived from {@link ActivateComparator}
 */
// 包装类比较器，用于比较Wrapper类的顺序
public class WrapperComparator implements Comparator<Object> {

    // 单例比较器实例
    public static final Comparator<Object> COMPARATOR = new WrapperComparator();

    // 比较两个对象的顺序
    @Override
    public int compare(Object o1, Object o2) {
        // 处理null值情况
        if (o1 == null && o2 == null) {
            return 0;
        }
        if (o1 == null) {
            return -1;
        }
        if (o2 == null) {
            return 1;
        }
        // 如果是同一个对象直接返回0
        if (o1.equals(o2)) {
            return 0;
        }

        // 转换为Class类型
        Class clazz1 = (Class) o1;
        Class clazz2 = (Class) o2;

        // 解析Order信息
        OrderInfo a1 = parseOrder(clazz1);
        OrderInfo a2 = parseOrder(clazz2);

        // 比较order值
        int n1 = a1.order;
        int n2 = a2.order;
        // 即使n1等于n2也返回1或-1，避免在HashSet等集合中互相覆盖
        return n1 > n2 ? 1 : -1;
    }

    // 解析类的Order信息
    private OrderInfo parseOrder(Class<?> clazz) {
        OrderInfo info = new OrderInfo();
        // 处理Apache Dubbo的Activate注解
        if (clazz.isAnnotationPresent(Activate.class)) {
            Activate activate = clazz.getAnnotation(Activate.class);
            info.order = activate.order();
        }
        // 处理Alibaba Dubbo的Activate注解
        else if (clazz.isAnnotationPresent(com.alibaba.dubbo.common.extension.Activate.class)) {
            com.alibaba.dubbo.common.extension.Activate activate = clazz.getAnnotation(
                    com.alibaba.dubbo.common.extension.Activate.class);
            info.order = activate.order();
        }
        return info;
    }

    // Order信息内部类
    private static class OrderInfo {
        private int order;  // 顺序值
    }
}
