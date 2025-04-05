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

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaBeanDescriptor implements Serializable, Iterable<Map.Entry<Object, Object>> {

    // 序列化版本号。
    private static final long serialVersionUID = -8505586483570518029L;

    // 类型常量：表示类类型。
    public static final int TYPE_CLASS = 1;
    // 类型常量：表示枚举类型。
    public static final int TYPE_ENUM = 2;
    // 类型常量：表示集合类型。
    public static final int TYPE_COLLECTION = 3;
    // 类型常量：表示映射类型。
    public static final int TYPE_MAP = 4;
    // 类型常量：表示数组类型。
    public static final int TYPE_ARRAY = 5;
    // 类型常量：表示基本类型。
    /**
     * @see org.apache.dubbo.common.utils.ReflectUtils#isPrimitive(Class)
     */
    public static final int TYPE_PRIMITIVE = 6;
    // 类型常量：表示JavaBean类型。
    public static final int TYPE_BEAN = 7;

    // 枚举类型的属性名。
    private static final String ENUM_PROPERTY_NAME = "name";
    // 类类型的属性名。
    private static final String CLASS_PROPERTY_NAME = "name";
    // 基本类型的属性值。
    private static final String PRIMITIVE_PROPERTY_VALUE = "value";

    // 用于定义有效类型的上限。
    /**
     * Used to define a type is valid.
     *
     * @see #isValidType(int)
     */
    private static final int TYPE_MAX = TYPE_BEAN;
    // 用于定义有效类型的下限。
    /**
     * Used to define a type is valid.
     *
     * @see #isValidType(int)
     */
    private static final int TYPE_MIN = TYPE_CLASS;

    // 类名。
    private String className;
    // 类型。
    private int type;

    // 属性的键值对集合。
    private Map<Object, Object> properties = new LinkedHashMap<>();

    // 默认构造函数。
    public JavaBeanDescriptor() {}

    // 构造函数，初始化JavaBeanDescriptor。
    public JavaBeanDescriptor(String className, int type) {
        // 检查类名是否为空。
        notEmpty(className, "class name is empty");
        // 检查类型是否有效。
        if (!isValidType(type)) {
            throw new IllegalArgumentException("type [ " + type + " ] is unsupported");
        }

        this.className = className;
        this.type = type;
    }

    // 判断是否为类类型。
    public boolean isClassType() {
        return TYPE_CLASS == type;
    }

    // 判断是否为枚举类型。
    public boolean isEnumType() {
        return TYPE_ENUM == type;
    }

    // 判断是否为集合类型。
    public boolean isCollectionType() {
        return TYPE_COLLECTION == type;
    }

    // 判断是否为映射类型。
    public boolean isMapType() {
        return TYPE_MAP == type;
    }

    // 判断是否为数组类型。
    public boolean isArrayType() {
        return TYPE_ARRAY == type;
    }

    // 判断是否为基本类型。
    public boolean isPrimitiveType() {
        return TYPE_PRIMITIVE == type;
    }

    // 判断是否为JavaBean类型。
    public boolean isBeanType() {
        return TYPE_BEAN == type;
    }

    // 获取类型。
    public int getType() {
        return type;
    }

    // 设置类型。
    public void setType(int type) {
        this.type = type;
    }

    // 获取类名。
    public String getClassName() {
        return className;
    }

    // 设置类名。
    public void setClassName(String className) {
        this.className = className;
    }

    // 设置属性。
    public Object setProperty(Object propertyName, Object propertyValue) {
        // 检查属性名是否为空。
        notNull(propertyName, "Property name is null");
        return properties.put(propertyName, propertyValue);
    }

    // 设置枚举名称属性。
    public String setEnumNameProperty(String name) {
        // 检查是否为枚举类型。
        if (isEnumType()) {
            Object result = setProperty(ENUM_PROPERTY_NAME, name);
            return result == null ? null : result.toString();
        }
        throw new IllegalStateException("The instance is not a enum wrapper");
    }

    // 获取枚举属性名。
    public String getEnumPropertyName() {
        // 检查是否为枚举类型。
        if (isEnumType()) {
            Object result = getProperty(ENUM_PROPERTY_NAME);
            return result == null ? null : result.toString();
        }
        throw new IllegalStateException("The instance is not a enum wrapper");
    }

    // 设置类名属性。
    public String setClassNameProperty(String name) {
        // 检查是否为类类型。
        if (isClassType()) {
            Object result = setProperty(CLASS_PROPERTY_NAME, name);
            return result == null ? null : result.toString();
        }
        throw new IllegalStateException("The instance is not a class wrapper");
    }

    // 获取类名属性。
    public String getClassNameProperty() {
        // 检查是否为类类型。
        if (isClassType()) {
            Object result = getProperty(CLASS_PROPERTY_NAME);
            return result == null ? null : result.toString();
        }
        throw new IllegalStateException("The instance is not a class wrapper");
    }

    // 设置基本类型属性。
    public Object setPrimitiveProperty(Object primitiveValue) {
        // 检查是否为基本类型。
        if (isPrimitiveType()) {
            return setProperty(PRIMITIVE_PROPERTY_VALUE, primitiveValue);
        }
        throw new IllegalStateException("The instance is not a primitive type wrapper");
    }

    // 获取基本类型属性。
    public Object getPrimitiveProperty() {
        // 检查是否为基本类型。
        if (isPrimitiveType()) {
            return getProperty(PRIMITIVE_PROPERTY_VALUE);
        }
        throw new IllegalStateException("The instance is not a primitive type wrapper");
    }

    // 获取属性。
    public Object getProperty(Object propertyName) {
        // 检查属性名是否为空。
        notNull(propertyName, "Property name is null");
        return properties.get(propertyName);
    }

    // 检查是否包含指定的属性。
    public boolean containsProperty(Object propertyName) {
        // 检查属性名是否为空。
        notNull(propertyName, "Property name is null");
        return properties.containsKey(propertyName);
    }

    // 返回属性迭代器。
    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return properties.entrySet().iterator();
    }

    // 获取属性数量。
    public int propertySize() {
        return properties.size();
    }

    // 检查类型是否有效。
    private boolean isValidType(int type) {
        return TYPE_MIN <= type && type <= TYPE_MAX;
    }

    // 检查对象是否为空。
    private void notNull(Object obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    // 检查字符串是否为空或仅包含空白字符。
    private void notEmpty(String string, String message) {
        if (isEmpty(string)) {
            throw new IllegalArgumentException(message);
        }
    }

    // 检查字符串是否为空或仅包含空白字符。
    private boolean isEmpty(String string) {
        return string == null || "".equals(string.trim());
    }
}
