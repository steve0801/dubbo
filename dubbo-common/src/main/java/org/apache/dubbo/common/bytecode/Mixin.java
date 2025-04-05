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

import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.ReflectUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mixin
 */
public abstract class Mixin {
    // 定义一个静态常量，保存当前类所在的包名
    private static final String PACKAGE_NAME = Mixin.class.getPackage().getName();
    // 用于生成唯一ID的原子计数器
    private static AtomicLong MIXIN_CLASS_COUNTER = new AtomicLong(0);

    // Mixin的保护构造函数
    protected Mixin() {
    }

    /**
     * 混合接口和委托。
     * 所有类必须是公共的。
     *
     * @param ics 接口类数组。
     * @param dc  委托类。
     * @return Mixin实例。
     */
    // 实现mixin方法，接受接口类数组和单个委托类作为参数
    public static Mixin mixin(Class<?>[] ics, Class<?> dc) {
        return mixin(ics, new Class[]{dc});
    }

    /**
     * 混合接口和委托。
     * 所有类必须是公共的。
     *
     * @param ics 接口类数组。
     * @param dc  委托类。
     * @param cl  类加载器。
     * @return Mixin实例。
     */
    // 实现mixin方法，接受接口类数组、单个委托类以及类加载器作为参数
    public static Mixin mixin(Class<?>[] ics, Class<?> dc, ClassLoader cl) {
        return mixin(ics, new Class[]{dc}, cl);
    }

    /**
     * 混合接口和委托。
     * 所有类必须是公共的。
     *
     * @param ics 接口类数组。
     * @param dcs 委托类数组。
     * @return Mixin实例。
     */
    // 实现mixin方法，接受接口类数组和委托类数组作为参数
    public static Mixin mixin(Class<?>[] ics, Class<?>[] dcs) {
        return mixin(ics, dcs, ClassUtils.getCallerClassLoader(Mixin.class));
    }

    /**
     * 混合接口和委托。
     * 所有类必须是公共的。
     *
     * @param ics 接口类数组。
     * @param dcs 委托类数组。
     * @param cl  类加载器。
     * @return Mixin实例。
     */
    // 实现mixin方法，接受接口类数组、委托类数组以及类加载器作为参数
    public static Mixin mixin(Class<?>[] ics, Class<?>[] dcs, ClassLoader cl) {
        // 断言所有提供的类都是接口
        assertInterfaceArray(ics);

        // 获取并增加唯一ID
        long id = MIXIN_CLASS_COUNTER.getAndIncrement();
        // 初始化包名变量
        String pkg = null;
        // 创建ClassGenerator实例
        ClassGenerator ccp = null, ccm = null;
        try {
            // 创建新的ClassGenerator实例
            ccp = ClassGenerator.newInstance(cl);

            // 构建实现构造函数所需的代码
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < dcs.length; i++) {
                // 确保所有非公共委托类来自同一个包
                if (!Modifier.isPublic(dcs[i].getModifiers())) {
                    String npkg = dcs[i].getPackage().getName();
                    if (pkg == null) {
                        pkg = npkg;
                    } else {
                        if (!pkg.equals(npkg)) {
                            throw new IllegalArgumentException("non-public interfaces class from different packages");
                        }
                    }
                }

                // 添加字段
                ccp.addField("private " + dcs[i].getName() + " d" + i + ";");

                // 构建初始化委托对象的代码
                code.append('d').append(i).append(" = (").append(dcs[i].getName()).append(")$1[").append(i).append("];\n");
                // 如果委托类实现了MixinAware接口，则设置mixin实例
                if (MixinAware.class.isAssignableFrom(dcs[i])) {
                    code.append("d").append(i).append(".setMixinInstance(this);\n");
                }
            }
            // 添加构造函数
            ccp.addConstructor(Modifier.PUBLIC, new Class<?>[]{Object[].class}, code.toString());

            // 记录已经处理过的方法签名
            Set<String> worked = new HashSet<String>();
            for (int i = 0; i < ics.length; i++) {
                // 确保所有非公共接口类来自同一个包
                if (!Modifier.isPublic(ics[i].getModifiers())) {
                    String npkg = ics[i].getPackage().getName();
                    if (pkg == null) {
                        pkg = npkg;
                    } else {
                        if (!pkg.equals(npkg)) {
                            throw new IllegalArgumentException("non-public delegate class from different packages");
                        }
                    }
                }

                // 添加接口
                ccp.addInterface(ics[i]);

                // 遍历接口中的方法
                for (Method method : ics[i].getMethods()) {
                    // 跳过Object类的方法
                    if ("java.lang.Object".equals(method.getDeclaringClass().getName())) {
                        continue;
                    }

                    // 获取方法描述符
                    String desc = ReflectUtils.getDesc(method);
                    // 如果该方法已经被处理过，则跳过
                    if (worked.contains(desc)) {
                        continue;
                    }
                    worked.add(desc);

                    // 查找对应的方法
                    int ix = findMethod(dcs, desc);
                    if (ix < 0) {
                        throw new RuntimeException("Missing method [" + desc + "] implement.");
                    }

                    // 获取返回类型
                    Class<?> rt = method.getReturnType();
                    String mn = method.getName();
                    // 根据返回类型添加方法
                    if (Void.TYPE.equals(rt)) {
                        ccp.addMethod(mn, method.getModifiers(), rt, method.getParameterTypes(), method.getExceptionTypes(),
                            "d" + ix + "." + mn + "($$);");
                    } else {
                        ccp.addMethod(mn, method.getModifiers(), rt, method.getParameterTypes(), method.getExceptionTypes(),
                            "return ($r)d" + ix + "." + mn + "($$);");
                    }
                }
            }

            // 如果包名为null，则使用默认包名
            if (pkg == null) {
                pkg = PACKAGE_NAME;
            }

            // 创建MixinInstance类
            String micn = pkg + ".mixin" + id;
            ccp.setClassName(micn);
            ccp.toClass();

            // 创建Mixin类
            String fcn = Mixin.class.getName() + id;
            ccm = ClassGenerator.newInstance(cl);
            ccm.setClassName(fcn);
            ccm.addDefaultConstructor();
            ccm.setSuperClass(Mixin.class.getName());
            ccm.addMethod("public Object newInstance(Object[] delegates){ return new " + micn + "($1); }");
            Class<?> mixin = ccm.toClass();
            // 返回新创建的Mixin实例
            return (Mixin) mixin.getDeclaredConstructor().newInstance();
        } catch (RuntimeException e) {
            // 抛出运行时异常
            throw e;
        } catch (Exception e) {
            // 抛出带消息的运行时异常
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            // 释放ClassGenerator资源
            if (ccp != null) {
                ccp.release();
            }
            if (ccm != null) {
                ccm.release();
            }
        }
    }

    // 在委托类数组中查找指定方法描述符的方法
    private static int findMethod(Class<?>[] dcs, String desc) {
        Class<?> cl;
        Method[] methods;
        for (int i = 0; i < dcs.length; i++) {
            cl = dcs[i];
            methods = cl.getMethods();
            for (Method method : methods) {
                if (desc.equals(ReflectUtils.getDesc(method))) {
                    return i;
                }
            }
        }
        return -1;
    }

    // 断言提供的类数组中的所有类都是接口
    private static void assertInterfaceArray(Class<?>[] ics) {
        for (int i = 0; i < ics.length; i++) {
            if (!ics[i].isInterface()) {
                throw new RuntimeException("Class " + ics[i].getName() + " is not a interface.");
            }
        }
    }

    /**
     * 创建新的Mixin实例。
     *
     * @param ds 委托实例数组。
     * @return 实例。
     */
    // 抽象方法，用于创建新的Mixin实例
    abstract public Object newInstance(Object[] ds);

    // 定义MixinAware接口
    public static interface MixinAware {
        // 设置mixin实例
        void setMixinInstance(Object instance);
    }
}
