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
package org.apache.dubbo.common.bytecode;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClassGenerator
 */
public final class ClassGenerator {

    // 用于生成类名的原子计数器
    private static final AtomicLong CLASS_NAME_COUNTER = new AtomicLong(0);
    // 初始化方法标签
    private static final String SIMPLE_NAME_TAG = "<init>";
    // 用于存储ClassLoader和ClassPool映射关系的Map
    private static final Map<ClassLoader, ClassPool> POOL_MAP = new ConcurrentHashMap<ClassLoader, ClassPool>(); //ClassLoader - ClassPool
    // 当前使用的ClassPool实例
    private ClassPool mPool;
    // 当前正在构造的CtClass对象
    private CtClass mCtc;
    // 生成的类名称
    private String mClassName;
    // 超类名称
    private String mSuperClass;
    // 接口集合
    private Set<String> mInterfaces;
    // 字段列表
    private List<String> mFields;
    // 构造函数列表
    private List<String> mConstructors;
    // 方法列表
    private List<String> mMethods;
    // 类加载器
    private ClassLoader mClassLoader;
    // 复制的方法映射表
    private Map<String, Method> mCopyMethods; // <method desc,method instance>
    // 复制的构造函数映射表
    private Map<String, Constructor<?>> mCopyConstructors; // <constructor desc,constructor instance>
    // 是否添加默认构造函数
    private boolean mDefaultConstructor = false;

    // 私有构造函数，防止外部直接实例化
    private ClassGenerator() {
    }

    // 使用给定的类加载器和ClassPool初始化ClassGenerator
    private ClassGenerator(ClassLoader classLoader, ClassPool pool) {
        mClassLoader = classLoader;
        mPool = pool;
    }

    // 创建一个新的ClassGenerator实例，默认使用当前线程上下文类加载器
    public static ClassGenerator newInstance() {
        return new ClassGenerator(Thread.currentThread().getContextClassLoader(), getClassPool(Thread.currentThread().getContextClassLoader()));
    }

    // 创建一个新的ClassGenerator实例，允许指定类加载器
    public static ClassGenerator newInstance(ClassLoader loader) {
        return new ClassGenerator(loader, getClassPool(loader));
    }

    // 判断给定类是否为动态生成的类
    public static boolean isDynamicClass(Class<?> cl) {
        return ClassGenerator.DC.class.isAssignableFrom(cl);
    }

    // 获取或创建与给定类加载器关联的ClassPool
    public static ClassPool getClassPool(ClassLoader loader) {
        if (loader == null) {
            return ClassPool.getDefault();
        }

        ClassPool pool = POOL_MAP.get(loader);
        if (pool == null) {
            pool = new ClassPool(true);
            pool.insertClassPath(new CustomizedLoaderClassPath(loader));
            POOL_MAP.put(loader, pool);
        }
        return pool;
    }

    // 根据访问修饰符返回相应的字符串表示形式
    private static String modifier(int mod) {
        StringBuilder modifier = new StringBuilder();
        if (Modifier.isPublic(mod)) {
            modifier.append("public");
        } else if (Modifier.isProtected(mod)) {
            modifier.append("protected");
        } else if (Modifier.isPrivate(mod)) {
            modifier.append("private");
        }

        if (Modifier.isStatic(mod)) {
            modifier.append(" static");
        }
        if (Modifier.isVolatile(mod)) {
            modifier.append(" volatile");
        }

        return modifier.toString();
    }

    // 获取当前设置的类名
    public String getClassName() {
        return mClassName;
    }

    // 设置要生成的类名，并返回当前ClassGenerator实例
    public ClassGenerator setClassName(String name) {
        mClassName = name;
        return this;
    }

    // 向类中添加接口，并返回当前ClassGenerator实例
    public ClassGenerator addInterface(String cn) {
        if (mInterfaces == null) {
            mInterfaces = new HashSet<String>();
        }
        mInterfaces.add(cn);
        return this;
    }

    // 通过类对象向类中添加接口，并返回当前ClassGenerator实例
    public ClassGenerator addInterface(Class<?> cl) {
        return addInterface(cl.getName());
    }

    // 设置超类，并返回当前ClassGenerator实例
    public ClassGenerator setSuperClass(String cn) {
        mSuperClass = cn;
        return this;
    }

    // 通过类对象设置超类，并返回当前ClassGenerator实例
    public ClassGenerator setSuperClass(Class<?> cl) {
        mSuperClass = cl.getName();
        return this;
    }

    // 向类中添加字段定义，并返回当前ClassGenerator实例
    public ClassGenerator addField(String code) {
        if (mFields == null) {
            mFields = new ArrayList<String>();
        }
        mFields.add(code);
        return this;
    }

    // 添加一个字段到类中，支持指定名称、修饰符和类型，并返回当前ClassGenerator实例
    public ClassGenerator addField(String name, int mod, Class<?> type) {
        return addField(name, mod, type, null);
    }

    // 添加一个字段到类中，支持指定名称、修饰符、类型及默认值，并返回当前ClassGenerator实例
    public ClassGenerator addField(String name, int mod, Class<?> type, String def) {
        StringBuilder sb = new StringBuilder();
        sb.append(modifier(mod)).append(' ').append(ReflectUtils.getName(type)).append(' ');
        sb.append(name);
        if (StringUtils.isNotEmpty(def)) {
            sb.append('=');
            sb.append(def);
        }
        sb.append(';');
        return addField(sb.toString());
    }

    // 向类中添加方法代码，并返回当前ClassGenerator实例
    public ClassGenerator addMethod(String code) {
        if (mMethods == null) {
            mMethods = new ArrayList<String>();
        }
        mMethods.add(code);
        return this;
    }

    // 添加一个方法到类中，支持指定名称、修饰符、返回类型、参数类型数组及方法体，并返回当前ClassGenerator实例
    public ClassGenerator addMethod(String name, int mod, Class<?> rt, Class<?>[] pts, String body) {
        return addMethod(name, mod, rt, pts, null, body);
    }

    // 添加一个方法到类中，支持指定名称、修饰符、返回类型、参数类型数组、异常类型数组及方法体，并返回当前ClassGenerator实例
    public ClassGenerator addMethod(String name, int mod, Class<?> rt, Class<?>[] pts, Class<?>[] ets,
                                    String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(modifier(mod)).append(' ').append(ReflectUtils.getName(rt)).append(' ').append(name);
        sb.append('(');
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ReflectUtils.getName(pts[i]));
            sb.append(" arg").append(i);
        }
        sb.append(')');
        if (ArrayUtils.isNotEmpty(ets)) {
            sb.append(" throws ");
            for (int i = 0; i < ets.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(ReflectUtils.getName(ets[i]));
            }
        }
        sb.append('{').append(body).append('}');
        return addMethod(sb.toString());
    }

    // 通过方法对象添加方法到类中，并返回当前ClassGenerator实例
    public ClassGenerator addMethod(Method m) {
        addMethod(m.getName(), m);
        return this;
    }

    // 通过方法名和方法对象添加方法到类中，并记录该方法以备后续复制，并返回当前ClassGenerator实例
    public ClassGenerator addMethod(String name, Method m) {
        String desc = name + ReflectUtils.getDescWithoutMethodName(m);
        addMethod(':' + desc);
        if (mCopyMethods == null) {
            mCopyMethods = new ConcurrentHashMap<String, Method>(8);
        }
        mCopyMethods.put(desc, m);
        return this;
    }

    // 向类中添加构造函数代码，并返回当前ClassGenerator实例
    public ClassGenerator addConstructor(String code) {
        if (mConstructors == null) {
            mConstructors = new LinkedList<String>();
        }
        mConstructors.add(code);
        return this;
    }

    // 添加一个构造函数到类中，支持指定修饰符、参数类型数组及构造函数体，并返回当前ClassGenerator实例
    public ClassGenerator addConstructor(int mod, Class<?>[] pts, String body) {
        return addConstructor(mod, pts, null, body);
    }

    // 添加一个构造函数到类中，支持指定修饰符、参数类型数组、异常类型数组及构造函数体，并返回当前ClassGenerator实例
    public ClassGenerator addConstructor(int mod, Class<?>[] pts, Class<?>[] ets, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(modifier(mod)).append(' ').append(SIMPLE_NAME_TAG);
        sb.append('(');
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ReflectUtils.getName(pts[i]));
            sb.append(" arg").append(i);
        }
        sb.append(')');
        if (ArrayUtils.isNotEmpty(ets)) {
            sb.append(" throws ");
            for (int i = 0; i < ets.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(ReflectUtils.getName(ets[i]));
            }
        }
        sb.append('{').append(body).append('}');
        return addConstructor(sb.toString());
    }

    // 通过构造函数对象添加构造函数到类中，并记录该构造函数以备后续复制，并返回当前ClassGenerator实例
    public ClassGenerator addConstructor(Constructor<?> c) {
        String desc = ReflectUtils.getDesc(c);
        addConstructor(":" + desc);
        if (mCopyConstructors == null) {
            mCopyConstructors = new ConcurrentHashMap<String, Constructor<?>>(4);
        }
        mCopyConstructors.put(desc, c);
        return this;
    }

    // 添加默认构造函数到类中，并返回当前ClassGenerator实例
    public ClassGenerator addDefaultConstructor() {
        mDefaultConstructor = true;
        return this;
    }

    // 获取当前使用的ClassPool
    public ClassPool getClassPool() {
        return mPool;
    }

    // 将构建的类转换为实际的Java类对象
    public Class<?> toClass() {
        return toClass(mClassLoader,
            getClass().getProtectionDomain());
    }

    // 将构建的类转换为实际的Java类对象，允许指定类加载器和保护域
    public Class<?> toClass(ClassLoader loader, ProtectionDomain pd) {
        if (mCtc != null) {
            mCtc.detach();
        }
        long id = CLASS_NAME_COUNTER.getAndIncrement();
        try {
            CtClass ctcs = mSuperClass == null ? null : mPool.get(mSuperClass);
            if (mClassName == null) {
                mClassName = (mSuperClass == null || javassist.Modifier.isPublic(ctcs.getModifiers())
                    ? ClassGenerator.class.getName() : mSuperClass + "$sc") + id;
            }
            mCtc = mPool.makeClass(mClassName);
            if (mSuperClass != null) {
                mCtc.setSuperclass(ctcs);
            }
            mCtc.addInterface(mPool.get(DC.class.getName())); // 添加动态类标记接口
            if (mInterfaces != null) {
                for (String cl : mInterfaces) {
                    mCtc.addInterface(mPool.get(cl));
                }
            }
            if (mFields != null) {
                for (String code : mFields) {
                    mCtc.addField(CtField.make(code, mCtc));
                }
            }
            if (mMethods != null) {
                for (String code : mMethods) {
                    if (code.charAt(0) == ':') {
                        mCtc.addMethod(CtNewMethod.copy(getCtMethod(mCopyMethods.get(code.substring(1))),
                            code.substring(1, code.indexOf('(')), mCtc, null));
                    } else {
                        mCtc.addMethod(CtNewMethod.make(code, mCtc));
                    }
                }
            }
            if (mDefaultConstructor) {
                mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
            }
            if (mConstructors != null) {
                for (String code : mConstructors) {
                    if (code.charAt(0) == ':') {
                        mCtc.addConstructor(CtNewConstructor
                            .copy(getCtConstructor(mCopyConstructors.get(code.substring(1))), mCtc, null));
                    } else {
                        String[] sn = mCtc.getSimpleName().split("\\$+"); // 内部类名包含$符号
                        mCtc.addConstructor(
                            CtNewConstructor.make(code.replaceFirst(SIMPLE_NAME_TAG, sn[sn.length - 1]), mCtc));
                    }
                }
            }
            return mCtc.toClass(loader, pd);
        } catch (RuntimeException e) {
            throw e;
        } catch (NotFoundException | CannotCompileException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // 释放资源，清理持有的数据结构
    public void release() {
        if (mCtc != null) {
            mCtc.detach();
        }
        if (mInterfaces != null) {
            mInterfaces.clear();
        }
        if (mFields != null) {
            mFields.clear();
        }
        if (mMethods != null) {
            mMethods.clear();
        }
        if (mConstructors != null) {
            mConstructors.clear();
        }
        if (mCopyMethods != null) {
            mCopyMethods.clear();
        }
        if (mCopyConstructors != null) {
            mCopyConstructors.clear();
        }
    }

    // 从给定的类获取对应的CtClass对象
    private CtClass getCtClass(Class<?> c) throws NotFoundException {
        return mPool.get(c.getName());
    }

    // 从给定的方法获取对应的CtMethod对象
    private CtMethod getCtMethod(Method m) throws NotFoundException {
        return getCtClass(m.getDeclaringClass())
            .getMethod(m.getName(), ReflectUtils.getDescWithoutMethodName(m));
    }

    // 从给定的构造函数获取对应的CtConstructor对象
    private CtConstructor getCtConstructor(Constructor<?> c) throws NotFoundException {
        return getCtClass(c.getDeclaringClass()).getConstructor(ReflectUtils.getDesc(c));
    }

    // 动态类标记接口
    public static interface DC {

    } // dynamic class tag interface.
}
