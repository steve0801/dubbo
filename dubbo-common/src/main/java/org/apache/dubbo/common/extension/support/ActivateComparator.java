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
import org.apache.dubbo.common.extension.ExtensionDirector;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.utils.ArrayUtils;

import java.util.Arrays;
import java.util.Comparator;

/**
 * OrderComparator
 */
// 激活注解比较器，用于比较带有@Activate注解的类
public class ActivateComparator implements Comparator<Class<?>> {

    // 扩展点目录
    private ExtensionDirector extensionDirector;

    // 构造函数，传入ExtensionDirector
    public ActivateComparator(ExtensionDirector extensionDirector) {
        this.extensionDirector = extensionDirector;
    }

    // 比较两个类的顺序
    @Override
    public int compare(Class o1, Class o2) {
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
        // 如果是同一个类直接返回0
        if (o1.equals(o2)) {
            return 0;
        }

        // 查找SPI接口
        Class<?> inf = findSpi(o1);

        // 解析Activate注解信息
        ActivateInfo a1 = parseActivate(o1);
        ActivateInfo a2 = parseActivate(o2);

        // 如果两个类中有需要比较before/after的情况
        if ((a1.applicableToCompare() || a2.applicableToCompare()) && inf != null) {
            ExtensionLoader<?> extensionLoader = extensionDirector.getExtensionLoader(inf);

            // 处理a1的before/after比较
            if (a1.applicableToCompare()) {
                String n2 = extensionLoader.getExtensionName(o2);
                if (a1.isLess(n2)) {
                    return -1;
                }
                if (a1.isMore(n2)) {
                    return 1;
                }
            }

            // 处理a2的before/after比较
            if (a2.applicableToCompare()) {
                String n1 = extensionLoader.getExtensionName(o1);
                if (a2.isLess(n1)) {
                    return 1;
                }
                if (a2.isMore(n1)) {
                    return -1;
                }
            }

            // 比较order值
            return a1.order > a2.order ? 1 : -1;
        }

        // 普通比较逻辑
        if (a1.order > a2.order) {
            return 1;
        } else if (a1.order == a2.order) {
            // order相同则按类名排序
            return o1.getSimpleName().compareTo(o2.getSimpleName()) > 0 ? 1 : -1;
        } else {
            return -1;
        }
    }

    // 查找SPI接口
    private Class<?> findSpi(Class<?> clazz) {
        if (clazz.getInterfaces().length == 0) {
            return null;
        }

        // 遍历接口查找SPI注解
        for (Class<?> intf : clazz.getInterfaces()) {
            if (intf.isAnnotationPresent(SPI.class)) {
                return intf;
            } else {
                // 递归查找
                Class<?> result = findSpi(intf);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    // 解析Activate注解信息
    private ActivateInfo parseActivate(Class<?> clazz) {
        ActivateInfo info = new ActivateInfo();
        if (clazz.isAnnotationPresent(Activate.class)) {
            Activate activate = clazz.getAnnotation(Activate.class);
            info.before = activate.before();
            info.after = activate.after();
            info.order = activate.order();
        } else if (clazz.isAnnotationPresent(com.alibaba.dubbo.common.extension.Activate.class)){
            // 兼容alibaba的Activate注解
            com.alibaba.dubbo.common.extension.Activate activate = clazz.getAnnotation(
                    com.alibaba.dubbo.common.extension.Activate.class);
            info.before = activate.before();
            info.after = activate.after();
            info.order = activate.order();
        } else {
            info.order = 0;
        }
        return info;
    }

    // Activate注解信息内部类
    private static class ActivateInfo {
        private String[] before;  // 需要在哪些扩展之前
        private String[] after;   // 需要在哪些扩展之后
        private int order;        // 排序值

        // 是否需要比较before/after
        private boolean applicableToCompare() {
            return ArrayUtils.isNotEmpty(before) || ArrayUtils.isNotEmpty(after);
        }

        // 判断是否需要在指定扩展之前
        private boolean isLess(String name) {
            return Arrays.asList(before).contains(name);
        }

        // 判断是否需要在指定扩展之后
        private boolean isMore(String name) {
            return Arrays.asList(after).contains(name);
        }
    }
}
