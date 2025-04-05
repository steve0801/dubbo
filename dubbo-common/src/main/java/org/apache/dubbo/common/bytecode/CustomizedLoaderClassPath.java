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

import javassist.ClassPath;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;

/**
 * A class search-path representing a class loader.
 *
 * <p>It is used for obtaining a class file from the given
 * class loader by <code>getResourceAsStream()</code>.
 * The <code>LoaderClassPath</code> refers to the class loader through
 * <code>WeakReference</code>.  If the class loader is garbage collected,
 * the other search pathes are examined.
 *
 * <p>The given class loader must have both <code>getResourceAsStream()</code>
 * and <code>getResource()</code>.
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @author Shigeru Chiba
 */
public class CustomizedLoaderClassPath implements ClassPath {
    // 用于存储ClassLoader的弱引用，防止内存泄漏
    private WeakReference clref;

    /**
     * 创建一个表示类加载器的搜索路径。
     */
    // 构造函数，初始化时传入一个ClassLoader，并将其封装为WeakReference
    public CustomizedLoaderClassPath(ClassLoader cl) {
        clref = new WeakReference(cl);
    }

    // 重写toString方法，返回当前对象的字符串表示形式
    public String toString() {
        // 声明一个Object类型的变量cl，初始值设为null
        Object cl = null;
        // 如果clref不为空，则尝试获取其引用的对象
        if (clref != null) {
            cl = clref.get();
        }
        // 根据cl是否为null返回不同的字符串
        return cl == null ? "<null>" : cl.toString();
    }

    /**
     * 从类加载器中获取类文件。
     * 此方法会调用类加载器上的getResourceAsStream(String)方法。
     */
    // 打开指定类名对应的类文件并返回输入流
    public InputStream openClassfile(String classname) {
        // 将类名转换成资源路径格式
        String cname = classname.replace('.', '/') + ".class";
        // 获取当前引用的类加载器
        ClassLoader cl = (ClassLoader) clref.get();
        // 如果类加载器为空，则直接返回null
        if (cl == null) {
            return null;        // not found
        } else {
            // 定义InputStream类型的变量result
            InputStream result;
            // 特殊处理以"org.apache.dubbo"开头的类
            if (classname.startsWith("org.apache.dubbo") && cl != this.getClass().getClassLoader()) {
                // 首先尝试使用本类的类加载器加载资源
                result = this.getClass().getClassLoader().getResourceAsStream(cname);
                // 如果找到了资源，则直接返回结果
                if (result != null) {
                    return result;
                } else {
                    // 否则再尝试使用原始的类加载器加载
                    return cl.getResourceAsStream(cname);
                }
            } else {
                // 对于非特殊处理的情况，直接使用原始类加载器加载
                result = cl.getResourceAsStream(cname);
                // 如果找不到且当前类加载器不是本类的类加载器，则再次尝试使用本类的类加载器
                if (result == null && (cl != this.getClass().getClassLoader())) {
                    return this.getClass().getClassLoader().getResourceAsStream(cname);
                }
                // 返回最终的结果
                return result;
            }
        }
    }

    /**
     * 获取指定类文件的URL。
     * 此方法会调用类加载器上的getResource(String)方法。
     *
     * @return 如果找不到类文件，则返回null。
     */
    // 查找并返回指定类名对应的类文件URL
    public URL find(String classname) {
        // 将类名转换成资源路径格式
        String cname = classname.replace('.', '/') + ".class";
        // 获取当前引用的类加载器
        ClassLoader cl = (ClassLoader) clref.get();
        // 如果类加载器为空，则直接返回null
        if (cl == null) {
            return null;        // not found
        } else {
            // 定义URL类型的变量url
            URL url;
            // 特殊处理以"org.apache.dubbo"开头的类
            if (classname.startsWith("org.apache.dubbo") && cl != this.getClass().getClassLoader()) {
                // 首先尝试使用本类的类加载器查找资源
                url = this.getClass().getClassLoader().getResource(cname);
                // 如果找到了资源，则直接返回结果
                if (url != null) {
                    return url;
                } else {
                    // 否则再尝试使用原始的类加载器查找
                    return cl.getResource(cname);
                }
            } else {
                // 对于非特殊处理的情况，直接使用原始类加载器查找
                url = cl.getResource(cname);
                // 如果找不到且当前类加载器不是本类的类加载器，则再次尝试使用本类的类加载器
                if (url == null && (cl != this.getClass().getClassLoader())) {
                    return this.getClass().getClassLoader().getResource(cname);
                }
                // 返回最终的结果
                return url;
            }
        }
    }

    /**
     * 关闭此类路径。
     */
    // 清除对类加载器的引用
    public void close() {
        clref = null;
    }
}
