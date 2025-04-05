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

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.LogHelper;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.SerializeClassChecker;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

// 定义一个工具类，用于JavaBean的序列化和反序列化
public final class JavaBeanSerializeUtil {

    // 获取Logger实例，用于记录日志
    private static final Logger logger = LoggerFactory.getLogger(JavaBeanSerializeUtil.class);
    // 初始化一个Map来存储基本类型及其对应的Class对象
    private static final Map<String, Class<?>> TYPES = new HashMap<String, Class<?>>();
    // 数组类型的前缀
    private static final String ARRAY_PREFIX = "[";
    // 引用类型的前缀
    private static final String REFERENCE_TYPE_PREFIX = "L";
    // 引用类型的后缀
    private static final String REFERENCE_TYPE_SUFFIX = ";";

    // 静态代码块，初始化基本类型到Class对象的映射
    static {
        // 将boolean的基本类型名映射到其Class对象
        TYPES.put(boolean.class.getName(), boolean.class);
        // 将byte的基本类型名映射到其Class对象
        TYPES.put(byte.class.getName(), byte.class);
        // 将short的基本类型名映射到其Class对象
        TYPES.put(short.class.getName(), short.class);
        // 将int的基本类型名映射到其Class对象
        TYPES.put(int.class.getName(), int.class);
        // 将long的基本类型名映射到其Class对象
        TYPES.put(long.class.getName(), long.class);
        // 将float的基本类型名映射到其Class对象
        TYPES.put(float.class.getName(), float.class);
        // 将double的基本类型名映射到其Class对象
        TYPES.put(double.class.getName(), double.class);
        // 将void的基本类型名映射到其Class对象
        TYPES.put(void.class.getName(), void.class);
        // 将字符'Z'映射为boolean类型
        TYPES.put("Z", boolean.class);
        // 将字符'B'映射为byte类型
        TYPES.put("B", byte.class);
        // 将字符'C'映射为char类型
        TYPES.put("C", char.class);
        // 将字符'D'映射为double类型
        TYPES.put("D", double.class);
        // 将字符'F'映射为float类型
        TYPES.put("F", float.class);
        // 将字符'I'映射为int类型
        TYPES.put("I", int.class);
        // 将字符'J'映射为long类型
        TYPES.put("J", long.class);
        // 将字符'S'映射为short类型
        TYPES.put("S", short.class);
    }

    // 私有构造函数，防止实例化
    private JavaBeanSerializeUtil() {
    }

    // 序列化对象，默认使用字段访问方式
    public static JavaBeanDescriptor serialize(Object obj) {
        return serialize(obj, JavaBeanAccessor.FIELD);
    }

    // 根据指定的访问器策略序列化对象
    public static JavaBeanDescriptor serialize(Object obj, JavaBeanAccessor accessor) {
        // 如果对象为空，则返回null
        if (obj == null) {
            return null;
        }
        // 如果对象已经是JavaBeanDescriptor，则直接返回
        if (obj instanceof JavaBeanDescriptor) {
            return (JavaBeanDescriptor) obj;
        }
        // 创建一个IdentityHashMap作为缓存
        IdentityHashMap<Object, JavaBeanDescriptor> cache = new IdentityHashMap<Object, JavaBeanDescriptor>();
        // 递归创建描述符，并填充缓存
        return createDescriptorIfAbsent(obj, accessor, cache);
    }

    // 创建描述符，根据类的不同类型设定不同的属性
    private static JavaBeanDescriptor createDescriptorForSerialize(Class<?> cl) {
        // 如果是枚举类型
        if (cl.isEnum()) {
            return new JavaBeanDescriptor(cl.getName(), JavaBeanDescriptor.TYPE_ENUM);
        }

        // 如果是数组类型
        if (cl.isArray()) {
            return new JavaBeanDescriptor(cl.getComponentType().getName(), JavaBeanDescriptor.TYPE_ARRAY);
        }

        // 如果是基本类型
        if (ReflectUtils.isPrimitive(cl)) {
            return new JavaBeanDescriptor(cl.getName(), JavaBeanDescriptor.TYPE_PRIMITIVE);
        }

        // 如果是Class类型
        if (Class.class.equals(cl)) {
            return new JavaBeanDescriptor(Class.class.getName(), JavaBeanDescriptor.TYPE_CLASS);
        }

        // 如果是可以被Collection赋值的类型
        if (Collection.class.isAssignableFrom(cl)) {
            return new JavaBeanDescriptor(cl.getName(), JavaBeanDescriptor.TYPE_COLLECTION);
        }

        // 如果是可以被Map赋值的类型
        if (Map.class.isAssignableFrom(cl)) {
            return new JavaBeanDescriptor(cl.getName(), JavaBeanDescriptor.TYPE_MAP);
        }

        // 默认视为普通Bean类型
        return new JavaBeanDescriptor(cl.getName(), JavaBeanDescriptor.TYPE_BEAN);
    }

    // 递归创建描述符并填充缓存
    private static JavaBeanDescriptor createDescriptorIfAbsent(Object obj, JavaBeanAccessor accessor,
                                                               IdentityHashMap<Object, JavaBeanDescriptor> cache) {
        // 如果缓存中已经存在该对象的描述符，则直接返回
        if (cache.containsKey(obj)) {
            return cache.get(obj);
        }

        // 如果对象本身已经是JavaBeanDescriptor，则直接转换并返回
        if (obj instanceof JavaBeanDescriptor) {
            return (JavaBeanDescriptor) obj;
        }

        // 创建描述符
        JavaBeanDescriptor result = createDescriptorForSerialize(obj.getClass());
        // 将描述符放入缓存
        cache.put(obj, result);
        // 递归处理内部结构
        serializeInternal(result, obj, accessor, cache);
        // 返回最终生成的描述符
        return result;
    }

    // 内部序列化逻辑实现
    private static void serializeInternal(JavaBeanDescriptor descriptor, Object obj, JavaBeanAccessor accessor,
                                          IdentityHashMap<Object, JavaBeanDescriptor> cache) {
        // 如果对象或描述符为空，则直接返回
        if (obj == null || descriptor == null) {
            return;
        }

        // 如果对象是一个枚举
        if (obj.getClass().isEnum()) {
            // 设置枚举名称属性
            descriptor.setEnumNameProperty(((Enum<?>) obj).name());
        } else if (ReflectUtils.isPrimitive(obj.getClass())) { // 如果对象是基本类型
            // 设置基本类型属性
            descriptor.setPrimitiveProperty(obj);
        } else if (Class.class.equals(obj.getClass())) { // 如果对象是Class类型
            // 设置类名属性
            descriptor.setClassNameProperty(((Class<?>) obj).getName());
        } else if (obj.getClass().isArray()) { // 如果对象是数组
            // 获取数组长度
            int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) { // 遍历数组元素
                // 获取数组中的元素
                Object item = Array.get(obj, i);
                if (item == null) { // 如果元素为空
                    // 设置属性为null
                    descriptor.setProperty(i, null);
                } else { // 如果元素不为空
                    // 递归处理数组元素
                    JavaBeanDescriptor itemDescriptor = createDescriptorIfAbsent(item, accessor, cache);
                    // 设置对应位置的属性
                    descriptor.setProperty(i, itemDescriptor);
                }
            }
        } else if (obj instanceof Collection) { // 如果对象是集合
            // 转换为Collection类型
            Collection collection = (Collection) obj;
            int index = 0; // 初始化索引
            for (Object item : collection) { // 遍历集合中的每个元素
                if (item == null) { // 如果元素为空
                    // 设置属性为null
                    descriptor.setProperty(index++, null);
                } else { // 如果元素不为空
                    // 递归处理集合元素
                    JavaBeanDescriptor itemDescriptor = createDescriptorIfAbsent(item, accessor, cache);
                    // 设置对应位置的属性
                    descriptor.setProperty(index++, itemDescriptor);
                }
            }
        } else if (obj instanceof Map) { // 如果对象是Map
            // 转换为Map类型
            Map map = (Map) obj;
            // 遍历Map中的每个键值对
            map.forEach((key, value) -> {
                // 处理键
                Object keyDescriptor = key == null ? null : createDescriptorIfAbsent(key, accessor, cache);
                // 处理值
                Object valueDescriptor = value == null ? null : createDescriptorIfAbsent(value, accessor, cache);
                // 设置键值对属性
                descriptor.setProperty(keyDescriptor, valueDescriptor);
            }); // 结束遍历Map
        } else { // 其他情况
            if (JavaBeanAccessor.isAccessByMethod(accessor)) { // 如果通过方法访问
                // 获取读取属性的方法
                Map<String, Method> methods = ReflectUtils.getBeanPropertyReadMethods(obj.getClass());
                // 遍历所有方法
                for (Map.Entry<String, Method> entry : methods.entrySet()) {
                    try {
                        // 通过反射调用方法获取属性值
                        Object value = entry.getValue().invoke(obj);
                        if (value == null) { // 如果属性值为空
                            continue; // 跳过
                        }
                        // 递归处理属性值
                        JavaBeanDescriptor valueDescriptor = createDescriptorIfAbsent(value, accessor, cache);
                        // 设置属性
                        descriptor.setProperty(entry.getKey(), valueDescriptor);
                    } catch (Exception e) { // 捕获异常
                        throw new RuntimeException(e.getMessage(), e); // 抛出运行时异常
                    }
                } // 结束遍历方法
            } // 结束if判断

            if (JavaBeanAccessor.isAccessByField(accessor)) { // 如果通过字段访问
                // 获取属性字段
                Map<String, Field> fields = ReflectUtils.getBeanPropertyFields(obj.getClass());
                // 遍历所有字段
                for (Map.Entry<String, Field> entry : fields.entrySet()) {
                    // 如果描述符中已包含该属性
                    if (!descriptor.containsProperty(entry.getKey())) {
                        try {
                            // 通过反射获取字段值
                            Object value = entry.getValue().get(obj);
                            if (value == null) { // 如果字段值为空
                                continue; // 跳过
                            }
                            // 递归处理字段值
                            JavaBeanDescriptor valueDescriptor = createDescriptorIfAbsent(value, accessor, cache);
                            // 设置属性
                            descriptor.setProperty(entry.getKey(), valueDescriptor);
                        } catch (Exception e) { // 捕获异常
                            throw new RuntimeException(e.getMessage(), e); // 抛出运行时异常
                        }
                    }
                } // 结束遍历字段
            } // 结束if判断
        } // 结束else
    } // 结束方法serializeInternal

    // 反序列化对象，使用当前线程的类加载器
    public static Object deserialize(JavaBeanDescriptor beanDescriptor) {
        return deserialize(
            beanDescriptor,
            Thread.currentThread().getContextClassLoader());
    }

    // 使用指定的类加载器反序列化对象
    public static Object deserialize(JavaBeanDescriptor beanDescriptor, ClassLoader loader) {
        // 如果beanDescriptor为空，则返回null
        if (beanDescriptor == null) {
            return null;
        }
        // 创建一个IdentityHashMap作为缓存
        IdentityHashMap<JavaBeanDescriptor, Object> cache = new IdentityHashMap<JavaBeanDescriptor, Object>();
        // 实例化对象
        Object result = instantiateForDeserialize(beanDescriptor, loader, cache);
        // 递归反序列化内部结构
        deserializeInternal(result, beanDescriptor, loader, cache);
        // 返回最终的对象
        return result;
    }

    // 递归反序列化的内部逻辑实现
    private static void deserializeInternal(Object result, JavaBeanDescriptor beanDescriptor, ClassLoader loader,
                                            IdentityHashMap<JavaBeanDescriptor, Object> cache) {
        // 如果是枚举类型、Class类型或基本类型，则直接返回
        if (beanDescriptor.isEnumType() || beanDescriptor.isClassType() || beanDescriptor.isPrimitiveType()) {
            return;
        }

        // 如果是数组类型
        if (beanDescriptor.isArrayType()) {
            int index = 0; // 初始化索引
            // 遍历beanDescriptor中的每个条目
            for (Map.Entry<Object, Object> entry : beanDescriptor) {
                // 获取条目的值
                Object item = entry.getValue();
                if (item instanceof JavaBeanDescriptor) { // 如果值是JavaBeanDescriptor
                    // 递归处理条目值
                    JavaBeanDescriptor itemDescriptor = (JavaBeanDescriptor) entry.getValue();
                    item = instantiateForDeserialize(itemDescriptor, loader, cache);
                    // 继续递归反序列化
                    deserializeInternal(item, itemDescriptor, loader, cache);
                }
                // 设置数组元素
                Array.set(result, index++, item);
            }
        } else if (beanDescriptor.isCollectionType()) { // 如果是集合类型
            // 转换为Collection类型
            Collection collection = (Collection) result;
            // 遍历beanDescriptor中的每个条目
            for (Map.Entry<Object, Object> entry : beanDescriptor) {
                // 获取条目的值
                Object item = entry.getValue();
                if (item instanceof JavaBeanDescriptor) { // 如果值是JavaBeanDescriptor
                    // 递归处理条目值
                    JavaBeanDescriptor itemDescriptor = (JavaBeanDescriptor) entry.getValue();
                    item = instantiateForDeserialize(itemDescriptor, loader, cache);
                    // 继续递归反序列化
                    deserializeInternal(item, itemDescriptor, loader, cache);
                }
                // 添加到集合中
                collection.add(item);
            }
        } else if (beanDescriptor.isMapType()) { // 如果是Map类型
            // 转换为Map类型
            Map map = (Map) result;
            // 遍历beanDescriptor中的每个条目
            for (Map.Entry<Object, Object> entry : beanDescriptor) {
                // 获取条目的键
                Object key = entry.getKey();
                // 获取条目的值
                Object value = entry.getValue();
                if (key instanceof JavaBeanDescriptor) { // 如果键是JavaBeanDescriptor
                    // 递归处理键
                    JavaBeanDescriptor keyDescriptor = (JavaBeanDescriptor) entry.getKey();
                    key = instantiateForDeserialize(keyDescriptor, loader, cache);
                    // 继续递归反序列化
                    deserializeInternal(key, keyDescriptor, loader, cache);
                }
                if (value instanceof JavaBeanDescriptor) { // 如果值是JavaBeanDescriptor
                    // 递归处理值
                    JavaBeanDescriptor valueDescriptor = (JavaBeanDescriptor) entry.getValue();
                    value = instantiateForDeserialize(valueDescriptor, loader, cache);
                    // 继续递归反序列化
                    deserializeInternal(value, valueDescriptor, loader, cache);
                }
                // 将键值对添加到Map中
                map.put(key, value);
            }
        } else if (beanDescriptor.isBeanType()) { // 如果是普通Bean类型
            // 遍历beanDescriptor中的每个条目
            for (Map.Entry<Object, Object> entry : beanDescriptor) {
                // 获取条目的键（属性名）
                String property = entry.getKey().toString();
                // 获取条目的值
                Object value = entry.getValue();
                if (value == null) { // 如果值为空
                    continue; // 跳过
                }

                if (value instanceof JavaBeanDescriptor) { // 如果值是JavaBeanDescriptor
                    // 递归处理值
                    JavaBeanDescriptor valueDescriptor = (JavaBeanDescriptor) entry.getValue();
                    value = instantiateForDeserialize(valueDescriptor, loader, cache);
                    // 继续递归反序列化
                    deserializeInternal(value, valueDescriptor, loader, cache);
                }

                // 获取设置属性的方法
                Method method = getSetterMethod(result.getClass(), property, value.getClass());
                boolean setByMethod = false; // 标记是否通过方法设置了属性
                try {
                    // 如果找到了设置方法
                    if (method != null) {
                        // 通过反射调用方法设置属性
                        method.invoke(result, value);
                        setByMethod = true; // 标记已通过方法设置了属性
                    }
                } catch (Exception e) { // 捕获异常
                    LogHelper.warn(logger, "Failed to set property through method " + method, e); // 记录警告日志
                }

                // 如果未通过方法设置属性
                if (!setByMethod) {
                    try {
                        // 获取字段
                        Field field = result.getClass().getField(property);
                        if (field != null) { // 如果字段不为空
                            // 通过反射设置字段值
                            field.set(result, value);
                        }
                    } catch (NoSuchFieldException | IllegalAccessException e1) { // 捕获异常
                        LogHelper.warn(logger, "Failed to set field value", e1); // 记录警告日志
                    }
                }
            }
        } else { // 如果是其他类型
            // 抛出非法参数异常
            throw new IllegalArgumentException("Unsupported type " +
                beanDescriptor.getClassName() +
                ":" + beanDescriptor.getType());
        }
    }

    // 获取setter方法
    private static Method getSetterMethod(Class<?> cls, String property, Class<?> valueCls) {
        // 构建setter方法名
        String name = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
        Method method = null; // 初始化方法变量
        try {
            // 尝试获取方法
            method = cls.getMethod(name, valueCls);
        } catch (NoSuchMethodException e) { // 如果找不到方法
            // 遍历类的所有方法
            for (Method m : cls.getMethods()) {
                // 如果是setter方法且方法名匹配
                if (ReflectUtils.isBeanPropertyWriteMethod(m)
                    && m.getName().equals(name)) {
                    method = m; // 找到了方法
                }
            }
        }
        if (method != null) { // 如果找到了方法
            // 设置方法可访问
            method.setAccessible(true);
        }
        // 返回找到的方法
        return method;
    }

    // 实例化给定类的新实例
    private static Object instantiate(Class<?> cl) throws Exception {
        // 获取类的所有构造器
        Constructor<?>[] constructors = cl.getDeclaredConstructors();
        Constructor<?> constructor = null; // 初始化构造器变量
        int argc = Integer.MAX_VALUE; // 初始化参数数量
        for (Constructor<?> c : constructors) { // 遍历所有构造器
            // 如果当前构造器的参数数量少于已知最小参数数量
            if (c.getParameterTypes().length < argc) {
                argc = c.getParameterTypes().length; // 更新最小参数数量
                constructor = c; // 更新构造器
            }
        }

        if (constructor != null) { // 如果找到了合适的构造器
            // 获取构造器的参数类型
            Class<?>[] paramTypes = constructor.getParameterTypes();
            // 创建参数数组
            Object[] constructorArgs = new Object[paramTypes.length];
            // 填充参数数组
            for (int i = 0; i < constructorArgs.length; i++) {
                constructorArgs[i] = getConstructorArg(paramTypes[i]);
            }
            try {
                // 设置构造器可访问
                constructor.setAccessible(true);
                // 通过构造器创建新实例
                return constructor.newInstance(constructorArgs);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) { // 捕获异常
                LogHelper.warn(logger, e.getMessage(), e); // 记录警告日志
            }
        }

        // 如果没有找到合适的构造器，则尝试使用默认构造器
        return cl.getDeclaredConstructor().newInstance();
    }

    // 获取构造器参数的默认值
    public static Object getConstructorArg(Class<?> cl) {
        // 如果是boolean或Boolean类型
        if (boolean.class.equals(cl) || Boolean.class.equals(cl)) {
            return Boolean.FALSE; // 返回false
        }

        // 如果是byte或Byte类型
        if (byte.class.equals(cl) || Byte.class.equals(cl)) {
            return (byte) 0; // 返回0
        }

        // 如果是short或Short类型
        if (short.class.equals(cl) || Short.class.equals(cl)) {
            return (short) 0; // 返回0
        }

        // 如果是int或Integer类型
        if (int.class.equals(cl) || Integer.class.equals(cl)) {
            return 0; // 返回0
        }

        // 如果是long或Long类型
        if (long.class.equals(cl) || Long.class.equals(cl)) {
            return 0L; // 返回0L
        }

        // 如果是float或Float类型
        if (float.class.equals(cl) || Float.class.equals(cl)) {
            return (float) 0; // 返回0.0f
        }

        // 如果是double或Double类型
        if (double.class.equals(cl) || Double.class.equals(cl)) {
            return (double) 0; // 返回0.0
        }

        // 如果是char或Character类型
        if (char.class.equals(cl) || Character.class.equals(cl)) {
            return (char) 0; // 返回0
        }
        // 如果是其他类型
        return null; // 返回null
    }

    // 为反序列化创建对象实例
    private static Object instantiateForDeserialize(JavaBeanDescriptor beanDescriptor, ClassLoader loader,
                                                    IdentityHashMap<JavaBeanDescriptor, Object> cache) {
        // 如果缓存中已经存在该beanDescriptor的实例
        if (cache.containsKey(beanDescriptor)) {
            return cache.get(beanDescriptor);
        }

        // 如果是Class类型
        if (beanDescriptor.isClassType()) {
            try {
                // 通过类名获取Class对象
                return name2Class(loader, beanDescriptor.getClassNameProperty());
            } catch (ClassNotFoundException e) { // 如果找不到类
                throw new RuntimeException(e.getMessage(), e); // 抛出运行时异常
            }
        }

        // 如果是枚举类型
        if (beanDescriptor.isEnumType()) {
            try {
                // 通过类名获取枚举类
                Class<?> enumType = name2Class(loader, beanDescriptor.getClassName());
                // 获取valueOf方法
                Method method = getEnumValueOfMethod(enumType);
                // 通过反射调用valueOf方法获取枚举实例
                return method.invoke(null, enumType, beanDescriptor.getEnumPropertyName());
            } catch (Exception e) { // 捕获异常
                throw new RuntimeException(e.getMessage(), e); // 抛出运行时异常
            }
        }

        // 如果是基本类型
        if (beanDescriptor.isPrimitiveType()) {
            // 返回基本类型值
            return beanDescriptor.getPrimitiveProperty();
        }

        Object result; // 初始化结果变量
        // 如果是数组类型
        if (beanDescriptor.isArrayType()) {
            Class<?> componentType; // 初始化组件类型
            try {
                // 通过类名获取组件类型
                componentType = name2Class(loader, beanDescriptor.getClassName());
            } catch (ClassNotFoundException e) { // 如果找不到类
                throw new RuntimeException(e.getMessage(), e); // 抛出运行时异常
            }
            // 创建数组实例
            result = Array.newInstance(componentType, beanDescriptor.propertySize());
            // 将实例放入缓存
            cache.put(beanDescriptor, result);
        } else { // 如果是其他类型
            try {
                // 通过类名获取类
                Class<?> cl = name2Class(loader, beanDescriptor.getClassName());
                // 实例化对象
                result = instantiate(cl);
                // 将实例放入缓存
                cache.put(beanDescriptor, result);
            } catch (Exception e) { // 捕获异常
                throw new RuntimeException(e.getMessage(), e); // 抛出运行时异常
            }
        }

        // 返回最终的实例
        return result;
    }

    // 将类名字符串转换为Class对象
    /**
     * Transform the Class.forName String to Class Object.
     *
     * @param name Class.getName()
     * @return Class
     * @throws ClassNotFoundException Class.forName
     */
    public static Class<?> name2Class(ClassLoader loader, String name) throws ClassNotFoundException {
        // 如果预定义的类型映射中包含该类名
        if (TYPES.containsKey(name)) {
            return TYPES.get(name); // 直接返回对应的Class对象
        }
        // 如果是数组类型
        if (isArray(name)) {
            int dimension = 0; // 初始化维度
            // 循环去除数组前缀
            while (isArray(name)) {
                ++dimension; // 增加维度
                name = name.substring(1); // 去除数组前缀
            }
            // 递归获取组件类型
            Class type = name2Class(loader, name);
            // 创建维度数组
            int[] dimensions = new int[dimension];
            // 初始化维度数组
            for (int i = 0; i < dimension; i++) {
                dimensions[i] = 0;
            }
            // 创建多维数组类
            return Array.newInstance(type, dimensions).getClass();
        }
        // 如果是引用类型
        if (isReferenceType(name)) {
            // 去除引用类型前后缀
            name = name.substring(1, name.length() - 1);
        }
        // 验证类名是否合法
        SerializeClassChecker.getInstance().validateClass(name);
        // 通过类名获取Class对象
        return Class.forName(name, false, loader);
    }

    // 判断是否为数组类型
    private static boolean isArray(String type) {
        // 如果类型不为空且以数组前缀开头
        return type != null && type.startsWith(ARRAY_PREFIX);
    }

    // 判断是否为引用类型
    private static boolean isReferenceType(String type) {
        // 如果类型不为空且以前缀开头且以后缀结尾
        return type != null
            && type.startsWith(REFERENCE_TYPE_PREFIX)
            && type.endsWith(REFERENCE_TYPE_SUFFIX);
    }

    // 获取枚举类型的valueOf方法
    private static Method getEnumValueOfMethod(Class cl) throws NoSuchMethodException {
        // 获取valueOf方法
        return cl.getMethod("valueOf", Class.class, String.class);
    }

}
