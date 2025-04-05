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
package org.apache.dubbo.common.compiler.support;

import org.apache.dubbo.common.utils.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * ClassUtils. (Tool, Static, ThreadSafe)
 */
// 定义一个工具类ClassUtils
public class ClassUtils {

    // 定义类文件扩展名常量
    public static final String CLASS_EXTENSION = ".class";

    // 定义Java文件扩展名常量
    public static final String JAVA_EXTENSION = ".java";

    // 定义JIT编译限制大小
    private static final int JIT_LIMIT = 5 * 1024;

    // 私有构造函数，防止实例化
    private ClassUtils() {
    }

    // 根据类名创建一个新的实例
    public static Object newInstance(String name) {
        try {
            // 获取指定类的默认构造函数并创建实例
            return forName(name).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // 抛出异常状态
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // 根据包名和类名加载类
    public static Class<?> forName(String[] packages, String className) {
        try {
            // 尝试直接加载类
            return classForName(className);
        } catch (ClassNotFoundException e) {
            // 如果提供了包名数组且不为空
            if (packages != null && packages.length > 0) {
                for (String pkg : packages) {
                    try {
                        // 尝试使用包名和类名加载类
                        return classForName(pkg + "." + className);
                    } catch (ClassNotFoundException ignore) {
                        // 忽略异常
                    }
                }
            }
            // 抛出异常状态
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // 根据类名加载类
    public static Class<?> forName(String className) {
        try {
            // 尝试直接加载类
            return classForName(className);
        } catch (ClassNotFoundException e) {
            // 抛出异常状态
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // 根据类名加载类，处理基本类型和数组类型
    public static Class<?> classForName(String className) throws ClassNotFoundException {
        switch (className) {
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "short":
                return short.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "boolean[]":
                return boolean[].class;
            case "byte[]":
                return byte[].class;
            case "char[]":
                return char[].class;
            case "short[]":
                return short[].class;
            case "int[]":
                return int[].class;
            case "long[]":
                return long[].class;
            case "float[]":
                return float[].class;
            case "double[]":
                return double[].class;
            default:
        }
        try {
            // 尝试加载数组类型
            return arrayForName(className);
        } catch (ClassNotFoundException e) {
            // 如果类名中没有点号，尝试从java.lang包中加载
            if (className.indexOf('.') == -1) {
                try {
                    return arrayForName("java.lang." + className);
                } catch (ClassNotFoundException ignore) {
                    // 忽略异常，抛出原始异常
                }
            }
            throw e;
        }
    }

    // 根据类名加载数组类型
    private static Class<?> arrayForName(String className) throws ClassNotFoundException {
        return Class.forName(className.endsWith("[]")
            ? "[L" + className.substring(0, className.length() - 2) + ";"
            : className, true, Thread.currentThread().getContextClassLoader());
    }

    // 获取包装类
    public static Class<?> getBoxedClass(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        } else if (type == char.class) {
            return Character.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == int.class) {
            return Integer.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == double.class) {
            return Double.class;
        } else {
            return type;
        }
    }

    // 将基本类型值装箱为Boolean
    public static Boolean boxed(boolean v) {
        return Boolean.valueOf(v);
    }

    // 将基本类型值装箱为Character
    public static Character boxed(char v) {
        return Character.valueOf(v);
    }

    // 将基本类型值装箱为Byte
    public static Byte boxed(byte v) {
        return Byte.valueOf(v);
    }

    // 将基本类型值装箱为Short
    public static Short boxed(short v) {
        return Short.valueOf(v);
    }

    // 将基本类型值装箱为Integer
    public static Integer boxed(int v) {
        return Integer.valueOf(v);
    }

    // 将基本类型值装箱为Long
    public static Long boxed(long v) {
        return Long.valueOf(v);
    }

    // 将基本类型值装箱为Float
    public static Float boxed(float v) {
        return Float.valueOf(v);
    }

    // 将基本类型值装箱为Double
    public static Double boxed(double v) {
        return Double.valueOf(v);
    }

    // 返回对象本身
    public static Object boxed(Object v) {
        return v;
    }

    // 将包装类型值拆箱为基本类型
    public static boolean unboxed(Boolean v) {
        return v == null ? false : v.booleanValue();
    }

    // 将包装类型值拆箱为基本类型
    public static char unboxed(Character v) {
        return v == null ? '\0' : v.charValue();
    }

    // 将包装类型值拆箱为基本类型
    public static byte unboxed(Byte v) {
        return v == null ? 0 : v.byteValue();
    }

    // 将包装类型值拆箱为基本类型
    public static short unboxed(Short v) {
        return v == null ? 0 : v.shortValue();
    }

    // 将包装类型值拆箱为基本类型
    public static int unboxed(Integer v) {
        return v == null ? 0 : v.intValue();
    }

    // 将包装类型值拆箱为基本类型
    public static long unboxed(Long v) {
        return v == null ? 0 : v.longValue();
    }

    // 将包装类型值拆箱为基本类型
    public static float unboxed(Float v) {
        return v == null ? 0 : v.floatValue();
    }

    // 将包装类型值拆箱为基本类型
    public static double unboxed(Double v) {
        return v == null ? 0 : v.doubleValue();
    }

    // 返回对象本身
    public static Object unboxed(Object v) {
        return v;
    }

    // 判断对象是否非空
    public static boolean isNotEmpty(Object object) {
        return getSize(object) > 0;
    }

    // 获取对象的大小
    public static int getSize(Object object) {
        if (object == null) {
            return 0;
        }
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).size();
        } else if (object instanceof Map<?, ?>) {
            return ((Map<?, ?>) object).size();
        } else if (object.getClass().isArray()) {
            return Array.getLength(object);
        } else {
            return -1;
        }
    }

    // 将字符串转换为URI
    public static URI toURI(String name) {
        try {
            return new URI(name);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // 获取泛型类
    public static Class<?> getGenericClass(Class<?> cls) {
        return getGenericClass(cls, 0);
    }

    // 获取泛型类
    public static Class<?> getGenericClass(Class<?> cls, int i) {
        try {
            ParameterizedType parameterizedType = ((ParameterizedType) cls.getGenericInterfaces()[0]);
            Object genericClass = parameterizedType.getActualTypeArguments()[i];
            if (genericClass instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) genericClass).getRawType();
            } else if (genericClass instanceof GenericArrayType) {
                Type type = ((GenericArrayType) genericClass).getGenericComponentType();
                if (type instanceof TypeVariable) {
                    return type.getClass();
                }
                return (((GenericArrayType) genericClass).getGenericComponentType() instanceof Class<?>)
                    ? (Class<?>) ((GenericArrayType) genericClass).getGenericComponentType()
                    : ((GenericArrayType) genericClass).getGenericComponentType().getClass();
            } else if (genericClass != null) {
                if (genericClass instanceof TypeVariable) {
                    return genericClass.getClass();
                }
                return (Class<?>) genericClass;
            }
        } catch (Throwable e) {

        }
        if (cls.getSuperclass() != null) {
            return getGenericClass(cls.getSuperclass(), i);
        } else {
            throw new IllegalArgumentException(cls.getName() + " generic type undefined!");
        }
    }

    // 判断Java版本是否早于1.5
    public static boolean isBeforeJava5(String javaVersion) {
        return (StringUtils.isEmpty(javaVersion) || "1.0".equals(javaVersion)
            || "1.1".equals(javaVersion) || "1.2".equals(javaVersion)
            || "1.3".equals(javaVersion) || "1.4".equals(javaVersion));
    }

    // 判断Java版本是否早于1.6
    public static boolean isBeforeJava6(String javaVersion) {
        return isBeforeJava5(javaVersion) || "1.5".equals(javaVersion);
    }

    // 将异常转换为字符串
    public static String toString(Throwable e) {
        StringWriter w = new StringWriter();
        PrintWriter p = new PrintWriter(w);
        p.print(e.getClass().getName() + ": ");
        if (e.getMessage() != null) {
            p.print(e.getMessage() + "\n");
        }
        p.println();
        try {
            e.printStackTrace(p);
            return w.toString();
        } finally {
            p.close();
        }
    }

    // 检查字节码大小
    public static void checkBytecode(String name, byte[] bytecode) {
        if (bytecode.length > JIT_LIMIT) {
            System.err.println("The template bytecode too long, may be affect the JIT compiler. template class: " + name);
        }
    }

    // 获取类的大小方法
    public static String getSizeMethod(Class<?> cls) {
        try {
            return cls.getMethod("size", new Class<?>[0]).getName() + "()";
        } catch (NoSuchMethodException e) {
            try {
                return cls.getMethod("length", new Class<?>[0]).getName() + "()";
            } catch (NoSuchMethodException e2) {
                try {
                    return cls.getMethod("getSize", new Class<?>[0]).getName() + "()";
                } catch (NoSuchMethodException e3) {
                    try {
                        return cls.getMethod("getLength", new Class<?>[0]).getName() + "()";
                    } catch (NoSuchMethodException e4) {
                        return null;
                    }
                }
            }
        }
    }

    // 获取方法名称
    public static String getMethodName(Method method, Class<?>[] parameterClasses, String rightCode) {
        if (method.getParameterTypes().length > parameterClasses.length) {
            Class<?>[] types = method.getParameterTypes();
            StringBuilder buf = new StringBuilder(rightCode);
            for (int i = parameterClasses.length; i < types.length; i++) {
                if (buf.length() > 0) {
                    buf.append(',');
                }
                Class<?> type = types[i];
                String def;
                if (type == boolean.class) {
                    def = "false";
                } else if (type == char.class) {
                    def = "\'\\0\'";
                } else if (type == byte.class
                    || type == short.class
                    || type == int.class
                    || type == long.class
                    || type == float.class
                    || type == double.class) {
                    def = "0";
                } else {
                    def = "null";
                }
                buf.append(def);
            }
        }
        return method.getName() + "(" + rightCode + ")";
    }

    // 查找方法
    public static Method searchMethod(Class<?> currentClass, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        if (currentClass == null) {
            throw new NoSuchMethodException("class == null");
        }
        try {
            return currentClass.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            for (Method method : currentClass.getMethods()) {
                if (method.getName().equals(name)
                    && parameterTypes.length == method.getParameterTypes().length
                    && Modifier.isPublic(method.getModifiers())) {
                    if (parameterTypes.length > 0) {
                        Class<?>[] types = method.getParameterTypes();
                        boolean match = true;
                        for (int i = 0; i < parameterTypes.length; i++) {
                            if (!types[i].isAssignableFrom(parameterTypes[i])) {
                                match = false;
                                break;
                            }
                        }
                        if (!match) {
                            continue;
                        }
                    }
                    return method;
                }
            }
            throw e;
        }
    }

    // 获取初始化代码
    public static String getInitCode(Class<?> type) {
        if (byte.class.equals(type)
            || short.class.equals(type)
            || int.class.equals(type)
            || long.class.equals(type)
            || float.class.equals(type)
            || double.class.equals(type)) {
            return "0";
        } else if (char.class.equals(type)) {
            return "'\\0'";
        } else if (boolean.class.equals(type)) {
            return "false";
        } else {
            return "null";
        }
    }

    // 将Map.Entry数组转换为Map
    public static <K, V> Map<K, V> toMap(Map.Entry<K, V>[] entries) {
        Map<K, V> map = new HashMap<K, V>();
        if (entries != null && entries.length > 0) {
            for (Map.Entry<K, V> entry : entries) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    /**
     * 从完全限定类名中获取简单类名
     */
    public static String getSimpleClassName(String qualifiedName) {
        if (null == qualifiedName) {
            return null;
        }
        int i = qualifiedName.lastIndexOf('.');
        return i < 0 ? qualifiedName : qualifiedName.substring(i + 1);
    }

}
