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
package org.apache.dubbo.common.config;

import java.util.NoSuchElementException;

import static org.apache.dubbo.common.config.ConfigurationUtils.isEmptyValue;

/**
 * Configuration interface, to fetch the value for the specified key.
 */
public interface Configuration {
    /**
     * 获取与给定配置键关联的字符串。
     *
     * @param key 配置键。
     * @return 关联的字符串。
     */
    default String getString(String key) {
        return convert(String.class, key, null);
    }

    /**
     * 获取与给定配置键关联的字符串。
     * 如果键没有映射到现有对象，则返回默认值。
     *
     * @param key          配置键。
     * @param defaultValue 默认值。
     * @return 如果找到键并具有有效格式，则返回关联的字符串，否则返回默认值。
     */
    default String getString(String key, String defaultValue) {
        return convert(String.class, key, defaultValue);
    }

    // 获取与给定配置键关联的整数。
    default int getInt(String key) {
        Integer i = this.getInteger(key, (Integer) null);
        if (i != null) {
            return i;
        } else {
            throw new NoSuchElementException('\'' + key + "' doesn't map to an existing object");
        }
    }

    // 获取与给定配置键关联的整数。
    // 如果键没有映射到现有对象，则返回默认值。
    default int getInt(String key, int defaultValue) {
        Integer i = this.getInteger(key, (Integer) null);
        return i == null ? defaultValue : i;
    }

    // 获取与给定配置键关联的整数对象。
    default Integer getInteger(String key, Integer defaultValue) {
        try {
            return convert(Integer.class, key, defaultValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException('\'' + key + "' doesn't map to a Integer object", e);
        }
    }

    // 获取与给定配置键关联的布尔值。
    default boolean getBoolean(String key) {
        Boolean b = this.getBoolean(key, null);
        if (b != null) {
            return b;
        } else {
            throw new NoSuchElementException('\'' + key + "' doesn't map to an existing object");
        }
    }

    // 获取与给定配置键关联的布尔值。
    // 如果键没有映射到现有对象，则返回默认值。
    default boolean getBoolean(String key, boolean defaultValue) {
        return this.getBoolean(key, toBooleanObject(defaultValue));
    }

    // 获取与给定配置键关联的布尔对象。
    default Boolean getBoolean(String key, Boolean defaultValue) {
        try {
            return convert(Boolean.class, key, defaultValue);
        } catch (Exception e) {
            throw new IllegalStateException("Try to get " + '\'' + key + "' failed, maybe because this key doesn't map to a Boolean object", e);
        }
    }

    /**
     * 从配置中获取属性。这是检索属性值的最基本的方法。
     * 在典型的 {@code Configuration} 接口实现中，其他获取方法（返回特定数据类型）将内部使用此方法。
     * 在这个级别上，变量替换尚未执行。返回的对象是传递的键的属性值的内部表示。
     * 它由 {@code Configuration} 对象拥有。因此，调用者不应修改此对象。
     * 不能保证此对象会随着时间保持不变（即对配置的进一步更新操作可能会更改其内部状态）。
     *
     * @param key 要检索的属性
     * @return 此配置映射到指定键的值，如果没有映射则返回 null。
     */
    default Object getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * 从配置中获取属性。如果配置不包含指定键的映射，则返回默认值。
     *
     * @param key property to retrieve
     * @param defaultValue default value
     * @return the value to which this configuration maps the specified key, or default value if the configuration
     * contains no mapping for this key.
     */
    default Object getProperty(String key, Object defaultValue) {
        Object value = getInternalProperty(key);
        return value != null ? value : defaultValue;
    }

    // 获取内部属性
    Object getInternalProperty(String key);

    /**
     * 检查配置是否包含指定的键。
     *
     * @param key 要测试的键
     * @return 如果配置包含此键的值，则返回 {@code true}，否则返回 {@code false}
     */
    default boolean containsKey(String key) {
        return !isEmptyValue(getProperty(key));
    }

    // 将配置中的值转换为指定类型的对象
    default <T> T convert(Class<T> cls, String key, T defaultValue) {
        // 目前我们只处理字符串属性
        String value = (String) getProperty(key);

        if (value == null) {
            return defaultValue;
        }

        Object obj = value;
        if (cls.isInstance(value)) {
            return cls.cast(value);
        }

        if (Boolean.class.equals(cls) || Boolean.TYPE.equals(cls)) {
            obj = Boolean.valueOf(value);
        } else if (Number.class.isAssignableFrom(cls) || cls.isPrimitive()) {
            if (Integer.class.equals(cls) || Integer.TYPE.equals(cls)) {
                obj = Integer.valueOf(value);
            } else if (Long.class.equals(cls) || Long.TYPE.equals(cls)) {
                obj = Long.valueOf(value);
            } else if (Byte.class.equals(cls) || Byte.TYPE.equals(cls)) {
                obj = Byte.valueOf(value);
            } else if (Short.class.equals(cls) || Short.TYPE.equals(cls)) {
                obj = Short.valueOf(value);
            } else if (Float.class.equals(cls) || Float.TYPE.equals(cls)) {
                obj = Float.valueOf(value);
            } else if (Double.class.equals(cls) || Double.TYPE.equals(cls)) {
                obj = Double.valueOf(value);
            }
        } else if (cls.isEnum()) {
            obj = Enum.valueOf(cls.asSubclass(Enum.class), value);
        }

        return cls.cast(obj);
    }

    // 将布尔值转换为布尔对象
    static Boolean toBooleanObject(boolean bool) {
        return bool ? Boolean.TRUE : Boolean.FALSE;
    }
}
